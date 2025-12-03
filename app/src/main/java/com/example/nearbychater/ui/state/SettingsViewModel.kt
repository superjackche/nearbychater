package com.example.nearbychat.ui.state

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nearbychat.core.logging.LogManager
import com.example.nearbychat.data.settings.SettingsRepository
import com.example.nearbychat.core.model.ChatMessage
import com.example.nearbychat.core.model.MemberProfile
import com.example.nearbychat.core.model.MessageStatus
import com.example.nearbychat.data.storage.ChatDao
import com.example.nearbychat.data.chat.ChatRepository
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// SettingsViewModel: 设置界面的ViewModel
// AndroidViewModel: 继承自ViewModel，但可以访问Application Context
// 与普通ViewModel的区别：
// - ViewModel: 不能持有Activity/Fragment引用
// - AndroidViewModel: 可以持有Application引用（不会内存泄漏）
public class SettingsViewModel(
    application: Application,
    private val chatRepository: ChatRepository? = null
) : AndroidViewModel(application) {
    // settingsRepository: 设置数据仓库
    private val settingsRepository = SettingsRepository(application)

    // logManager: 日志管理器
    private val logManager = LogManager(application)

    // samplePhrases: 示例短语列表
    // 用于生成测试数据，模拟真实的聊天内容
    // 内容都是学生之间的日常交流
    private val samplePhrases =
            listOf(
                    "课程作业：本周阅读笔记已完成 📚",
                    "明天的研讨会记得带资料 ✍️",
                    "图书馆自习 19:00 见？",
                    "老师刚发的实验要求我转给你了",
                    "实验报告我已经提交，请查收",
                    "期末项目需要再讨论一下 😊",
                    "下周开始准备答辩 PPT 吧",
                    "课堂练习有不懂的可以问我",
                    "实验室预约在周四下午",
                    "记得上传课堂笔记到群里"
            )

    // diagnosticsEnabled: 诊断模式开关状态
    // stateIn()把Repository的Flow转换成StateFlow
    // 这样UI层可以直接观察这个值的变化
    public val diagnosticsEnabled: StateFlow<Boolean> =
            settingsRepository.diagnosticsEnabled.stateIn(
                    viewModelScope, // ViewModel的协程作用域
                    SharingStarted.Eagerly, // 立即开始收集
                    true // 初始值
            )

    // backgroundServiceEnabled: 后台服务开关状态
    public val backgroundServiceEnabled: StateFlow<Boolean> =
            settingsRepository.backgroundServiceEnabled.stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    true
            )

    // _logLines: 内部可变的日志行列表
    // 前缀_表示私有，提供对外的只读版本logLines
    private val _logLines = MutableStateFlow<List<String>>(emptyList())

    // logLines: 公开的只读日志行列表
    public val logLines: StateFlow<List<String>> = _logLines
    
    // _isLoading: 内部可变的加载状态
    private val _isLoading = MutableStateFlow(false)
    
    // isLoading: 公开的只读加载状态
    public val isLoading: StateFlow<Boolean> = _isLoading

    // init块在ViewModel创建时执行
    init {
        refreshLogs() // 加载日志
    }

    // setDiagnosticsEnabled: 设置诊断模式开关
    // viewModelScope.launch{}在ViewModel的协程作用域中启动协程
    // 当ViewModel销毁时，会自动取消所有协程
    public fun setDiagnosticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDiagnosticsEnabled(enabled) }
    }

    // setBackgroundServiceEnabled: 设置后台服务开关
    public fun setBackgroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundServiceEnabled(enabled) }
    }

    // refreshLogs: 刷新日志列表
    // Dispatchers.IO指定在IO线程执行（文件读取）
    public fun refreshLogs() {
        viewModelScope.launch(Dispatchers.IO) { _logLines.value = logManager.readLogs() }
    }

    // clearLogs: 清空日志
    public fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            logManager.clearLogs()
            _logLines.value = emptyList()
        }
    }

    // generateSamples: 生成测试数据
    // 这个功能用于演示和测试，会创建100个模拟会话和消息
    // 在开发阶段非常有用，可以快速填充界面测试UI
    public fun generateSamples() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val dao = ChatDao(getApplication())
                val localId = deviceId() // 获取本机设备ID
                val random = Random(System.currentTimeMillis())

                // 先删除旧的测试数据
                removeSampleData(dao, localId)

                // 创建100个测试会话
                for (i in SAMPLE_RANGE) {
                    // 生成远程成员ID: sample_member_01, sample_member_02, ...
                    val remoteId = sampleMemberId(i)

                    // 生成会话ID
                    val conversationId = sampleConversationId(localId, remoteId)

                    // 创建成员档案
                    val profile = 
                            MemberProfile(
                                    memberId = remoteId,
                                    localNickname = "测试${formatIndex(i)}", // 测试01, 测试02, ...
                                    deviceModel = "TestDevice"
                            )
                    dao.upsertMember(profile)

                    // 确保会话存在
                    dao.ensureConversation(conversationId, remoteId, setOf(localId, remoteId))

                    // 为每个会话生成几十条消息，每条消息间隔1分钟
                    val messageCount = random.nextInt(21) + 30
                    val baseTime = System.currentTimeMillis() - (messageCount * 60 * 1000L) // 从过去开始
                    
                    for (j in 1..messageCount) {
                        // 偶数索引是对方发的，奇数索引是自己发的
                        val sender = if (j % 2 == 0) remoteId else localId
                        val message = 
                                ChatMessage(
                                        conversationId = conversationId,
                                        senderId = sender,
                                        content = "${samplePhrases[j % samplePhrases.size]} (${j})",
                                        timestamp = baseTime + j * 60 * 1000L, // 每条消息间隔1分钟
                                        status = MessageStatus.SENT
                                )
                        dao.insertOrUpdateMessage(message)
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // deleteSamples: 删除测试数据
    // 清理generateSamples()生成的所有测试会话和消息
    public fun deleteSamples() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val dao = ChatDao(getApplication())
                val localId = deviceId()
                removeSampleData(dao, localId)
                // 刷新会话列表，确保用户回到主页面能看到更新
                chatRepository?.refresh()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // deviceId: 获取设备ID
    // 优先使用ANDROID_ID，如果获取失败则生成随机UUID
    private fun deviceId(): String {
        val androidId =
                Settings.Secure.getString(
                        getApplication<Application>().contentResolver,
                        Settings.Secure.ANDROID_ID
                )
        return androidId ?: UUID.randomUUID().toString()
    }

    // sampleMemberId: 生成测试成员ID
    // 格式: sample_member_01, sample_member_02, ...
    private fun sampleMemberId(index: Int): String = "sample_member_${formatIndex(index)}"

    // sampleConversationId: 生成测试会话ID
    // 会话ID由两个成员ID排序后用-连接
    // 这样保证A-B和B-A得到相同的ID
    private fun sampleConversationId(localId: String, remoteId: String): String =
            listOf(localId, remoteId).sorted().joinToString("-")

    // formatIndex: 格式化索引为两位数字
    // 1 -> "01", 20 -> "20"
    private fun formatIndex(index: Int): String = String.format("%02d", index)

    // 生成群聊测试数据
    public fun generateGroupSamples() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val dao = ChatDao(getApplication())
                val localId = deviceId() // 获取本机设备ID
                val random = Random(System.currentTimeMillis())

                // 生成50个测试群聊
                for (i in 1..50) {
                    // 生成3-20个随机用户
                    val memberCount = random.nextInt(18) + 3
                    val memberIds = mutableSetOf(localId)
                    
                    // 生成随机成员ID
                    for (j in 0 until memberCount - 1) {
                        memberIds.add(sampleMemberId(i * 100 + j))
                    }
                    
                    // 创建群聊会话
                    val conversationId = "sample_group_${i}"
                    val conversationKey = memberIds.sorted().joinToString(":")
                    
                    // 确保会话存在
                    dao.ensureConversation(conversationId, conversationKey, memberIds)
                    
                    // 为每个成员创建档案
                    for (memberId in memberIds) {
                        if (memberId != localId) {
                            val profile = MemberProfile(
                                    memberId = memberId,
                                    localNickname = "群成员${formatIndex(i * 100 + memberIds.indexOf(memberId))}",
                                    deviceModel = "TestDevice"
                            )
                            dao.upsertMember(profile)
                        }
                    }
                    
                    // 生成10-30条测试消息，每条消息间隔1分钟
                    val messageCount = random.nextInt(21) + 10
                    val baseTime = System.currentTimeMillis() - (messageCount * 60 * 1000L) // 从过去开始
                    val phrases = samplePhrases.shuffled(random)
                    
                    for (j in 1..messageCount) {
                        // 随机选择发送者
                        val senderId = memberIds.random()
                        val content = if (random.nextBoolean()) {
                            // 文字消息
                            "${phrases[j % phrases.size]} (${j})"
                        } else {
                            // 表情消息
                            listOf("😊", "😂", "👍", "❤️", "🎉", "🤔", "😢", "😮").random()
                        }
                        
                        val message = ChatMessage(
                                conversationId = conversationId,
                                senderId = senderId,
                                content = content,
                                timestamp = baseTime + j * 60 * 1000L, // 每条消息间隔1分钟
                                status = MessageStatus.SENT
                        )
                        dao.insertOrUpdateMessage(message)
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 生成群聊会话ID
    private fun generateGroupConversationId(memberIds: Set<String>): String {
        return "sample_group_" + memberIds.sorted().joinToString("_")
    }
    
    // 删除群聊测试数据
    private fun removeGroupSampleData(dao: ChatDao, localId: String) {
        // 获取所有会话
        val conversations = dao.readConversations()
        
        // 删除所有群聊测试会话
        conversations.forEach { (conversationId, snapshot) ->
            if (conversationId.startsWith("sample_group_")) {
                dao.deleteConversation(conversationId)
                
                // 删除群聊成员（如果是测试成员）
                snapshot.memberIds.forEach { memberId ->
                    if (memberId.startsWith("sample_member_")) {
                        dao.deleteMember(memberId)
                    }
                }
            }
        }
    }
    
    // 同时生成单聊和群聊测试数据
    public fun generateAllSamples() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                generateSamples() // 生成单聊
                generateGroupSamples() // 生成群聊
                // 刷新会话列表，确保用户回到主页面能看到更新
                chatRepository?.refresh()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 增强removeSampleData方法，支持删除群聊测试数据
    private fun removeSampleData(dao: ChatDao, localId: String) {
        // 删除单聊测试数据
        for (i in SAMPLE_RANGE) {
            val remoteId = sampleMemberId(i)
            val conversationId = sampleConversationId(localId, remoteId)
            dao.deleteConversation(conversationId) // 删除会话（包括消息）
            dao.deleteMember(remoteId) // 删除成员
        }
        
        // 删除群聊测试数据
        removeGroupSampleData(dao, localId)
    }
    
    // companion object: 伴生对象
    // 类似Java的static成员，属于类而不是实例
    // private表示只在这个类内部可见
    private companion object {
        // SAMPLE_RANGE: 测试数据范围 1..100
        // 表示生成100个测试会话
        private val SAMPLE_RANGE = 1..100
    }
}