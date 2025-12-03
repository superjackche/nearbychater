package com.example.nearbychater

import android.app.Application
import com.example.nearbychater.core.logging.LogManager
import com.example.nearbychater.data.chat.ChatRepository
import com.example.nearbychater.data.nearby.NearbyChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// NearbyChaterApplication继承自Application
// Application是Android应用的全局单例，在应用启动时创建，整个应用生命周期只有一个实例
// 用途：初始化全局对象、配置、第三方SDK等
// 类似Python的__main__或C的main函数，但更长寿
class NearbyChaterApplication : Application() {
    // applicationScope是应用级别的协程作用域
    // SupervisorJob：子协程失败不会影响其他协程
    // Dispatchers.Default：默认调度器，适合CPU密集型任务
    // 这个scope和应用同生共死，不会因为Activity销毁而取消
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // lateinit表示"稍后初始化"
    // 必须在使用前赋值，否则会抛出UninitializedPropertyAccessException
    // 为什么用lateinit：这些对象需要context，只能在onCreate中创建
    lateinit var logManager: LogManager
        private set // private set表示只有内部能赋值，外部只读

    lateinit var nearbyChatService: NearbyChatService
        private set

    lateinit var chatRepository: ChatRepository
        private set

    // onCreate在应用启动时被调用，只会执行一次
    // 这里初始化全局单例对象
    override fun onCreate() {
        super.onCreate()
        // 初始化日志管理器
        logManager = LogManager(this)

        // 初始化近场通信服务
        // 传入应用级协程作用域，确保服务和应用同步
        nearbyChatService = NearbyChatService(this, applicationScope)

        // 初始化聊天数据仓库
        // ChatRepository依赖nearbyChatService和logManager
        // 这种依赖注入的方式让各个组件松散耦合
        chatRepository =
                ChatRepository(
                        context = this,
                        nearbyChatService = nearbyChatService,
                        logManager = logManager,
                        externalScope = applicationScope
                )
    }
}
