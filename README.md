# MiniWeChat - 基于Nearby API的局域网聊天应用

MiniWeChat 是一款基于 Android Nearby Connections API 构建的局域网即时通讯应用，支持设备发现、点对点消息传输和图片分享等功能。该应用无需互联网连接，可在局域网内实现设备间的即时通信。

## 功能特性

### 核心功能
- **局域网设备发现**：自动发现附近开启蓝牙和WiFi的设备
- **点对点消息传输**：支持文本消息和图片分享
- **离线消息队列**：设备离线时消息会被保存，上线后自动发送
- **消息状态追踪**：显示消息发送状态（排队中、发送中、已发送、已送达）
- **网状网络支持**：通过多跳转发实现更广范围的通信
- **诊断日志系统**：内置日志记录和查看功能，便于调试

### 用户界面
- **现代化 Material Design 3**：采用最新的 Android UI 设计语言
- **深色/浅色主题**：支持系统主题自动切换
- **会话列表管理**：支持会话重命名和删除
- **消息气泡界面**：类似主流聊天应用的交互体验
- **图片预览和保存**：支持发送和查看图片，可保存到相册
- **诊断气泡**：可开启的诊断信息悬浮显示

### 技术特性
- **Jetpack Compose**：声明式UI框架，支持响应式编程
- **Kotlin协程**：异步编程支持
- **DataStore缓存**：本地数据持久化存储
- **前台服务**：保持应用在后台运行
- **权限管理**：适配 Android 16+ 的权限要求

## 技术架构

### 架构层次

#### 1. UI层 (UI Layer)
- **Jetpack Compose**：声明式UI框架，支持响应式编程
- **ViewModel**：管理UI相关数据，生命周期感知
- **Material Design 3**：现代化设计语言

#### 2. 状态层 (State Layer)
- **ChatViewModel**：管理聊天状态、成员列表、消息数据
- **SettingsViewModel**：管理应用设置和诊断开关
- **StateFlow/SharedFlow**：响应式状态管理

#### 3. 数据层 (Data Layer)
- **ChatRepository**：协调缓存、Nearby事件和离线队列
- **SettingsRepository**：设置管理
- **LogManager**：诊断日志管理
- **ChatCacheDataSource**：DataStore缓存实现

#### 4. 平台层 (Platform Layer)
- **NearbyChatService**：Google Nearby Connections API封装
- **ChatForegroundService**：前台服务管理
- **AppDatabaseHelper**：SQLite数据库支持

### 核心数据模型

#### ChatMessage
- 消息ID、内容、时间戳
- 发送者ID、会话ID
- 消息状态（QUEUED, SENDING, SENT, DELIVERED等）
- 附件支持（图片等）
- 消息类型（TEXT, IMAGE, ACK）

#### MemberProfile
- 成员ID和昵称（本地/远程）
- 设备型号
- 在线状态和最后活跃时间

#### ConversationSnapshot
- 会话ID和成员列表
- 消息列表和会话属性
- 会话密钥和置顶状态

#### MeshEnvelope
- 网络传输信封，包含消息负载
- 路由信息（跳数、最大跳数）
- 数据包ID（用于去重）
- 参与者列表

## 网络架构

### Mesh 网络
应用采用网状网络架构，每个设备既是客户端也是路由器：
- **消息转发**：设备间可多跳转发消息
- **去重机制**：通过 packetId 防止消息重复处理
- **跳数限制**：默认最大4跳，防止无限循环
- **泛洪路由**：简单高效的广播机制

### 连接管理
- **自动发现**：自动搜索附近设备
- **连接维护**：保持稳定的点对点连接
- **状态同步**：实时更新成员在线状态

### 消息流程
1. **会话建立**：ViewModel请求ChatRepository启动mesh
2. **消息发送**：Composer创建消息→DataStore缓存→立即发送或排队
3. **消息接收**：解析MeshEnvelope→去重→存储→状态更新
4. **离线处理**：5秒间隔重试机制，用户可手动取消

## 权限说明

应用需要以下权限以实现完整功能：

```
INTERNET                     - 网络访问
ACCESS_FINE_LOCATION         - 位置权限（Nearby API 要求）
NEARBY_WIFI_DEVICES          - WiFi 设备发现（Android 16+）
BLUETOOTH                    - 蓝牙通信
BLUETOOTH_ADMIN              - 蓝牙管理
BLUETOOTH_SCAN               - 蓝牙扫描
BLUETOOTH_CONNECT            - 蓝牙连接
BLUETOOTH_ADVERTISE          - 蓝牙广播
FOREGROUND_SERVICE           - 前台服务
POST_NOTIFICATIONS           - 通知权限（Android 16+）
```

## 安装和运行

### 环境要求
- Android 8.0 (API 级别 26) 或更高版本
- 蓝牙和 WiFi 功能
- 至少 50MB 可用存储空间
- 支持 Android 16 的新特性

### 构建项目
```bash
# 克隆项目
git clone <repository-url>
cd miniwechat

# 构建 APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 构建发布版本
./gradlew assembleRelease
```

### 自动构建
本项目使用 GitHub Actions 进行自动构建：
- 每次推送到 main 分支或创建 Pull Request 时，会自动构建 Release 版本的 APK
- 构建产物会自动上传为构建工件

你可以在 [Actions](https://github.com/your-username/miniwechat/actions) 页面查看构建状态。

### 项目结构
```
app/src/main/java/com/example/miniwechat/
├── core/                    # 核心模型和日志管理
│   ├── logging/            # 日志管理
│   └── model/              # 数据模型定义
├── data/                    # 数据层
│   ├── chat/               # 聊天业务逻辑
│   ├── nearby/             # Nearby Connections API封装
│   ├── service/            # 前台服务
│   ├── settings/           # 设置管理
│   └── storage/            # 数据存储（SQLite + DataStore）
├── ui/                      # 用户界面
│   ├── state/              # ViewModel状态管理
│   └── theme/              # 主题和样式
├── util/                    # 工具类
├── MainActivity.kt          # 主入口
└── MiniwechatApplication.kt # 应用类
```

## 使用说明

### 基本操作
1. **启动应用**：首次启动需要授予相关权限
2. **设备发现**：确保附近设备开启蓝牙和 WiFi
3. **开始聊天**：点击发现的设备开始会话
4. **发送消息**：在聊天界面输入文本或选择图片发送
5. **查看状态**：消息状态显示发送进度

### 高级功能
- **后台运行**：前台服务保持应用在后台接收消息
- **会话管理**：支持会话重命名和删除
- **诊断模式**：设置中开启诊断气泡，显示网络状态
- **日志查看**：开发者日志功能，便于调试
- **图片分享**：支持选择相册图片发送
- **消息重试**：失败消息可手动重试或取消

## 开发和测试

### 代码质量
- 使用 Kotlin 作为主要开发语言
- 遵循 Android 官方开发规范
- 采用 MVVM 架构模式
- 使用 Kotlin协程处理异步操作
- 响应式编程（StateFlow/SharedFlow）

### 测试支持
- 包含 Instrumented 测试和单元测试
- 提供测试数据生成功能用于 UI 测试
- 支持日志文件旋转和清理测试

### 性能优化
- 懒加载列表提升滚动性能
- 图片压缩减少内存占用
- 数据库索引优化查询速度
- 消息去重机制防止重复处理
- LRU缓存策略优化内存使用

## 已知限制

1. **平台限制**：仅支持 Android 16 设备间通信
2. **网络范围**：受蓝牙/WiFi 覆盖范围限制
3. **电池消耗**：持续发现和连接可能增加耗电
4. **数据安全**：当前版本未实现端到端加密
5. **设备兼容性**：需要 Android 16.0 (API 36) 和支持 Nearby Connections API

## 未来改进方向

- 实现端到端消息加密
- 添加群聊功能
- 支持文件传输
- 增加语音消息功能
- 优化电池使用效率
- 添加消息撤回功能
- 支持多语言本地化
- 添加消息搜索功能
- 实现更智能的路由算法

## 许可证

本项目采用定制许可证，仅供学习和参考使用。

**重要提示**：未经版权所有者明确书面授权，任何人不得将本软件或其衍生品用于任何商业用途。
如需商业使用，请联系版权所有者获得正式授权。

## 贡献

欢迎提交 Issue 和 Pull Request 来改进项目。

### 开发环境
- Kotlin 2.0.21
- Android Gradle Plugin 8.13.1
- Jetpack Compose BOM 2024.09.00
- 仅支持 Android 16 (API 36)