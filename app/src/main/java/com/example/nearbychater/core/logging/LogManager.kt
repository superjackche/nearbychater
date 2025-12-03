package com.example.nearbychat.core.logging

import android.content.Context
import com.example.nearbychat.core.model.DiagnosticsEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 日志文件最大大小: 1MB
// 超过这个大小会自动轮转(归档旧日志,创建新日志文件)
private const val MAX_LOG_BYTES: Long = 1_000_000L
// 日志文件夹名称
private const val LOG_FOLDER = "logs"
// 当前日志文件名
private const val LOG_FILE_NAME = "diagnostics.log"

// LogManager: 日志管理器
// 职责:
// 1. 持久化诊断事件到本地文件
// 2. 日志轮转(避免单个文件过大)
// 3. 提供日志读取/清空/归档列表API
// 文件存储在应用私有目录: /data/data/包名/files/logs/
public class LogManager(private val context: Context) {
    // logDir: 日志目录
    // by lazy表示延迟初始化,第一次访问时才创建
    // File(parent, child)创建文件路径: filesDir/logs
    private val logDir: File by lazy { File(context.filesDir, LOG_FOLDER) }

    // logFile: 当前日志文件
    // 指向 logs/diagnostics.log
    private val logFile: File by lazy { File(logDir, LOG_FILE_NAME) }

    // timestampFormatter: 时间戳格式化器
    // 用于生成归档文件名: diagnostics-20231120-143022.log
    private val timestampFormatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    // log: 记录诊断事件
    // suspend表示这是挂起函数,会在IO线程执行
    public suspend fun log(event: DiagnosticsEvent) {
        // withContext切换到IO线程执行文件操作
        // Dispatchers.IO是专门用于文件/网络IO的调度器
        withContext(Dispatchers.IO) {
            ensureLogFile() // 确保日志文件存在
            rotateIfNeeded() // 如果文件太大,进行轮转
            logFile.appendText(formatEvent(event)) // 追加日志
        }
    }

    // readLogs: 读取所有日志行
    // 返回String列表,每一行一个元素
    public suspend fun readLogs(): List<String> =
            withContext(Dispatchers.IO) {
                if (!logFile.exists()) return@withContext emptyList()
                logFile.readLines() // 读取所有行
            }

    // clearLogs: 清空日志文件
    // 将文件内容置空,不删除文件
    public suspend fun clearLogs() {
        withContext(Dispatchers.IO) {
            if (logFile.exists()) {
                logFile.writeText("") // 写入空字符串
            }
        }
    }

    // listArchives: 列出所有归档文件
    // 归档文件命名格式: diagnostics-20231120-143022.log
    // 按修改时间倒序排列(最新的在前)
    public suspend fun listArchives(): List<File> =
            withContext(Dispatchers.IO) {
                if (!logDir.exists()) return@withContext emptyList()
                // listFiles()获取目录下所有文件
                // filter{}过滤出归档文件(以diagnostics-开头)
                // sortedByDescending{}按最后修改时间倒序排列
                logDir.listFiles()
                        ?.filter { it.isFile && it.name.startsWith("diagnostics-") }
                        ?.sortedByDescending { it.lastModified() }
                        ?: emptyList()
            }

    // ensureLogFile: 确保日志文件存在
    // 如果目录或文件不存在,创建它们
    private fun ensureLogFile() {
        if (!logDir.exists()) {
            logDir.mkdirs() // 创建目录(包括父目录)
        }
        if (!logFile.exists()) {
            logFile.createNewFile() // 创建空文件
        }
    }

    // rotateIfNeeded: 日志轮转
    // 当日志文件大小超过MAX_LOG_BYTES时:
    // 1. 复制当前日志到归档文件
    // 2. 清空当前日志
    // 这样可以避免单个日志文件过大
    private fun rotateIfNeeded() {
        if (!logFile.exists()) return
        if (logFile.length() < MAX_LOG_BYTES) return // 未超过大小限制

        // 生成归档文件名: diagnostics-20231120-143022.log
        val archiveName = "diagnostics-${timestampFormatter.format(Date())}.log"
        val archiveFile = File(logDir, archiveName)

        // 复制到归档文件
        logFile.copyTo(archiveFile, overwrite = true)
        // 清空当前日志
        logFile.writeText("")
    }

    // formatEvent: 格式化诊断事件为字符串
    // 格式: timestamp code message | cause=ExceptionName:message
    // 示例: 1700460022000 connection_failed Connection refused | cause=IOException:Connection refused
    private fun formatEvent(event: DiagnosticsEvent): String {
        val builder = StringBuilder()
        builder.append(event.timestamp) // 时间戳
                .append(' ')
                .append(event.code) // 错误代码
                .append(' ')
                .append(event.message) // 错误消息

        // 如果有异常原因,追加异常信息
        // let{}是Kotlin的作用域函数,相当于if (throwable != null) {...}
        event.cause?.let { throwable ->
            builder.append(" | cause=")
                    .append(throwable::class.simpleName) // 异常类名
                    .append(':')
                    .append(throwable.message ?: "") // 异常消息
        }
        builder.append('\n') // 换行
        return builder.toString()
    }
}
