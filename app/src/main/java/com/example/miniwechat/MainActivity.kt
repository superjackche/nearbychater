package com.example.miniwechat

// Android 16 最新原生动画支持
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.miniwechat.ui.ChatScreen
import com.example.miniwechat.ui.ConversationListScreen
import com.example.miniwechat.ui.LogsScreen
import com.example.miniwechat.ui.SettingsScreen
import com.example.miniwechat.ui.state.ChatViewModel
import com.example.miniwechat.ui.state.SettingsViewModel
import com.example.miniwechat.ui.theme.MiniwechatTheme
import com.example.miniwechat.data.service.ChatForegroundService

// MainActivity是应用的入口Activity
// ComponentActivity是Jetpack Compose推荐的基类，提供Compose支持
// Activity的生命周期：onCreate -> onStart -> onResume -> onPause -> onStop -> onDestroy
class MainActivity : ComponentActivity() {
    private lateinit var backCallback: OnBackInvokedCallback
    
    // onCreate在Activity创建时调用
    // savedInstanceState保存了Activity销毁前的状态，比如屏幕旋转后可以恢复数据
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge让应用内容延伸到状态栏和导航栏下方，实现沉浸式体验
        enableEdgeToEdge()
        
        // 动态设置刷新率为120Hz（如果设备支持）
        setHighRefreshRate()
        
        // 注册预测返回动画回调
        registerBackInvokedCallback()
        
        // setContent设置UI内容，使用Compose声明式UI
        // MiniwechatTheme提供主题配置（颜色、字体等）
        // MiniwechatApp是根Composable，整个应用的UI入口
        setContent { MiniwechatTheme { MiniwechatApp() } }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消注册预测返回动画回调
        unregisterBackInvokedCallback()
    }
    
    // 注册预测返回动画回调
    private fun registerBackInvokedCallback() {
        // 创建OnBackInvokedCallback，不传递优先级参数
        backCallback = object : OnBackInvokedCallback {
            override fun onBackInvoked() {
                // 处理返回事件，这里由Compose导航组件处理
                Log.d("BackInvoked", "onBackInvoked called")
            }
        }
        
        // 注册回调时设置优先级
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            backCallback
        )
    }
    
    // 取消注册预测返回动画回调
    private fun unregisterBackInvokedCallback() {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
    }
    
    // 动态设置高刷新率
    private fun setHighRefreshRate() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays
        
        for (display in displays) {
            val supportedModes = display.supportedModes
            if (supportedModes.isNotEmpty()) {
                // 找到最高刷新率的显示模式
                var highestRefreshRateMode = supportedModes[0]
                for (mode in supportedModes) {
                    if (mode.refreshRate > highestRefreshRateMode.refreshRate) {
                        highestRefreshRateMode = mode
                    }
                }
                
                // 优先选择120Hz或更高的刷新率
                var targetMode = highestRefreshRateMode
                for (mode in supportedModes) {
                    if (mode.refreshRate >= 120.0f) {
                        targetMode = mode
                        break
                    }
                }
                
                // 打印刷新率信息
                val supportedRates = StringBuilder()
                for (mode in supportedModes) {
                    supportedRates.append(mode.refreshRate).append("Hz, ")
                }
                if (supportedRates.isNotEmpty()) {
                    supportedRates.setLength(supportedRates.length - 2)
                }
                
                Log.d("RefreshRate", "Display ${display.displayId} supports refresh rates: $supportedRates")
                Log.d("RefreshRate", "Selected refresh rate: ${targetMode.refreshRate}Hz")
            }
        }
    }
}

// MiniwechatApp是应用的根Composable
// 负责权限管理、导航、服务启动等核心逻辑
@Composable
fun MiniwechatApp() {
    // viewModel()获取ViewModel实例
    // ViewModel会自动绑定到Activity生命周期，屏幕旋转不会销毁
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    // rememberNavController创建导航控制器
    // 用于在不同界面之间跳转
    val navController = rememberNavController()

    // LocalContext.current获取当前的Context
    // Context是Android的上下文，可以访问系统服务、资源等
    val context = LocalContext.current

    // remember保存数据，Recompose时不会重新计算
    // 类似C的static变量，但只在这个函数作用域内有效
    val permissions = remember { requiredPermissions() }

    // mutableStateOf创建可观察的状态
    // 当hasPermissions改变时，使用它的UI会自动重绘
    // by关键字是属性委托，让我们可以直接用hasPermissions赋值
    var hasPermissions by remember {
        mutableStateOf(
                // all检查是否所有权限都已授予
                permissions.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                }
        )
    }

    // rememberLauncherForActivityResult创建一个启动器
    // 用于请求权限，结果会在回调中返回
    // 这是Android新的权限请求方式，替代了旧的onRequestPermissionsResult
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                // result是Map<权限, 是否授予>
                // 检查是否所有权限都授予了
                val granted = result.entries.all { it.value }
                hasPermissions =
                        granted ||
                                permissions.all { permission ->
                                    ContextCompat.checkSelfPermission(context, permission) ==
                                            PackageManager.PERMISSION_GRANTED
                                }
            }

    // collectAsStateWithLifecycle订阅StateFlow并转换成Compose的State
    // 当backgroundServiceEnabled变化时，这个值会自动更新
    val backgroundServiceEnabled by
            settingsViewModel.backgroundServiceEnabled.collectAsStateWithLifecycle()

    // LaunchedEffect在Compose进入组合时执行副作用
    // permissions, hasPermissions, backgroundServiceEnabled是key
    // 当这些值变化时，LaunchedEffect会重新执行
    LaunchedEffect(permissions, hasPermissions, backgroundServiceEnabled) {
        if (!hasPermissions && permissions.isNotEmpty()) {
            // 如果没有权限，请求权限
            permissionLauncher.launch(permissions)
        } else if (hasPermissions) {
            // 如果有权限，根据设置决定是否启动前台服务
            handleForegroundService(context, backgroundServiceEnabled)
        }
    }

    // 连接性警告对话框的显示状态
    var showConnectivityWarning by remember { mutableStateOf(false) }

    // LocalLifecycleOwner.current获取生命周期拥有者
    // 用于监听Activity的生命周期事件
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // 监听生命周期，当Activity回到前台时检查蓝牙和WiFi
    LaunchedEffect(lifecycleOwner, hasPermissions) {
        // LifecycleEventObserver观察生命周期事件
        val observer = LifecycleEventObserver { _, event ->
            // ON_RESUME表示Activity回到前台（用户可见可交互）
            if (event == Lifecycle.Event.ON_RESUME && hasPermissions) {
                // 检查蓝牙和WiFi是否开启
                showConnectivityWarning = !checkConnectivity(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // 如果需要显示连接性警告
    if (showConnectivityWarning) {
        // 蓝牙开启请求的启动器
        val enableBluetoothLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult(),
                        onResult = { /* 结果在observer中自动重新检查 */}
                )

        ConnectivityWarningDialog(
                onDismiss = { showConnectivityWarning = false },
                onEnableBluetooth = {
                    // 打开系统的蓝牙开启界面
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onGoToSettings = {
                    // 打开系统的无线设置界面
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                },
                isBluetoothMissing = !checkBluetooth(context),
                isWifiMissing = !checkWifi(context)
        )
    }

    // 如果有权限或不需要权限，显示主界面
    if (hasPermissions || permissions.isEmpty()) {
        // NavHost是导航的容器
        // 定义了所有可导航的界面（路由）
        NavHost(
                navController = navController,
                startDestination = "home", // 启动时显示的界面
                modifier = Modifier.fillMaxSize(),
                // 定义界面切换动画
            // enterTransition: 新界面进入时的动画
            // exitTransition: 当前界面退出时的动画
            // popEnterTransition: 返回时，目标界面进入的动画
            // popExitTransition: 返回时，当前界面退出的动画
            enterTransition = {
                // 从右侧滑入，优化动画性能，适合120Hz屏幕
                slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing) // 200ms动画，使用更流畅的缓动函数
                )
            },
            exitTransition = {
                // 向左滑出，优化动画性能，适合120Hz屏幕
                slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                // 返回时从左侧滑入，优化动画性能，适合120Hz屏幕
                slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                // 返回时向右滑出，优化动画性能，适合120Hz屏幕
                slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            }
        ) {
            // composable定义一个路由
            // "home"是会话列表界面
            composable("home") {
                ConversationListScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = chatViewModel,
                        onConversationSelected = { conversationId ->
                            // 点击会话后导航到聊天界面
                            navController.navigate("chat/$conversationId") {
                                // launchSingleTop避免重复打开同一个界面
                                launchSingleTop = true
                            }
                        },
                        onOpenSettings = { navController.navigate("settings") },
                        onOpenLogs = { navController.navigate("logs") }
                )
            }
            // "chat/{conversationId}"是动态路由
            // {conversationId}是路径参数，可以传递不同的会话ID
            composable(
                    route = "chat/{conversationId}",
                    // navArgument定义参数类型
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { entry ->
                // entry.arguments包含路由参数
                ChatScreen(
                        modifier = Modifier.fillMaxSize(),
                        conversationId = entry.arguments?.getString("conversationId"),
                        onBack = { navController.popBackStack() }, // 返回上一界面
                        onOpenSettings = { navController.navigate("settings") },
                        onOpenLogs = { navController.navigate("logs") },
                        viewModel = chatViewModel
                )
            }
            // 设置界面
            composable("settings") {
                SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = settingsViewModel,
                        selfMemberId = chatViewModel.selfMemberId
                )
            }
            // 日志界面
            composable("logs") {
                LogsScreen(modifier = Modifier.fillMaxSize(), viewModel = settingsViewModel)
            }
        }
    } else {
        // 如果没有权限，显示权限请求界面
        PermissionRequiredScreen(
                onRequestAgain = {
                    if (permissions.isNotEmpty()) {
                        permissionLauncher.launch(permissions)
                    }
                }
        )
    }
}

// 权限请求界面
// 显示提示文字和重新授权按钮
@Composable
private fun PermissionRequiredScreen(onRequestAgain: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "需要附近设备、蓝牙和位置信息权限才能互相发现。")
            Button(onClick = onRequestAgain, modifier = Modifier.padding(top = 16.dp)) {
                Text(text = "重新授权")
            }
        }
    }
}

// 获取所需的权限列表
// 不同Android版本需要的权限不同
private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf<String>()

    // 位置权限：Nearby Connections需要
    // 这是因为蓝牙扫描可以推断用户位置，Android要求申请位置权限
    permissions += Manifest.permission.ACCESS_FINE_LOCATION

    // Android 13+ (TIRAMISU)需要单独的WiFi设备权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    }

    // Android 12+ (S)重新设计了蓝牙权限
    // 分成了扫描、连接、广播三个细粒度权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
    } else {
        // Android 12以下使用旧的蓝牙权限
        permissions += Manifest.permission.BLUETOOTH
        permissions += Manifest.permission.BLUETOOTH_ADMIN
    }

    // Android 13+需要通知权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }

    // distinct()去重，toTypedArray()转换成数组
    return permissions.distinct().toTypedArray()
}

// 检查连接性：蓝牙和WiFi都要开启
private fun checkConnectivity(context: Context): Boolean {
    return checkBluetooth(context) && checkWifi(context)
}

// 检查蓝牙是否开启
private fun checkBluetooth(context: Context): Boolean {
    // getSystemService获取系统服务
    // as?是安全类型转换，失败返回null而不是抛异常
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter
    // ?.是安全调用，如果bluetoothAdapter为null，整个表达式返回null
    // == true确保返回Boolean而不是Boolean?
    return bluetoothAdapter?.isEnabled == true
}

// 检查WiFi是否开启
private fun checkWifi(context: Context): Boolean {
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    return wifiManager?.isWifiEnabled == true
}

// 处理前台服务
private fun handleForegroundService(context: Context, enabled: Boolean) {
    val serviceIntent = Intent(context, ChatForegroundService::class.java)
    if (enabled) {
        // 启动前台服务
        ContextCompat.startForegroundService(context, serviceIntent)
    } else {
        // 停止前台服务
        context.stopService(serviceIntent)
    }
}

// 连接性警告对话框
// 提示用户开启蓝牙和/或WiFi
@Composable
private fun ConnectivityWarningDialog(
        onDismiss: () -> Unit,
        onEnableBluetooth: () -> Unit,
        onGoToSettings: () -> Unit,
        isBluetoothMissing: Boolean,
        isWifiMissing: Boolean
) {
    AlertDialog(
            onDismissRequest = onDismiss, // 点击对话框外部或返回键时调用
            title = { Text("需要开启连接功能") },
            text = {
                // 根据缺失的连接类型生成提示文字
                val missing = mutableListOf<String>()
                if (isBluetoothMissing) missing.add("蓝牙")
                if (isWifiMissing) missing.add("Wi-Fi")
                // joinToString用指定分隔符连接字符串
                Text("为了发现附近的设备，请开启: ${missing.joinToString(" 和 ")}。")
            },
            confirmButton = {
                // 如果只缺蓝牙，直接提供开启蓝牙按钮
                // 否则跳转到设置页面（因为WiFi不能直接编程开启）
                if (isBluetoothMissing && !isWifiMissing) {
                    TextButton(
                            onClick = {
                                onEnableBluetooth()
                                onDismiss()
                            }
                    ) { Text("开启蓝牙") }
                } else {
                    TextButton(
                            onClick = {
                                onGoToSettings()
                                onDismiss()
                            }
                    ) { Text("去设置") }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}