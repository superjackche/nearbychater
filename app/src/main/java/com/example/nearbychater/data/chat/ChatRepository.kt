package com.example.nearbychater.data.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.nearbychater.MainActivity
import com.example.nearbychater.R
import com.example.nearbychater.core.logging.LogManager
import com.example.nearbychater.core.model.Attachment
import com.example.nearbychater.core.model.AttachmentType
import com.example.nearbychater.core.model.ChatMessage
import com.example.nearbychater.core.model.ConversationId
import com.example.nearbychater.core.model.ConversationSnapshot
import com.example.nearbychater.core.model.ConversationSummary
import com.example.nearbychater.core.model.DiagnosticsEvent
import com.example.nearbychater.core.model.MemberId
import com.example.nearbychater.core.model.MemberProfile
import com.example.nearbychater.core.model.MeshEnvelope
import com.example.nearbychater.core.model.MessageStatus
import com.example.nearbychater.core.model.MessageType
import com.example.nearbychater.data.nearby.EndpointInfo
import com.example.nearbychater.data.nearby.NearbyChatService
import com.example.nearbychater.data.nearby.NearbyEvent
import com.example.nearbychater.data.storage.ChatDao
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 自己和自己的会话ID常量
// 用于标识"我"这个特殊会话（类似微信的文件传输助手）
private const val SELF_SUMMARY_ID = "__self__"

/** Coordinates cached conversations, Nearby mesh events, and offline queue flushing. */
class ChatRepository(
        private val context: Context,
        private val nearbyChatService: NearbyChatService,
        private val logManager: LogManager,
        private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        private val chatDao: ChatDao = ChatDao(context),
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // 本机设备ID，根据ANDROID_ID生成，唯一标识这台设备
    private val localMemberId: MemberId = deviceId(context)

    // _conversations: 所有会话的内存缓存
    // Map<会话ID, 会话快照>，会话快照包含消息列表和成员列表
    // MutableStateFlow是热流，有新数据时自动通知订阅者（UI层）
    private val _conversations =
            MutableStateFlow<Map<ConversationId, ConversationSnapshot>>(emptyMap())

    // _members: 所有成员的内存缓存
    // 存储每个成员的昵称、在线状态、最后上线时间等信息
    private val _members = MutableStateFlow<Map<MemberId, MemberProfile>>(emptyMap())

    // _diagnostics: 诊断事件流
    // SharedFlow类似广播，可以有多个订阅者
    // extraBufferCapacity=32表示最多缓存32个未消费的事件
    private val _diagnostics = MutableSharedFlow<DiagnosticsEvent>(extraBufferCapacity = 32)

    // flushTrigger: 消息发送触发器
    // Channel是协程间通信的管道（类似Python的Queue）
    // CONFLATED表示只保留最新的一个值，旧值会被覆盖
    // 当有新消息或网络恢复时，发送信号触发消息队列刷新
    private val flushTrigger = Channel<Unit>(Channel.CONFLATED)

    // flushJob: 消息刷新循环的Job
    // Job用于管理协程的生命周期，可以取消
    private val flushJob: Job

    // pendingSends: 待发送消息队列
    // ConcurrentHashMap是线程安全的Map（类似Java的ConcurrentHashMap）
    // 存储所有正在发送或等待重试的消息
    private val pendingSends = ConcurrentHashMap<String, ChatMessage>()

    // seenPacketIds: 已处理的数据包ID集合
    // 用于防止重复处理同一个消息（P2P网络中消息可能被多次转发）
    // LinkedHashMap with LRU (Least Recently Used) 策略
    // 当size>1000时自动删除最旧的记录，防止内存泄漏
    private val seenPacketIds =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(
                            object : LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
                                override fun removeEldestEntry(
                                        eldest: MutableMap.MutableEntry<String, Boolean>?
                                ): Boolean {
                                    return size > 1000
                                }
                            }
                    )
            )

    // 公开的诊断事件流，UI层可以订阅
    // asSharedFlow()转换成只读的SharedFlow
    val diagnostics = _diagnostics.asSharedFlow()

    // 公开的会话数据，UI层订阅这个Flow就能实时获取会话更新
    val conversations: StateFlow<Map<ConversationId, ConversationSnapshot>> = _conversations

    // 公开的成员列表
    // map{}把Map转换成List，并按memberId排序
    // stateIn把Flow转换成StateFlow（热流），参数：
    // - externalScope: 协程作用域
    // - SharingStarted.Eagerly: 立即开始收集
    // - emptyList(): 初始值
    val members: StateFlow<List<MemberProfile>> =
            _members
                    .map { it.values.sortedBy { profile -> profile.memberId } }
                    .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    // conversationSummaries: 会话摘要列表（用于会话列表UI）
    // combine()合并多个Flow，当任一Flow更新时都会重新计算
    // buildSummaries()根据会话和成员信息生成摘要
    // 摘要包含：标题、预览、未读数、头像种子等
    val conversationSummaries: StateFlow<List<ConversationSummary>> =
            combine(_conversations, _members) { convMap, memberMap ->
                        buildSummaries(convMap, memberMap)
                    }
                    .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    // selfMemberId: 本机设备ID的公开访问器
    // get()表示这是计算属性，每次访问都返回localMemberId
    val selfMemberId: MemberId
        get() = localMemberId

    // init块在对象创建时执行，初始化Repository
    init {
        // 启动协程加载数据库中的状态
        externalScope.launch {
            reloadState() // 从数据库加载会话和成员
            flushTrigger.trySend(Unit) // 触发消息队列刷新
        }
        // 开始监听Nearby服务的事件（成员上线/下线、消息接收等）
        observeNearbyEvents()
        // 启动Nearby服务，开始广播自己并发现附近设备
        // EndpointInfo包含自己的设备ID和昵称（设备型号）
        nearbyChatService.start(
                EndpointInfo(memberId = localMemberId, nickname = BuildNickname.local())
        )
        // 启动消息刷新循环
        // 这个协程会一直运行，等待flushTrigger的信号
        flushJob = externalScope.launch { runFlushLoop() }
    }

    // 获取指定会话的消息列表
    // 返回StateFlow，UI层订阅后会自动更新
    // filter过滤掉ACK消息（确认消息不需要显示在UI上）
    fun conversationMessages(conversationId: ConversationId): StateFlow<List<ChatMessage>> =
            _conversations
                    .map { conversations ->
                        conversations[conversationId]?.messages?.filter {
                            it.type != MessageType.ACK
                        }
                                ?: emptyList()
                    }
                    .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    // 发送消息的核心函数
    // suspend表示这是挂起函数，可能会暂停协程但不阻塞线程
    suspend fun sendMessage(
            conversationId: ConversationId,
            content: String,
            attachment: Attachment? = null // 可选的附件（图片等）
    ) {
        // 第1步：创建消息对象
        // 初始状态是QUEUED（排队中）
        val message = 
                ChatMessage(
                        conversationId = conversationId,
                        senderId = localMemberId,
                        content = content,
                        status = MessageStatus.QUEUED,
                        attachment = attachment
                )
        // 第2步：加入待发送队列
        // 即使现在网络不通，消息也会保存，等网络恢复后自动发送
        pendingSends[message.id] = message

        // 第3步：准备会话成员信息
        val memberIds = knownMemberIds(conversationId)

        // 第4步：持久化到数据库
        // onDb{}在IO线程执行数据库操作
        onDb {
            chatDao.ensureConversation(conversationId, resolveConversationKey(memberIds), memberIds)
            chatDao.insertOrUpdateMessage(message)
        }

        // 第5步：更新内存缓存，UI立即显示（状态改为SENDING）
        upsertMessageLocally(message.copy(status = MessageStatus.SENDING))

        // 第6步：立即尝试发送消息，而不是等待flushLoop
        // 优化消息发送延迟，实现快速发送
        attemptSend(message)
    }

    public suspend fun updateLocalNickname(memberId: MemberId, nickname: String) {
        val current = _members.value[memberId]
        val updated = (current ?: MemberProfile(memberId = memberId)).copy(localNickname = nickname)
        onDb { chatDao.upsertMember(updated) }
        upsertMemberLocally(updated)
    }

    private fun conversationIdFromMembers(memberIds: Set<MemberId>): ConversationId {
        val remoteMembers = memberIds.filterNot { it == localMemberId }.sorted()
        return if (remoteMembers.isEmpty()) SELF_SUMMARY_ID else remoteMembers.joinToString(":")
    }

    fun conversationIdFor(remoteMemberId: MemberId): ConversationId =
            conversationIdFromMembers(setOf(remoteMemberId))

    fun conversationIdForMembers(remoteMemberIds: Set<MemberId>): ConversationId =
            conversationIdFromMembers(remoteMemberIds)

    fun ensureConversationMembers(remoteMemberIds: Set<MemberId>): ConversationId {
        val filtered = remoteMemberIds.filterNot { it == localMemberId }.toSet()
        val conversationId = conversationIdFromMembers(filtered)
        val memberSet = filtered + localMemberId
        externalScope.launch {
            onDb {
                chatDao.ensureConversation(
                        conversationId,
                        resolveConversationKey(memberSet),
                        memberSet
                )
            }
            ensureConversationLocally(conversationId, memberSet)
        }
        return conversationId
    }

    suspend fun refresh() {
        nearbyChatService.refreshDiscovery()
        reloadState()
        flushTrigger.trySend(Unit)
    }

    fun isMemberConnected(memberId: MemberId): Boolean =
            nearbyChatService.isMemberConnected(memberId)

    public suspend fun cancelMessage(conversationId: ConversationId, messageId: String) {
        pendingSends.remove(messageId)
        onDb {
            chatDao.updateMessageStatus(
                    conversationId,
                    messageId,
                    MessageStatus.CANCELLED,
                    shouldRelay = false
            )
        }
        updateMessageLocally(conversationId, messageId) { current ->
            current.copy(status = MessageStatus.CANCELLED, shouldRelay = false)
        }
    }

    public suspend fun retryMessage(conversationId: ConversationId, messageId: String) {
        val snapshot = _conversations.value[conversationId] ?: return
        val message = snapshot.messages.find { it.id == messageId } ?: return
        val queuedMessage = message.copy(status = MessageStatus.QUEUED)
        pendingSends[messageId] = queuedMessage
        onDb { chatDao.updateMessageStatus(conversationId, messageId, MessageStatus.QUEUED) }
        updateMessageLocally(conversationId, messageId) { it.copy(status = MessageStatus.QUEUED) }
        flushTrigger.trySend(Unit)
    }

    public suspend fun deleteConversation(conversationId: ConversationId) {
        onDb { chatDao.deleteConversation(conversationId) }
        _conversations.update { it - conversationId }
    }

    private suspend fun attemptSend(message: ChatMessage) {
        when (val target = resolveConversationTarget(message.conversationId)) {
            ConversationTarget.Unknown -> return
            ConversationTarget.Self -> {
                onDb {
                    chatDao.updateMessageStatus(
                            message.conversationId,
                            message.id,
                            MessageStatus.SENT,
                            shouldRelay = false
                    )
                }
                updateMessageLocally(message.conversationId, message.id) { current ->
                    current.copy(status = MessageStatus.SENT, shouldRelay = false)
                }
                pendingSends.remove(message.id) // 确保移除消息
            }
            is ConversationTarget.Remote -> {
                val envelope =
                        MeshEnvelope(
                                conversationId = message.conversationId,
                                message = message.copy(status = MessageStatus.SENT),
                                originId = localMemberId,
                                hopCount = 0
                        )
                // 实际调用 nearbyChatService 发送消息
                val success =
                        nearbyChatService.broadcast(
                                conversationId = message.conversationId,
                                message = envelope,
                                targetMembers = target.memberIds
                        )
                val nextStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                onDb { chatDao.updateMessageStatus(message.conversationId, message.id, nextStatus) }
                updateMessageLocally(message.conversationId, message.id) { current ->
                    current.copy(status = nextStatus)
                }
                pendingSends.remove(message.id) // 无论成功与否都移除
            }
        }
    }

    private suspend fun reloadState() {
        val (members, conversations) = onDb { chatDao.readMembers() to chatDao.readConversations() }
        _members.value = members
        _conversations.value = conversations
    }

    // observeNearbyEvents: 监听Nearby服务的事件
    // 相当于设置事件监听器，当有新事件时自动响应
    private fun observeNearbyEvents() {
        externalScope.launch {
            // nearbyChatService.events()返回一个Flow
            // collect{}持续监听这个Flow，类似while(true){等待事件}
            nearbyChatService.events().collect { event ->
                // when是Kotlin的switch，is类似 instanceof
                when (event) {
                    // 成员上线: 更新成员状态，创建会话，尝试发送离线消息
                    is NearbyEvent.MemberOnline ->
                            handleMemberOnline(event.memberId, event.nickname)
                    // 成员下线: 标记为离线状态
                    is NearbyEvent.MemberOffline -> handleMemberOffline(event.memberId)
                    // 收到消息: 处理来自其他设备的消息
                    is NearbyEvent.MessageReceived -> handleRemoteMessage(event.envelope)
                    // 错误事件: 记录诊断信息
                    is NearbyEvent.Error -> trackDiagnostics(event.diagnosticsEvent)
                }
            }
        }
    }

    private suspend fun handleMemberOnline(memberId: MemberId, nickname: String?) {
        val now = System.currentTimeMillis()
        val profile = _members.value[memberId]
        val updated =
                (profile ?: MemberProfile(memberId = memberId)).copy(
                        remoteNickname = nickname ?: profile?.remoteNickname,
                        isOnline = true,
                        lastSeenAt = now
                )
        val conversationId = conversationIdFor(memberId)
        val memberIds = setOf(localMemberId, memberId)
        onDb {
            chatDao.updateMemberOnlineState(memberId, nickname, true, now)
            chatDao.ensureConversation(conversationId, resolveConversationKey(memberIds), memberIds)
        }
        upsertMemberLocally(updated)
        ensureConversationLocally(conversationId, memberIds)
        flushTrigger.trySend(Unit)
    }

    private suspend fun handleMemberOffline(memberId: MemberId) {
        val profile = _members.value[memberId] ?: return
        val updated = profile.copy(isOnline = false, lastSeenAt = System.currentTimeMillis())
        onDb { chatDao.updateMemberOnlineState(memberId, null, false, updated.lastSeenAt) }
        upsertMemberLocally(updated)
    }

    // handleRemoteMessage: 处理收到的远程消息
    // 这是P2P网络的关键函数，处理各种消息类型
    private suspend fun handleRemoteMessage(envelope: MeshEnvelope) {
        // 第1步：检查是否已处理过该消息
        // P2P网络中，同一消息可能由多个节点转发，需要去重
        if (seenPacketIds.contains(envelope.packetId)) {
            return // 已处理过，直接忽略
        }
        seenPacketIds.add(envelope.packetId) // 记录为已处理

        val message = envelope.message

        // 情凵1：如果是ACK消息（确认回复）
        if (message.type == MessageType.ACK) {
            // ACK消息的content字段存储的是原消息ID
            val targetMessageId = message.content
            // 把原消息状态改为DELIVERED（已送达）
            onDb {
                chatDao.updateMessageStatus(
                        message.conversationId,
                        targetMessageId,
                        MessageStatus.DELIVERED
                )
            }
            updateMessageLocally(message.conversationId, targetMessageId) { current ->
                current.copy(status = MessageStatus.DELIVERED)
            }
            return
        }

        // 情凵2：如果是自己发的消息（回环收到）
        // 这种情况在网状网络中很常见，消息会经过其他节点转发回来
        if (message.senderId == localMemberId) {
            // 标记为SENT，证明消息已成功在网络中传播
            onDb {
                chatDao.updateMessageStatus(message.conversationId, message.id, MessageStatus.SENT)
            }
            updateMessageLocally(message.conversationId, message.id) { current ->
                current.copy(status = MessageStatus.SENT)
            }
            return
        }

        // 情凵3：来自其他设备的正常消息
        val delivered = message.copy(status = MessageStatus.SENT)
        // 获取消息参与者列表，如果envelope中没有，就使用已知的成员列表
        val remoteParticipants =
                envelope.participants.takeIf { it.isNotEmpty() }
                        ?: (knownMemberIds(message.conversationId) - localMemberId)
        val memberIds = (remoteParticipants + message.senderId).toSet()
        val fullMemberSet = memberIds + localMemberId

        // 保存消息到数据库
        onDb {
            chatDao.ensureConversation(
                    message.conversationId,
                    resolveConversationKey(fullMemberSet),
                    fullMemberSet
            )
            chatDao.insertOrUpdateMessage(delivered)
        }
        ensureConversationLocally(message.conversationId, memberIds)
        upsertMessageLocally(delivered)

        // 发送ACK确认
        // 告诉发送者：我收到了
        val ack =
                ChatMessage(
                        conversationId = message.conversationId,
                        senderId = localMemberId,
                        content = message.id, // ACK的content是原消息ID
                        type = MessageType.ACK,
                        status = MessageStatus.SENT
                )
        attemptSend(ack)

        // 后台时显示通知
        // 如果应用不在前台，就发送系统通知
        if (!isAppInForeground()) {
            showNotification(message.conversationId, message)
        }
    }

    private suspend fun trackDiagnostics(event: DiagnosticsEvent) {
        logManager.log(event)
        _diagnostics.emit(event)
    }

    private suspend fun flushQueuedMessages() {
        _conversations.value.values.forEach { snapshot ->
            snapshot.messages
                    .filter {
                        it.status == MessageStatus.QUEUED || it.status == MessageStatus.FAILED
                    }
                    .forEach { pending ->
                        pendingSends[pending.id] = pending
                        attemptSend(pending)
                    }
        }
    }

    private suspend fun runFlushLoop() {
        for (trigger in flushTrigger) {
            flushQueuedMessages()
        }
    }

    private fun upsertMessageLocally(message: ChatMessage) {
        _conversations.update { conversations ->
            val snapshot =
                    conversations[message.conversationId]
                            ?: ConversationSnapshot(conversationId = message.conversationId)
            val updatedMessages = snapshot.messages.filterNot { it.id == message.id } + message
            val updatedMemberIds = snapshot.memberIds + message.senderId + localMemberId
            val updatedSnapshot =
                    snapshot.copy(
                            messages = updatedMessages.sortedBy { it.timestamp },
                            memberIds = updatedMemberIds,
                            conversationKey = resolveConversationKey(updatedMemberIds)
                    )
            conversations + (message.conversationId to updatedSnapshot)
        }
    }

    private fun updateMessageLocally(
            conversationId: ConversationId,
            messageId: String,
            transform: (ChatMessage) -> ChatMessage
    ) {
        _conversations.update { conversations ->
            val snapshot = conversations[conversationId] ?: return@update conversations
            val updatedMessages =
                    snapshot.messages.map { if (it.id == messageId) transform(it) else it }
            conversations + (conversationId to snapshot.copy(messages = updatedMessages))
        }
    }

    private fun ensureConversationLocally(
            conversationId: ConversationId,
            memberIds: Set<MemberId>
    ) {
        _conversations.update { conversations ->
            val existing = conversations[conversationId]
            val mergedMembers = (existing?.memberIds ?: emptySet()) + memberIds + localMemberId
            val snapshot =
                    (existing ?: ConversationSnapshot(conversationId = conversationId)).copy(
                            memberIds = mergedMembers,
                            conversationKey = resolveConversationKey(mergedMembers)
                    )
            conversations + (conversationId to snapshot)
        }
    }

    private fun upsertMemberLocally(profile: MemberProfile) {
        _members.update { it + (profile.memberId to profile) }
    }

    private fun knownMemberIds(conversationId: ConversationId): Set<MemberId> {
        return (_conversations.value[conversationId]?.memberIds ?: emptySet()) + localMemberId
    }

    private suspend fun <T> onDb(block: () -> T): T = withContext(ioDispatcher) { block() }

    private fun deviceId(context: Context): MemberId {
        val androidId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: UUID.randomUUID().toString()
    }

    private fun buildSummaries(
            conversations: Map<ConversationId, ConversationSnapshot>,
            members: Map<MemberId, MemberProfile>
    ): List<ConversationSummary> {
        val grouped = mutableMapOf<String, ConversationSummary>()
        conversations.values.forEach { snapshot ->
            val summary = snapshot.toSummary(members)
            val key = summary.conversationKey ?: summaryKey(summary)
            val existing = grouped[key]
            if (existing == null || summary.lastTimestamp >= existing.lastTimestamp) {
                grouped[key] = summary
            }
        }

        val summaries = grouped.values.toMutableList()
        if (summaries.none { it.isSelf }) {
            summaries.add(0, selfSummary())
        }
        val (selfItems, nonSelf) = summaries.partition { it.isSelf }
        val (pinned, unpinned) = nonSelf.partition { it.isPinned }
        return selfItems.sortedByDescending { it.lastTimestamp } +
                pinned.sortedByDescending { it.lastTimestamp } +
                unpinned.sortedByDescending { it.lastTimestamp }
    }

    private fun ConversationSnapshot.toSummary(
            members: Map<MemberId, MemberProfile>
    ): ConversationSummary {
        val remoteMembers = memberIds.filterNot { it == localMemberId }
        val isSelf = remoteMembers.isEmpty()
        val lastMessage = messages.maxByOrNull { it.timestamp }
        val preview =
                lastMessage?.let { formatPreview(it) }
                        ?: if (isSelf) "本机收藏夹" else "Tap to start chatting"
        val unreadCount =
                messages.count { it.senderId != localMemberId && it.status == MessageStatus.QUEUED }
        val avatarSeed = remoteMembers.firstOrNull() ?: localMemberId
        val key = resolveConversationKey(memberIds + localMemberId)
        return ConversationSummary(
                conversationId = conversationId,
                title = if (isSelf) "我" else conversationTitle(remoteMembers, members),
                preview = preview,
                lastTimestamp = lastMessage?.timestamp ?: 0L,
                unreadCount = unreadCount,
                avatarSeed = avatarSeed,
                isSelf = isSelf,
                conversationKey = key,
                isPinned = isPinned
        )
    }

    private fun summaryKey(summary: ConversationSummary): String {
        return if (summary.isSelf) SELF_SUMMARY_ID else summary.title.lowercase(Locale.getDefault())
    }

    private fun selfSummary(): ConversationSummary {
        val snapshot = _conversations.value[conversationIdFor(localMemberId)]
        val lastMessage = snapshot?.messages?.maxByOrNull { it.timestamp }
        val preview = lastMessage?.let { formatPreview(it) } ?: "本机收藏夹"
        val unread =
                snapshot?.messages?.count {
                    it.senderId != localMemberId && it.status == MessageStatus.QUEUED
                }
                        ?: 0
        return ConversationSummary(
                conversationId = conversationIdFor(localMemberId),
                title = "我",
                preview = preview,
                lastTimestamp = lastMessage?.timestamp ?: 0L,
                unreadCount = unread,
                avatarSeed = localMemberId,
                isSelf = true,
                conversationKey = SELF_SUMMARY_ID,
                isPinned = snapshot?.isPinned ?: false
        )
    }

    private fun resolveConversationKey(memberIds: Set<MemberId>): String {
        val remoteMembers = memberIds.filterNot { it == localMemberId }.sorted()
        return if (remoteMembers.isEmpty()) SELF_SUMMARY_ID else remoteMembers.joinToString(":")
    }

    private fun resolveConversationTarget(conversationId: ConversationId): ConversationTarget {
        val snapshot = _conversations.value[conversationId] ?: return ConversationTarget.Unknown
        val remoteMembers = snapshot.memberIds.filterNot { it == localMemberId }.toSet()
        return if (remoteMembers.isEmpty()) ConversationTarget.Self
        else ConversationTarget.Remote(remoteMembers)
    }

    private fun conversationTitle(
            memberIds: List<MemberId>,
            members: Map<MemberId, MemberProfile>
    ): String {
        if (memberIds.isEmpty()) {
            return "我"
        }
        return memberIds.joinToString(separator = ", ") { memberId ->
            val profile = members[memberId]
            profile?.remoteNickname?.takeIf { it.isNotBlank() }
                    ?: profile?.localNickname?.takeIf { it.isNotBlank() } ?: memberId.take(6)
        }
    }

    private fun formatPreview(message: ChatMessage): String {
        return when {
            message.attachment?.type == AttachmentType.PHOTO -> "发送了图片"
            message.content.isBlank() -> "(empty message)"
            message.content.length > 40 -> message.content.take(40) + "..."
            else -> message.content
        }
    }

    private object BuildNickname {
        fun local(): String = Build.MODEL ?: "Android"
    }

    private fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get()
                .lifecycle
                .currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    }

    private fun showNotification(conversationId: ConversationId, message: ChatMessage) {
        val channelId = "NearbyChater_messages"
        val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(channelId, "Messages", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
        val pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // 获取发送者信息
        val senderProfile = _members.value[message.senderId]
        val senderName = senderProfile?.let {
            it.localNickname?.takeIf { it.isNotBlank() }
                    ?: it.remoteNickname?.takeIf { it.isNotBlank() }
                    ?: it.deviceModel?.takeIf { it.isNotBlank() }
        } ?: message.senderId.take(6)

        val contentText =
                when (message.type) {
                    MessageType.IMAGE -> "[图片]"
                    else -> message.content
                }

        val notification =
                NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("${senderName} 发来消息")
                        .setContentText(contentText)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

        notificationManager.notify(message.id.hashCode(), notification)
    }

    suspend fun setConversationPinned(conversationId: ConversationId, pinned: Boolean) {
        onDb { chatDao.setConversationPinned(conversationId, pinned) }
        _conversations.update { conversations ->
            val snapshot = conversations[conversationId] ?: return@update conversations
            conversations + (conversationId to snapshot.copy(isPinned = pinned))
        }
    }
}

private sealed interface ConversationTarget {
    data object Unknown : ConversationTarget
    data object Self : ConversationTarget
    data class Remote(val memberIds: Set<MemberId>) : ConversationTarget
}
