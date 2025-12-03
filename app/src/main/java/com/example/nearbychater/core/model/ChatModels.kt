package com.example.nearbychater.core.model

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// typealias：类型别名 (类比C语言typedef)，提升代码语义
public typealias ConversationId = String

public typealias MemberId = String

// @Serializable：启用Kotlinx Serialization序列化支持 (JSON转换)
// data class：自动生成equals/hashCode/toString (类比C语言struct，但功能更强)
@Serializable
data class ChatMessage(
        @SerialName("id") val id: String = UUID.randomUUID().toString(),
        @SerialName("conversationId") val conversationId: ConversationId,
        @SerialName("senderId") val senderId: MemberId,
        @SerialName("content") val content: String,
        @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis(),
        @SerialName("status") val status: MessageStatus = MessageStatus.QUEUED,
        @SerialName("shouldRelay") val shouldRelay: Boolean = true,
        @SerialName("attachment") val attachment: Attachment? = null,
        @SerialName("type") val type: MessageType = MessageType.TEXT
)

@Serializable
enum class MessageType {
        TEXT,
        IMAGE,
        ACK
}

// enum class：枚举类型，定义消息状态常量
enum class MessageStatus {
        QUEUED,
        SENDING,
        SENT,
        DELIVERED,
        CANCELLED,
        FAILED
}

@Serializable
data class MemberProfile(
        @SerialName("memberId") val memberId: MemberId,
        @SerialName("localNickname") val localNickname: String? = null,
        @SerialName("remoteNickname") val remoteNickname: String? = null,
        @SerialName("deviceModel") val deviceModel: String? = null,
        @SerialName("isOnline") val isOnline: Boolean = false,
        @SerialName("lastSeenAt") val lastSeenAt: Long = System.currentTimeMillis()
)

@Serializable
data class ConversationSnapshot(
        @SerialName("conversationId") val conversationId: ConversationId,
        @SerialName("messages") val messages: List<ChatMessage> = emptyList(),
        @SerialName("memberIds") val memberIds: Set<MemberId> = emptySet(),
        @SerialName("conversationKey") val conversationKey: String? = null,
        @SerialName("isPinned") val isPinned: Boolean = false
)

data class ConversationSummary(
        val conversationId: ConversationId,
        val title: String,
        val preview: String,
        val lastTimestamp: Long,
        val unreadCount: Int = 0,
        val avatarSeed: String = conversationId,
        val isSelf: Boolean = false,
        val conversationKey: String? = null,
        val isPinned: Boolean = false
)

@Serializable
data class Attachment(
        @SerialName("type") val type: AttachmentType,
        @SerialName("mimeType") val mimeType: String,
        @SerialName("dataBase64") val dataBase64: String
)

@Serializable
enum class AttachmentType {
        PHOTO
}

// MeshEnvelope：网络传输信封，包含消息负载与路由信息 (用于P2P转发)
@Serializable
data class MeshEnvelope(
        @SerialName("conversationId") val conversationId: ConversationId,
        @SerialName("message") val message: ChatMessage,
        @SerialName("originId") val originId: MemberId,
        @SerialName("hopCount") val hopCount: Int = 0,
        @SerialName("maxHops") val maxHops: Int = DEFAULT_MAX_HOPS,
        @SerialName("packetId") val packetId: String = UUID.randomUUID().toString(),
        @SerialName("participants") val participants: Set<MemberId> = emptySet()
) {
        companion object {
                // companion object：伴生对象 (类比Java static)
                const val DEFAULT_MAX_HOPS: Int = 4
        }
}

data class DiagnosticsEvent(
        val code: String,
        val message: String,
        val cause: Throwable? = null,
        val timestamp: Long = System.currentTimeMillis()
)
