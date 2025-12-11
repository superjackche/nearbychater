package com.example.nearbychater.ui.state

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.nearbychater.data.settings.SettingsRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ViewModel：负责UI数据与逻辑，生命周期长于Activity (屏幕旋转不丢失数据)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
public class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val logManager = LogManager(application)
    private val settingsRepository = SettingsRepository(application)
    private val nearbyChatService =
            (application as com.example.nearbychater.NearbyChaterApplication).nearbyChatService
    private val chatRepository =
            (application as com.example.nearbychater.NearbyChaterApplication).chatRepository

    // MutableStateFlow：可观察的数据流 (类似广播)，UI层订阅更新
    private val _activeConversationId = MutableStateFlow<ConversationId?>(null)
    private val _diagnosticsBubble = MutableStateFlow(DiagnosticsBubbleState())
    private var hideBubbleJob: Job? = null
    private var snoozedDiagnosticsKey: String? = null
    private var snoozedDiagnosticsUntil: Long = 0L
    // 添加缺失的属性
    private val _isRefreshing = MutableStateFlow(false)
    private val _isSending = MutableStateFlow(false)
    private var periodicRefreshJob: Job = Job()

    public val members: StateFlow<List<MemberProfile>> = chatRepository.members
    public val conversationSnapshots: StateFlow<Map<ConversationId, ConversationSnapshot>> =
            chatRepository.conversations
    public val conversationSummaries: StateFlow<List<ConversationSummary>> =
            chatRepository.conversationSummaries
    public val activeConversationId: StateFlow<ConversationId?> =
            _activeConversationId.asStateFlow()
    public val diagnosticsBubble: StateFlow<DiagnosticsBubbleState> =
            _diagnosticsBubble.asStateFlow()
    public val selfMemberId: MemberId = chatRepository.selfMemberId
    public val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    public val isSending: StateFlow<Boolean> = _isSending.asStateFlow()
    public val conversationAliases: StateFlow<Map<ConversationId, String>> =
            settingsRepository.conversationAliases

    // flatMapLatest：切换流，当ID变化时取消旧请求，订阅新会话消息
    // stateIn：将流转换为热状态，WhileSubscribed(5000)实现自动暂停/恢复以节省资源
    public val messages: StateFlow<List<ChatMessage>> =
            _activeConversationId
                    .flatMapLatest { id ->
                        if (id == null) flowOf(emptyList())
                        else chatRepository.conversationMessages(id)
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            settingsRepository.diagnosticsEnabled.collect { enabled ->
                _diagnosticsBubble.value =
                        _diagnosticsBubble.value.copy(
                                isEnabled = enabled,
                                isVisible =
                                        if (enabled) _diagnosticsBubble.value.isVisible else false
                        )
                if (!enabled) {
                    hideBubbleJob?.cancel()
                }
            }
        }
        viewModelScope.launch {
            chatRepository.diagnostics.collect { event ->
                if (_diagnosticsBubble.value.isEnabled) {
                    showDiagnostics(event)
                }
            }
        }
        viewModelScope.launch {
            chatRepository.conversations.collect { conversations ->
                if (_activeConversationId.value == null && conversations.isNotEmpty()) {
                    _activeConversationId.value = conversations.keys.first()
                }
            }
        }
        // 定时刷新任务 (5秒)
        // 使用协程避免阻塞主线程
        periodicRefreshJob =
                viewModelScope.launch {
                    while (true) {
                        chatRepository.refresh()
                        delay(5_000)
                    }
                }
    }

    public fun selectConversation(conversationId: ConversationId) {
        _activeConversationId.value = conversationId
    }

    public fun focusOnMember(memberId: MemberId) {
        _activeConversationId.value = chatRepository.conversationIdFor(memberId)
    }

    public fun ensureConversation(memberId: MemberId): ConversationId =
            ensureConversation(listOf(memberId))

    public fun ensureConversation(memberIds: List<MemberId>): ConversationId {
        val normalized =
                memberIds.map { it.trim() }.filter { it.isNotEmpty() && it != selfMemberId }.toSet()
        val conversationId = chatRepository.ensureConversationMembers(normalized)
        _activeConversationId.value = conversationId
        return conversationId
    }

    public fun conversationIdFor(memberId: MemberId): ConversationId =
            chatRepository.conversationIdFor(memberId)

    public fun isMemberConnected(memberId: MemberId): Boolean =
            chatRepository.isMemberConnected(memberId)

    public fun refreshConversations() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                chatRepository.refresh()
                delay(300)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    public fun dismissDiagnosticsBubble() {
        val currentEvent = _diagnosticsBubble.value.latestEvent
        if (currentEvent != null) {
            snoozedDiagnosticsKey = diagnosticsKey(currentEvent)
            snoozedDiagnosticsUntil = System.currentTimeMillis() + 10_000L
        }
        _diagnosticsBubble.value = _diagnosticsBubble.value.copy(isVisible = false)
        hideBubbleJob?.cancel()
    }

    public fun sendChatMessage(text: String, attachment: Attachment? = null) {
        val conversationId =
                _activeConversationId.value
                        ?: members.value.firstOrNull { it.memberId != selfMemberId }?.let {
                            chatRepository.ensureConversationMembers(setOf(it.memberId))
                        }
                                ?: chatRepository.ensureConversationMembers(emptySet())
        viewModelScope.launch {
            _isSending.value = true
            try {
                chatRepository.sendMessage(conversationId, text, attachment)
            } finally {
                _isSending.value = false
            }
        }
    }

    public fun cancelMessage(messageId: String) {
        val conversationId = _activeConversationId.value ?: return
        viewModelScope.launch { chatRepository.cancelMessage(conversationId, messageId) }
    }

    public fun retryMessage(messageId: String) {
        val conversationId = _activeConversationId.value ?: return
        viewModelScope.launch { chatRepository.retryMessage(conversationId, messageId) }
    }

    public fun sendPhoto(uri: Uri) {
        val targetConversation =
                _activeConversationId.value
                        ?: members.value.firstOrNull()?.let {
                            chatRepository.ensureConversationMembers(setOf(it.memberId))
                        }
                                ?: chatRepository.ensureConversationMembers(emptySet())
        // 启动协程处理图片发送
        // withContext(Dispatchers.IO)：切换至IO线程执行耗时操作(图片压缩)，防止主线程卡顿(ANR)
        viewModelScope.launch {
            _isSending.value = true
            try {
                val attachment =
                        withContext(Dispatchers.IO) { createPhotoAttachment(uri) } ?: return@launch
                chatRepository.sendMessage(targetConversation, "", attachment)
            } finally {
                _isSending.value = false
            }
        }
    }

    private fun createPhotoAttachment(uri: Uri): Attachment? {
        val resolver = getApplication<Application>().contentResolver
        val bitmap =
                resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
                        ?: return null
        val scaled = scaleBitmap(bitmap, 720)
        val (bytes, mimeType) = compressBitmapToLimit(scaled)
        return Attachment(
                type = AttachmentType.PHOTO,
                mimeType = mimeType,
                dataBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        )
    }

    private fun scaleBitmap(source: Bitmap, maxSize: Int): Bitmap {
        val width = source.width
        val height = source.height
        val maxDim = maxOf(width, height)
        if (maxDim <= maxSize) return source
        val ratio = maxSize.toFloat() / maxDim
        val targetWidth = (width * ratio).toInt()
        val targetHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun compressBitmapToLimit(
            bitmap: Bitmap,
            maxBytes: Int = 60_000
    ): Pair<ByteArray, String> {
        var quality = 80
        var currentBitmap = bitmap
        var data = compressBitmap(currentBitmap, quality)
        while (data.size > maxBytes && quality > 40) {
            quality -= 10
            data = compressBitmap(currentBitmap, quality)
        }
        var maxDim = maxOf(currentBitmap.width, currentBitmap.height)
        while (data.size > maxBytes && maxDim > 320) {
            maxDim = (maxDim * 0.8f).toInt().coerceAtLeast(320)
            // 创建新的缩放Bitmap，并回收旧的临时Bitmap
            val scaledBitmap = scaleBitmap(currentBitmap, maxDim)
            if (currentBitmap != bitmap) {
                currentBitmap.recycle()
            }
            currentBitmap = scaledBitmap
            quality = 70
            data = compressBitmap(currentBitmap, quality)
        }
        if (data.size > maxBytes) {
            data = compressBitmap(currentBitmap, 40)
        }
        // 回收临时Bitmap，除非它是原始Bitmap
        if (currentBitmap != bitmap) {
            currentBitmap.recycle()
        }
        return data to "image/jpeg"
    }

    private fun compressBitmap(bitmap: Bitmap, quality: Int): ByteArray =
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.toByteArray()
            }
    public fun deleteConversation(conversationId: ConversationId) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
        }
    }

    public fun setConversationPinned(conversationId: ConversationId, pinned: Boolean) {
        viewModelScope.launch { chatRepository.setConversationPinned(conversationId, pinned) }
    }
    public fun updateLocalNickname(memberId: MemberId, nickname: String) {
        viewModelScope.launch { chatRepository.updateLocalNickname(memberId, nickname) }
    }

    public fun setConversationAlias(conversationId: ConversationId, title: String) {
        viewModelScope.launch {
            settingsRepository.setConversationAlias(conversationId, title.trim())
        }
    }

    public fun clearConversationAlias(conversationId: ConversationId) {
        viewModelScope.launch { settingsRepository.clearConversationAlias(conversationId) }
    }
    private fun showDiagnostics(event: DiagnosticsEvent) {
        val key = diagnosticsKey(event)
        if (System.currentTimeMillis() < snoozedDiagnosticsUntil && key == snoozedDiagnosticsKey) {
            return
        }
        snoozedDiagnosticsKey = null
        snoozedDiagnosticsUntil = 0L
        _diagnosticsBubble.value =
                _diagnosticsBubble.value.copy(latestEvent = event, isVisible = true)
        hideBubbleJob?.cancel()
        hideBubbleJob =
                viewModelScope.launch {
                    delay(1_000)
                    _diagnosticsBubble.value = _diagnosticsBubble.value.copy(isVisible = false)
                }
    }

    private fun diagnosticsKey(event: DiagnosticsEvent): String = "${event.code}:${event.message}"

    override fun onCleared() {
        super.onCleared()
        nearbyChatService.stop()
        // 取消周期性刷新任务，避免内存泄漏
        periodicRefreshJob.cancel()
    }
}

public data class DiagnosticsBubbleState(
        val isEnabled: Boolean = true,
        val latestEvent: DiagnosticsEvent? = null,
        val isVisible: Boolean = false
)
