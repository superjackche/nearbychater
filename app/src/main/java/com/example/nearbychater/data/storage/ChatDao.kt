package com.example.nearbychater.data.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.nearbychater.core.model.Attachment
import com.example.nearbychater.core.model.AttachmentType
import com.example.nearbychater.core.model.ChatMessage
import com.example.nearbychater.core.model.ConversationId
import com.example.nearbychater.core.model.ConversationSnapshot
import com.example.nearbychater.core.model.MemberId
import com.example.nearbychater.core.model.MemberProfile
import com.example.nearbychater.core.model.MessageStatus
import com.example.nearbychater.core.model.MessageType

// ChatDao是Data Access Object（数据访问对象）的缩写
// DAO模式把数据库操作封装成一个个函数，让上层代码不用直接写SQL
// 这样代码更清晰，也方便测试
public class ChatDao(context: Context) {
    // helper是AppDatabaseHelper的实例，用于获取数据库连接
    // applicationContext确保不会因为Activity销毁导致内存泄漏
    private val helper = AppDatabaseHelper(context.applicationContext)

    // 从数据库读取所有成员信息，返回Map<成员ID, 成员资料>
    // Map比List查找快，用成员ID可以O(1)时间找到成员信息
    public fun readMembers(): Map<MemberId, MemberProfile> {
        // readableDatabase获取只读数据库连接（效率更高）
        // query()是Android封装的查询函数，比直接写SQL简单
        // 参数：表名, 要查的列(null=全部), where条件, where参数, groupBy, having, orderBy
        return helper.readableDatabase.query("members", null, null, null, null, null, null).use {
                cursor ->
            // use{}会在代码块结束后自动关闭cursor，避免资源泄漏
            // 类似Python的with语句或C++的RAII
            // buildMap是Kotlin的构建器，用于创建Map
            buildMap {
                // cursor就像指向结果集的指针
                // moveToNext()移动到下一行，类似C语言的指针++
                while (cursor.moveToNext()) {
                    // 从当前行的各列读取数据，构造MemberProfile对象
                    val member =
                            MemberProfile(
                                    memberId = cursor.getString("member_id")!!, // !!表示确定非null
                                    localNickname = cursor.getString("local_nickname"),
                                    remoteNickname = cursor.getString("remote_nickname"),
                                    deviceModel = cursor.getString("device_model"),
                                    isOnline = cursor.getInt("is_online") == 1, // SQLite用0/1表示布尔值
                                    lastSeenAt = cursor.getLong("last_seen_at")
                            )
                    // put把成员加入Map
                    put(member.memberId, member)
                }
            }
        }
    }

    // 读取所有会话及其消息，返回Map<会话ID, 会话快照>
    // 这个函数比较复杂，需要从3个表读取数据并组合
    public fun readConversations(): Map<ConversationId, ConversationSnapshot> {
        val db = helper.readableDatabase

        // 第一步：读取消息并按会话分组
        // mutableMapOf创建可变Map，可以动态添加元素
        val messagesByConversation = mutableMapOf<ConversationId, MutableList<ChatMessage>>()
        db.query("messages", null, null, null, null, null, "timestamp ASC").use { cursor ->
            // timestamp ASC表示按时间升序排列，早的消息在前
            while (cursor.moveToNext()) {
                // toChatMessage()是扩展函数，把cursor转换成ChatMessage对象
                val message = cursor.toChatMessage()
                // getOrPut：如果key不存在，就用lambda创建并插入；然后返回value
                // 这样可以避免重复检查key是否存在
                messagesByConversation
                        .getOrPut(message.conversationId) { mutableListOf() }
                        .add(message)
            }
        }

        // 第二步：读取会话成员关联关系
        val membersByConversation = mutableMapOf<ConversationId, MutableSet<MemberId>>()
        db.query("conversation_members", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val conversationId = cursor.getString("conversation_id")!!
                val memberId = cursor.getString("member_id")!!
                // 用Set存储成员ID，自动去重
                membersByConversation.getOrPut(conversationId) { mutableSetOf() }.add(memberId)
            }
        }

        // 第三步：读取会话基本信息，并组装完整的ConversationSnapshot
        return db.query("conversations", null, null, null, null, null, null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val conversationId = cursor.getString("conversation_id")!!
                    // 从前面读取的messagesByConversation和membersByConversation中获取数据
                    // 如果不存在，用空集合/空列表作为默认值
                    put(
                            conversationId,
                            ConversationSnapshot(
                                    conversationId = conversationId,
                                    messages = messagesByConversation[conversationId]?.toList()
                                                    ?: emptyList(),
                                    memberIds = membersByConversation[conversationId] ?: emptySet(),
                                    conversationKey = cursor.getString("conversation_key"),
                                    isPinned = cursor.getInt("pinned") == 1
                            )
                    )
                }
            }
        }
    }

    // upsert = update + insert
    // 如果成员已存在就更新，不存在就插入
    // 这样不用分两步操作，代码更简洁
    public fun upsertMember(profile: MemberProfile) {
        // writableDatabase获取可写数据库连接
        helper.writableDatabase.insertWithOnConflict(
                "members",
                null, // nullColumnHack，通常传null就行
                // ContentValues类似Map，用于存储要插入的列值
                // apply{}是Kotlin的作用域函数，在{}内this指向ContentValues对象
                ContentValues().apply {
                    put("member_id", profile.memberId)
                    put("local_nickname", profile.localNickname)
                    put("remote_nickname", profile.remoteNickname)
                    put("device_model", profile.deviceModel)
                    put("is_online", if (profile.isOnline) 1 else 0)
                    put("last_seen_at", profile.lastSeenAt)
                },
                // CONFLICT_REPLACE表示遇到主键冲突时，替换旧记录
                // 这就是upsert的原理
                SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // 更新成员的在线状态
    // 这个函数比upsertMember特殊，它先尝试插入，如果已存在则只更新部分字段
    public fun updateMemberOnlineState(
            memberId: MemberId,
            nickname: String?,
            isOnline: Boolean,
            lastSeenAt: Long
    ) {
        // 第一步：先尝试插入，如果member_id已存在则忽略
        helper.writableDatabase.insertWithOnConflict(
                "members",
                null,
                ContentValues().apply {
                    put("member_id", memberId)
                    put("remote_nickname", nickname)
                    put("is_online", if (isOnline) 1 else 0)
                    put("last_seen_at", lastSeenAt)
                },
                // CONFLICT_IGNORE表示遇到冲突时忽略新数据，保留旧数据
                SQLiteDatabase.CONFLICT_IGNORE
        )
        // 第二步：无论是否插入成功，都执行更新操作
        // update()只会更新已存在的记录
        helper.writableDatabase.update(
                "members",
                ContentValues().apply {
                    // nickname?.let只在nickname不为null时执行
                    nickname?.let { put("remote_nickname", it) }
                    put("is_online", if (isOnline) 1 else 0)
                    put("last_seen_at", lastSeenAt)
                },
                "member_id = ?", // where条件
                arrayOf(memberId) // where条件的参数，?会被替换成这个值
        )
    }

    // 确保会话存在，如果不存在就创建
    // 同时确保所有memberIds都在conversation_members表中
    public fun ensureConversation(
            conversationId: ConversationId,
            conversationKey: String?,
            memberIds: Set<MemberId>
    ) {
        // 插入会话记录，如果已存在则忽略
        helper.writableDatabase.insertWithOnConflict(
                "conversations",
                null,
                ContentValues().apply {
                    put("conversation_id", conversationId)
                    put("conversation_key", conversationKey)
                    // memberIds.size <= 1表示这是自己和自己的会话
                    put("is_self", if (memberIds.size <= 1) 1 else 0)
                    put("pinned", 0)
                },
                SQLiteDatabase.CONFLICT_IGNORE
        )
        // 遍历所有成员ID，确保每个成员都在conversation_members表中
        val db = helper.writableDatabase
        memberIds.forEach { memberId ->
            db.insertWithOnConflict(
                    "conversation_members",
                    null,
                    ContentValues().apply {
                        put("conversation_id", conversationId)
                        put("member_id", memberId)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    // 插入或更新消息
    // 如果message_id已存在，就更新整条记录
    public fun insertOrUpdateMessage(message: ChatMessage) {
        helper.writableDatabase.insertWithOnConflict(
                "messages",
                null,
                ContentValues().apply {
                    put("message_id", message.id)
                    put("conversation_id", message.conversationId)
                    put("sender_id", message.senderId)
                    put("content", message.content)
                    put("timestamp", message.timestamp)
                    // enum的name属性返回枚举常量的字符串形式，比如"SENT"
                    put("status", message.status.name)
                    put("should_relay", if (message.shouldRelay) 1 else 0)
                    put("message_type", message.type.name)
                    // ?.表示如果attachment为null，整个表达式返回null
                    // 这样attachment_type等字段会是null
                    put("attachment_type", message.attachment?.type?.name)
                    put("attachment_mime", message.attachment?.mimeType)
                    put("attachment_data", message.attachment?.dataBase64)
                },
                SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // 只更新消息的状态字段
    // 比insertOrUpdateMessage高效，因为只更新需要的字段
    public fun updateMessageStatus(
            conversationId: ConversationId,
            messageId: String,
            status: MessageStatus,
            shouldRelay: Boolean? = null // 默认参数null，表示可选
    ) {
        helper.writableDatabase.update(
                "messages",
                ContentValues().apply {
                    put("status", status.name)
                    // 只有shouldRelay不为null时才更新这个字段
                    shouldRelay?.let { put("should_relay", if (it) 1 else 0) }
                },
                "message_id = ? AND conversation_id = ?", // 用两个条件确保精确匹配
                arrayOf(messageId, conversationId)
        )
    }

    // 删除会话及其所有关联数据
    // 由于设置了ON DELETE CASCADE，删除会话时会自动删除相关消息
    public fun deleteConversation(conversationId: ConversationId) {
        // delete()函数格式：表名, where条件, where参数
        helper.writableDatabase.delete(
                "conversations",
                "conversation_id = ?",
                arrayOf(conversationId)
        )
        // conversation_members不会自动删除，需要手动删除
        helper.writableDatabase.delete(
                "conversation_members",
                "conversation_id = ?",
                arrayOf(conversationId)
        )
    }

    // 设置会话是否置顶
    public fun setConversationPinned(conversationId: ConversationId, pinned: Boolean) {
        helper.writableDatabase.update(
                "conversations",
                ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
                "conversation_id = ?",
                arrayOf(conversationId)
        )
    }

    // 删除成员
    // 会同时删除conversation_members中的关联记录
    public fun deleteMember(memberId: MemberId) {
        // 先删除关联表中的记录，避免外键约束问题
        helper.writableDatabase.delete("conversation_members", "member_id = ?", arrayOf(memberId))
        helper.writableDatabase.delete("members", "member_id = ?", arrayOf(memberId))
    }

    // Cursor扩展函数：安全地获取指定列的字符串值
    // 私有函数，只在这个类内部使用
    // getColumnIndex返回列的索引，-1表示列不存在
    // takeIf { it >= 0 }如果索引>=0就返回索引，否则返回null
    // ?.let { getString(it) }如果不是null就获取字符串值
    private fun Cursor.getString(column: String): String? =
            getColumnIndex(column).takeIf { it >= 0 }?.let { getString(it) }

    // 获取整数值，默认返回0
    private fun Cursor.getInt(column: String): Int =
            getColumnIndex(column).takeIf { it >= 0 }?.let { getInt(it) } ?: 0

    // 获取长整数值，默认返回0L
    private fun Cursor.getLong(column: String): Long =
            getColumnIndex(column).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L

    // 将Cursor当前行转换为ChatMessage对象
    // 这个函数把数据库的扁平数据转换成对象模型
    private fun Cursor.toChatMessage(): ChatMessage {
        // 先获取attachment_type，判断是否有附件
        val attachmentType = getString("attachment_type")
        val attachment =
                attachmentType?.let {
                    // 如果有附件类型，就构造Attachment对象
                    // valueOf把字符串转成枚举值，比如"PHOTO" -> AttachmentType.PHOTO
                    Attachment(
                            type = AttachmentType.valueOf(it),
                            mimeType = getString("attachment_mime") ?: "image/jpeg", // 默认JPEG格式
                            dataBase64 = getString("attachment_data") ?: ""
                    )
                }
        // 构造并返回ChatMessage对象
        return ChatMessage(
                id = getString("message_id")!!,
                conversationId = getString("conversation_id")!!,
                senderId = getString("sender_id")!!,
                content = getString("content") ?: "",
                timestamp = getLong("timestamp"),
                status = MessageStatus.valueOf(getString("status")!!),
                shouldRelay = getInt("should_relay") == 1,
                type = MessageType.valueOf(getString("message_type") ?: "TEXT"),
                attachment = attachment
        )
    }
}
