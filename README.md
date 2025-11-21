# MiniWeChat - 基于Nearby API的局域网聊天应用

MiniWeChat 是一款基于 Android Nearby Connections API 构建的局域网即时通讯应用，支持设备发现、点对点消息传输和图片分享等功能。该应用无需互联网连接，可在局域网内实现设备间的即时通信。

## 功能特性

### 核心功能
- **局域网设备发现**：自动发现附近开启蓝牙和WiFi的设备
- **点对点消息传输**：支持文本消息和图片分享
- **离线消息存储**：设备离线时消息会被保存，上线后自动发送
- **消息状态追踪**：显示消息发送状态（排队中、发送中、已发送、已送达）
- **网状网络支持**：通过多跳转发实现更广范围的通信

### 用户界面
- **现代化 Material Design 3**：采用最新的 Android UI 设计语言
- **深色/浅色主题**：支持系统主题自动切换
- **会话列表管理**：支持会话置顶、删除和重命名
- **消息气泡界面**：类似主流聊天应用的交互体验
- **图片预览和保存**：支持发送和查看图片，可保存到相册

### 技术特性
- **后台服务支持**：通过前台服务保持应用在后台运行
- **SQLite 数据存储**：本地持久化存储消息、会话和设置
- **诊断日志系统**：内置日志记录和查看功能，便于调试
- **权限管理**：适配 Android 13+ 的权限要求

## 技术架构

### 核心组件

#### 1. 应用层 (UI Layer)
- **Jetpack Compose**：声明式 UI 框架
- **ViewModel**：管理 UI 相关数据
- **Navigation Component**：页面导航

#### 2. 业务逻辑层 (Domain Layer)
- **ChatRepository**：聊天业务逻辑处理
- **SettingsRepository**：设置管理
- **NearbyChatService**：Nearby Connections API 封装

#### 3. 数据层 (Data Layer)
- **SQLite Database**：本地数据存储
- **ChatDao/SettingsDao**：数据访问对象
- **数据模型**：ChatMessage、MemberProfile、Conversation 等

#### 4. 核心服务
- **Nearby Connections API**：Google 提供的近场通信框架
- **Foreground Service**：前台服务保持应用活跃

### 主要数据模型

#### ChatMessage
- 消息 ID、内容、时间戳
- 发送者 ID、会话 ID
- 消息状态和类型
- 附件支持（图片等）

#### MemberProfile
- 成员 ID 和昵称
- 设备型号
- 在线状态和最后活跃时间

#### Conversation
- 会话 ID 和成员列表
- 消息列表和会话属性

## 网络架构

### Mesh 网络
应用采用网状网络架构，每个设备既是客户端也是路由器：
- **消息转发**：设备间可多跳转发消息
- **去重机制**：通过 packetId 防止消息重复处理
- **跳数限制**：默认最大 4 跳，防止无限循环

### 连接管理
- **自动发现**：自动搜索附近设备
- **连接维护**：保持稳定的点对点连接
- **状态同步**：实时更新成员在线状态

## 权限说明

应用需要以下权限以实现完整功能：

```
INTERNET                     - 网络访问
ACCESS_FINE_LOCATION         - 位置权限（Nearby API 要求）
NEARBY_WIFI_DEVICES          - WiFi 设备发现（Android 13+）
BLUETOOTH                    - 蓝牙通信
BLUETOOTH_ADMIN              - 蓝牙管理
BLUETOOTH_SCAN               - 蓝牙扫描
BLUETOOTH_CONNECT            - 蓝牙连接
BLUETOOTH_ADVERTISE          - 蓝牙广播
FOREGROUND_SERVICE           - 前台服务
POST_NOTIFICATIONS           - 通知权限（Android 13+）
```

## 安装和运行

### 环境要求
- Android 8.0 (API 级别 26) 或更高版本
- 蓝牙和 WiFi 功能
- 至少 50MB 可用存储空间

### 构建项目
```bash
# 克隆项目
git clone <repository-url>
cd miniwechat

# 构建 APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 自动构建
本项目使用 GitHub Actions 进行自动构建：
- 每次推送到 main 分支或创建 Pull Request 时，会自动构建 Release 版本的 APK
- 创建 Release 时，会自动构建签名的 APK 并作为 Release Asset 附加

你可以在 [Actions](https://github.com/your-username/miniwechat/actions) 页面查看构建状态，
在 [Releases](https://github.com/your-username/miniwechat/releases) 页面下载最新版本。

### 项目结构
```
app/
├── src/main/java/com/example/miniwechat/
│   ├── core/              # 核心模型和日志管理
│   ├── data/              # 数据层（存储、服务、设置）
│   ├── ui/                # 用户界面
│   │   ├── state/         # ViewModel
│   │   └── theme/         # 主题和颜色
│   ├── util/              # 工具类
│   ├── MainActivity.kt    # 主入口
│   └── MiniwechatApplication.kt  # 应用类
└── src/main/res/          # 资源文件
```

## 使用说明

### 基本操作
1. **启动应用**：首次启动需要授予相关权限
2. **设备发现**：确保附近设备开启蓝牙和 WiFi
3. **开始聊天**：点击发现的设备开始会话
4. **发送消息**：在聊天界面输入文本或选择图片发送

### 高级功能
- **后台运行**：开启"保持后台运行"可在应用最小化时接收消息
- **会话管理**：长按会话可进行置顶或删除操作
- **诊断模式**：在设置中开启诊断气泡可查看连接状态

## 开发和测试

### 代码质量
- 使用 Kotlin 作为主要开发语言
- 遵循 Android 官方开发规范
- 采用 MVVM 架构模式

### 测试支持
- 包含 Instrumented 测试和单元测试
- 提供测试数据生成功能用于 UI 测试

### 性能优化
- 懒加载列表提升滚动性能
- 图片压缩减少内存占用
- 数据库索引优化查询速度

## 已知限制

1. **平台限制**：仅支持 Android 设备间通信
2. **网络范围**：受蓝牙/WiFi 覆盖范围限制
3. **电池消耗**：持续发现和连接可能增加耗电
4. **数据安全**：当前版本未实现端到端加密

## 未来改进方向

- 实现端到端消息加密
- 添加群聊功能
- 支持文件传输
- 增加语音消息功能
- 优化电池使用效率
- 添加消息撤回功能

## 许可证

本项目采用定制许可证，仅供学习和参考使用。

**重要提示**：未经版权所有者明确书面授权，任何人不得将本软件或其衍生品用于任何商业用途。
如需商业使用，请联系版权所有者获得正式授权。

## 贡献

欢迎提交 Issue 和 Pull Request 来改进项目。