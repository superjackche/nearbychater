package com.example.miniwechat.data.storage

import android.database.Cursor
import com.example.miniwechat.core.model.Attachment
import com.example.miniwechat.core.model.AttachmentType
import com.example.miniwechat.core.model.ChatMessage
import com.example.miniwechat.core.model.MemberProfile
import com.example.miniwechat.core.model.MessageStatus
import com.example.miniwechat.core.model.MessageType

// 这个文件定义扩展函数(Extension Functions)
// 扩展函数是Kotlin的特性，可以给已有的类添加新方法，而不需要继承
// 类似Python的monkey patching，但更安全

// 扩展函数：从Cursor中安全地获取字符串值
// fun Cursor.xxx表示给Cursor类添加一个名为xxx的方法
// 这样可以直接用cursor.getStringOrNull("column")调用
fun Cursor.getStringOrNull(columnName: String): String? {
    // getColumnIndex返回列的索引位置
    // 如果列不存在，返回-1
    val columnIndex = getColumnIndex(columnName)
    // 只有索引>=0时才尝试获取字符串，否则返回null
    // 这样可以避免IndexOutOfBoundsException异常
    return if (columnIndex >= 0) getString(columnIndex) else null
}

// 扩展函数：获取字符串值，如果为null则返回默认值
// 默认参数defaultValue=""，调用时可以省略这个参数
fun Cursor.getStringOrDefault(columnName: String, defaultValue: String = ""): String {
    // ?:是Elvis操作符，左边为null时返回右边的值
    // 相当于Python的: return xxx if xxx is not None else defaultValue
    return getStringOrNull(columnName) ?: defaultValue
}

// 扩展函数：获取整数值
// SQLite的INTEGER类型对应Java/Kotlin的int/Int
fun Cursor.getIntOrDefault(columnName: String, defaultValue: Int = 0): Int {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex >= 0) getInt(columnIndex) else defaultValue
}

// 扩展函数：获取长整数值
// SQLite用INTEGER存储时间戳，需要用Long类型接收
fun Cursor.getLongOrDefault(columnName: String, defaultValue: Long = 0L): Long {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex >= 0) getLong(columnIndex) else defaultValue
}

// 扩展函数：获取布尔值
// SQLite没有布尔类型，用INTEGER的0/1表示false/true
// 这个函数帮我们做转换，调用时就不用每次都写 == 1
fun Cursor.getBooleanOrDefault(columnName: String, defaultValue: Boolean = false): Boolean {
    // 先获取整数值（0或1），然后判断是否!=0
    // !=0返回true，==0返回false
    return getIntOrDefault(columnName, if (defaultValue) 1 else 0) != 0
}

// 扩展函数：将Cursor转换为MemberProfile对象
// 这个函数封装了MemberProfile的构造逻辑
// 调用时只需要cursor.toMemberProfile()就能得到对象
fun Cursor.toMemberProfile(): MemberProfile {
    return MemberProfile(
            // getStringOrDefault确保即使列不存在也返回空字符串
            memberId = getStringOrDefault("member_id"),
            // 昵称可以为null，所以用getStringOrNull
            localNickname = getStringOrNull("local_nickname"),
            remoteNickname = getStringOrNull("remote_nickname"),
            deviceModel = getStringOrNull("device_model"),
            // getBooleanOrDefault自动把0/1转成false/true
            isOnline = getBooleanOrDefault("is_online", false),
            // lastSeenAt如果为0，就用当前时间作为默认值
            lastSeenAt = getLongOrDefault("last_seen_at", System.currentTimeMillis())
    )
}

// 扩展函数：将Cursor转换为ChatMessage对象
// 这个比toMemberProfile复杂，因为ChatMessage包含可选的Attachment
fun Cursor.toChatMessage(): ChatMessage {
    // 先获取attachment_type，看看是否有附件
    val attachmentType = getStringOrNull("attachment_type")
    // let{}只在attachmentType不为null时执行
    // 这种写法比if (attachmentType != null) {...}更简洁
    val attachment =
            attachmentType?.let {
                Attachment(
                        // valueOf把字符串转成枚举值，"PHOTO" -> AttachmentType.PHOTO
                        type = AttachmentType.valueOf(it),
                        // mimeType默认"image/jpeg"
                        mimeType = getStringOrNull("attachment_mime") ?: "image/jpeg",
                        // attachment_data可能很大（Base64编码的图片），但也可能为空
                        dataBase64 = getStringOrNull("attachment_data") ?: ""
                )
            }

    // 构造ChatMessage对象
    return ChatMessage(
            id = getStringOrDefault("message_id"),
            conversationId = getStringOrDefault("conversation_id"),
            senderId = getStringOrDefault("sender_id"),
            content = getStringOrDefault("content"),
            timestamp = getLongOrDefault("timestamp"),
            // MessageStatus是枚举，用valueOf转换
            status = MessageStatus.valueOf(getStringOrDefault("status")),
            shouldRelay = getBooleanOrDefault("should_relay", true),
            // message_type默认"TEXT"
            type = MessageType.valueOf(getStringOrDefault("message_type", "TEXT")),
            attachment = attachment
    )
}
