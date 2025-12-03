package com.example.nearbychater.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

// ClipboardUtils: 剪贴板工具类
// 提供复制文本到系统剪贴板的功能
// object表示这是单例，类似Java的static工具类
object ClipboardUtils {
    // copyText: 复制文本到剪贴板
    // suspend表示这是挂起函数，但实际上这个操作很快，不需要在后台线程
    // 保持suspend是为了API一致性，可以在协程中调用
    suspend fun copyText(context: Context, text: String) {
        // getSystemService获取剪贴板管理器
        // ClipboardManager是Android系统服务，管理剪贴板
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        // ClipData: 剪贴板数据对象
        // newPlainText创建纯文本数据
        // 第一个参数是标签(label)，通常是描述性文字
        // 第二个参数是实际要复制的文本
        val clip = ClipData.newPlainText("Member ID", text)

        // setPrimaryClip设置到剪贴板
        // 用户就可以在其他应用粘贴了
        clipboard?.setPrimaryClip(clip)
    }
}
