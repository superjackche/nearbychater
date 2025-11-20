package com.example.miniwechat.data.settings

import android.content.Context
import com.example.miniwechat.core.model.ConversationId
import com.example.miniwechat.data.storage.SettingsDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// SettingsRepository是仓库层，介于ViewModel和DAO之间
// Repository模式的作用：
// 1. 封装数据源访问（这里是SettingsDao）
// 2. 提供StateFlow让UI层观察数据变化
// 3. 在后台线程执行数据库操作，不阻塞UI
class SettingsRepository(
        context: Context,
        // ioDispatcher指定在哪个线程执行数据库操作
        // 默认是Dispatchers.IO，专门用于IO操作的线程池
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // settingsDao负责实际的数据库读写操作
    private val settingsDao = SettingsDao(context.applicationContext)

    // CoroutineScope管理协程的生命周期
    // SupervisorJob()表示子协程失败不会影响其他协程
    // 这样一个设置读取失败不会导致整个Repository崩溃
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // MutableStateFlow是私有的，只能在内部修改
    // StateFlow是公开的，外部只能读取和观察
    // 这种写法保证了数据的单向流动，类似单例模式的封装思想
    private val _diagnosticsEnabled = MutableStateFlow(true)
    val diagnosticsEnabled: StateFlow<Boolean> = _diagnosticsEnabled

    private val _backgroundServiceEnabled = MutableStateFlow(true)
    val backgroundServiceEnabled: StateFlow<Boolean> = _backgroundServiceEnabled

    // 会话别名：用于给会话起个自定义名称
    // Map<会话ID, 别名> 比如 "conv123" -> "我的好友"
    private val _conversationAliases = MutableStateFlow<Map<ConversationId, String>>(emptyMap())
    val conversationAliases: StateFlow<Map<ConversationId, String>> = _conversationAliases

    // companion object相当于Java的static
    // 类的所有实例共享这些常量
    private companion object {
        // 会话别名在数据库中存储的key前缀
        // 比如alias:conv123 = "我的好友"
        private const val ALIAS_PREFIX = "alias:"
    }

    // init块在对象创建时执行
    // 这里启动一个协程，异步加载所有设置项
    init {
        scope.launch {
            // 从数据库读取设置并更新StateFlow
            // 这样UI层订阅StateFlow后立即能获得初始值
            _diagnosticsEnabled.value = settingsDao.diagnosticsEnabled(true)
            _backgroundServiceEnabled.value = settingsDao.backgroundServiceEnabled(true)
            _conversationAliases.value = loadAliases()
        }
    }

    // suspend关键字表示这是一个挂起函数
    // 只能在协程或其他挂起函数中调用
    // 它不会阻塞线程，而是"挂起"协程，等待完成后恢复
    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        // withContext切换到IO线程执行数据库操作
        // 类似Python的async/await，但更轻量
        withContext(ioDispatcher) {
            // 先写入数据库
            settingsDao.setDiagnosticsEnabled(enabled)
            // 再更新StateFlow，触发UI刷新
            _diagnosticsEnabled.value = enabled
        }
    }

    // 设置后台服务开关
    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            settingsDao.setBackgroundServiceEnabled(enabled)
            _backgroundServiceEnabled.value = enabled
        }
    }

    // 设置会话别名
    suspend fun setConversationAlias(conversationId: ConversationId, title: String) {
        withContext(ioDispatcher) {
            // aliasKey()生成完整的key，比如"alias:conv123"
            settingsDao.setValue(aliasKey(conversationId), title)
            // + 操作符创建新map，添加新的键值对
            // Kotlin的Map是不可变的，不能直接修改，要创建新的
            _conversationAliases.value = _conversationAliases.value + (conversationId to title)
        }
    }

    // 清除会话别名
    suspend fun clearConversationAlias(conversationId: ConversationId) {
        withContext(ioDispatcher) {
            settingsDao.deleteKey(aliasKey(conversationId))
            // - 操作符创建新map，删除指定的key
            _conversationAliases.value = _conversationAliases.value - conversationId
        }
    }

    // 从数据库加载所有会话别名
    // private表示只在内部使用
    private suspend fun loadAliases(): Map<ConversationId, String> =
            withContext(ioDispatcher) {
                // readByPrefix读取所有以"alias:"开头的设置
                // mapKeys转换Map的key
                // entry.key.removePrefix去掉"alias:"前缀，得到纯粹的conversationId
                settingsDao.readByPrefix(ALIAS_PREFIX).mapKeys { entry ->
                    entry.key.removePrefix(ALIAS_PREFIX)
                }
            }

    // 生成会话别名的数据库key
    // $表示字符串模板，可以插入变量
    private fun aliasKey(conversationId: ConversationId): String = "$ALIAS_PREFIX$conversationId"
}