package com.example.nearbychat.data.nearby

import android.content.Context
import android.os.Build
import com.example.nearbychat.core.model.ConversationId
import com.example.nearbychat.core.model.DiagnosticsEvent
import com.example.nearbychat.core.model.MemberId
import com.example.nearbychat.core.model.MeshEnvelope
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// SERVICE_ID: Nearby Connections服务标识符
// 相同的SERVICE_ID的设备才能相互发现和连接
private const val SERVICE_ID = "com.example.nearbychat.MESH"

// NearbyChatService是Google Nearby Connections API的封装
// Nearby Connections是Google提供的P2P近场通信框架
// 可以通过蓝牙、WiFi Direct、WiFi热点等方式连接附近设备
// 核心功能：
// 1. Advertising（广播）：告诉附近设备自己的存在
// 2. Discovery（发现）：搜索附近的其他设备
// 3. Connection（连接）：建立点对点连接
// 4. Payload Transfer（数据传输）：发送和接收消息
// 这个类还实现了层状网络（Mesh Network）和洪泛路由（Flood Routing）
public class NearbyChatService(
        context: Context,
        private val externalScope: CoroutineScope,
        // json用于序列化/反序列化消息，ignoreUnknownKeys允许忽略未知字段
        private val json: Json = Json { ignoreUnknownKeys = true }
) {
        // connectionsClient: Nearby Connections API的客户端
        // 类似网络套接字(Socket)，用于执行所有Nearby操作
        private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

        // eventFlow: 事件流，用于发送事件到上层（ChatRepository）
        // extraBufferCapacity=64表示最多缓存64个未消费的事件
        private val eventFlow = MutableSharedFlow<NearbyEvent>(extraBufferCapacity = 64)

        // connectedEndpoints: 已连接的端点列表
        // Map<成员ID, 端点元数据>
        // 存储当前直接连接的所有设备
        private val connectedEndpoints = ConcurrentHashMap<MemberId, EndpointMetadata>()

        // pendingRemoteInfo: 待处理的远程信息
        // 连接成功前用于临时存储对方的信息
        private val pendingRemoteInfo = ConcurrentHashMap<String, EndpointInfo?>()

        // seenPacketIds: 已处理的数据包ID集合
        // 用于防止重复处理同一消息（网状网络中消息会被多次转发）
        // LRU策略，size>1000时自动删除最旧的记录
        private val seenPacketIds =
                Collections.synchronizedSet(
                        Collections.newSetFromMap(
                                object : LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
                                        override fun removeEldestEntry(
                                                eldest: MutableMap.MutableEntry<String, Boolean>?
                                        ): Boolean {
                                                return size > 1000
                                        }
                                }
                        )
                )

        // localEndpointInfo: 本地端点信息（设备ID和昵称）
        private var localEndpointInfo: EndpointInfo? = null

        // isRunning: 服务是否正在运行
        private var isRunning: Boolean = false

        // payloadCallback: 负载回调
        // 当收到数据或传输状态变化时被调用
        private val payloadCallback =
                object : PayloadCallback() {
                        // onPayloadReceived: 收到数据时调用
                        override fun onPayloadReceived(endpointId: String, payload: Payload) {
                                // 从 Payload中提取字节数组
                                val bytes = payload.asBytes() ?: return
                                // 转换成UTF-8字符串
                                val body = bytes.toString(StandardCharsets.UTF_8)
                                // runCatching{}捕获异常，类似Python的try-except
                                // 尝试反序列化JSON为MeshEnvelope对象
                                runCatching {
                                        json.decodeFromString(MeshEnvelope.serializer(), body)
                                }
                                        .onSuccess { envelope ->
                                                // 成功解析
                                                // seenPacketIds.add()返回true表示首次看到这个数据包
                                                if (seenPacketIds.add(envelope.packetId)) {
                                                        externalScope.launch {
                                                                // 发送消息接收事件
                                                                eventFlow.emit(
                                                                        NearbyEvent.MessageReceived(
                                                                                envelope
                                                                        )
                                                                )
                                                                // 网状网络关键：转发消息给其他连接的设备
                                                                // hopCount+1记录跳数，防止无限循环
                                                                // excludeEndpointId避免把消息发回给发送者
                                                                forwardEnvelope(
                                                                        envelope.copy(
                                                                                hopCount =
                                                                                        envelope.hopCount +
                                                                                                1
                                                                        ),
                                                                        excludeEndpointId =
                                                                                endpointId
                                                                )
                                                        }
                                                }
                                        }
                                        .onFailure { throwable ->
                                                // 解析失败，发送错误事件
                                                externalScope.launch {
                                                        eventFlow.emit(
                                                                NearbyEvent.Error(
                                                                        DiagnosticsEvent(
                                                                                code =
                                                                                        "nearby_decode_failure",
                                                                                message =
                                                                                        "Failed to decode payload from $endpointId",
                                                                                cause = throwable
                                                                        )
                                                                )
                                                        )
                                                }
                                        }
                        }

                        // onPayloadTransferUpdate: 数据传输状态更新时调用
                        override fun onPayloadTransferUpdate(
                                endpointId: String,
                                update: PayloadTransferUpdate
                        ) {
                                // 如果传输失败，发送错误事件
                                if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                                        externalScope.launch {
                                                eventFlow.emit(
                                                        NearbyEvent.Error(
                                                                DiagnosticsEvent(
                                                                        code =
                                                                                "payload_transfer_failure",
                                                                        message =
                                                                                "Payload transfer failed for $endpointId: ${update.status}"
                                                                )
                                                        )
                                                )
                                        }
                                }
                        }
                }

        private val connectionLifecycleCallback =
                object : ConnectionLifecycleCallback() {
                        override fun onConnectionInitiated(
                                endpointId: String,
                                connectionInfo: ConnectionInfo
                        ) {
                                pendingRemoteInfo[endpointId] =
                                        parseEndpointPayload(connectionInfo.endpointName)
                                connectionsClient.acceptConnection(endpointId, payloadCallback)
                        }

                        override fun onConnectionResult(
                                endpointId: String,
                                result: ConnectionResolution
                        ) {
                                when (result.status.statusCode) {
                                        ConnectionsStatusCodes.STATUS_OK -> {
                                                val remoteInfo =
                                                        pendingRemoteInfo.remove(endpointId)
                                                val profile =
                                                        EndpointMetadata(
                                                                memberId = remoteInfo?.memberId
                                                                                ?: endpointId,
                                                                endpointId = endpointId,
                                                                nickname = remoteInfo?.nickname
                                                                                ?: connectionInfoOrDefault(
                                                                                        null
                                                                                )
                                                        )
                                                connectedEndpoints[profile.memberId] = profile
                                                externalScope.launch {
                                                        eventFlow.emit(
                                                                NearbyEvent.MemberOnline(
                                                                        profile.memberId,
                                                                        profile.nickname
                                                                )
                                                        )
                                                }
                                        }
                                        else -> {
                                                externalScope.launch {
                                                        eventFlow.emit(
                                                                NearbyEvent.Error(
                                                                        DiagnosticsEvent(
                                                                                code =
                                                                                        "connection_failed",
                                                                                message =
                                                                                        "Connection failed: ${result.status.statusCode}"
                                                                        )
                                                                )
                                                        )
                                                }
                                        }
                                }
                        }

                        override fun onDisconnected(endpointId: String) {
                                connectedEndpoints.values
                                        .firstOrNull { it.endpointId == endpointId }
                                        ?.let { profile ->
                                                connectedEndpoints.remove(profile.memberId)
                                                externalScope.launch {
                                                        eventFlow.emit(
                                                                NearbyEvent.MemberOffline(
                                                                        profile.memberId
                                                                )
                                                        )
                                                }
                                        }
                        }
                }

        private val discoveryCallback =
                object : EndpointDiscoveryCallback() {
                        override fun onEndpointFound(
                                endpointId: String,
                                info: DiscoveredEndpointInfo
                        ) {
                                val endpointName =
                                        localEndpointInfo?.let { formatEndpointPayload(it) }
                                                ?: defaultEndpointName()
                                connectionsClient.requestConnection(
                                                endpointName,
                                                endpointId,
                                                connectionLifecycleCallback
                                        )
                                        .addOnFailureListener { throwable ->
                                                externalScope.launch {
                                                        eventFlow.emit(
                                                                NearbyEvent.Error(
                                                                        DiagnosticsEvent(
                                                                                code =
                                                                                        "request_connection_failed",
                                                                                message =
                                                                                        "Failed to request connection",
                                                                                cause = throwable
                                                                        )
                                                                )
                                                        )
                                                }
                                        }
                        }

                        override fun onEndpointLost(endpointId: String) {
                                connectedEndpoints.values
                                        .firstOrNull { it.endpointId == endpointId }
                                        ?.let { profile ->
                                                connectedEndpoints.remove(profile.memberId)
                                                externalScope.launch {
                                                        eventFlow.emit(
                                                                NearbyEvent.MemberOffline(
                                                                        profile.memberId
                                                                )
                                                        )
                                                }
                                        }
                        }
                }

        // start: 启动Nearby服务
        // localInfo包含本设备的ID和昵称
        public fun start(localInfo: EndpointInfo) {
                localEndpointInfo = localInfo
                if (isRunning) {
                        // 如果已经运行，重启广播和发现（刷新）
                        restartAdvertisingAndDiscovery()
                } else {
                        // 首次启动
                        isRunning = true
                        startAdvertisingAndDiscovery(localInfo)
                }
        }

        // refreshDiscovery: 刷新发现
        // 用于重新搜索附近设备
        public fun refreshDiscovery() {
                if (!isRunning) return
                restartAdvertisingAndDiscovery()
        }

        // stop: 停止Nearby服务
        // 停止广播、发现、断开所有连接
        public fun stop() {
                isRunning = false
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                connectionsClient.stopAllEndpoints()
                connectedEndpoints.clear()
        }

        // hasConnectedEndpoints: 检查是否有连接的设备
        public fun hasConnectedEndpoints(): Boolean = connectedEndpoints.isNotEmpty()

        public suspend fun broadcast(
                conversationId: ConversationId,
                message: MeshEnvelope,
                targetMembers: Collection<MemberId>? = null,
                excludeEndpointId: String? = null
        ): Boolean {
                val payloadString = json.encodeToString(message)
                val payload = Payload.fromBytes(payloadString.toByteArray(StandardCharsets.UTF_8))
                val targets =
                        if (targetMembers.isNullOrEmpty()) {
                                connectedEndpoints.values.map { it.endpointId }
                        } else {
                                targetMembers.mapNotNull { connectedEndpoints[it]?.endpointId }
                        }
                val finalTargets =
                        if (excludeEndpointId != null) {
                                targets.filter { it != excludeEndpointId }
                        } else {
                                targets
                        }

                if (finalTargets.isEmpty()) {
                        externalScope.launch {
                                eventFlow.emit(
                                        NearbyEvent.Error(
                                                DiagnosticsEvent(
                                                        code = "payload_send_skipped",
                                                        message =
                                                                "No connected endpoints for $conversationId"
                                                )
                                        )
                                )
                        }
                        return false
                }
                return runCatching {
                                finalTargets.forEach { endpointId ->
                                        connectionsClient.sendPayload(endpointId, payload)
                                }
                        }
                        .onFailure { throwable ->
                                externalScope.launch {
                                        eventFlow.emit(
                                                NearbyEvent.Error(
                                                        DiagnosticsEvent(
                                                                code = "payload_send_failure",
                                                                message =
                                                                        "Failed to send payload for $conversationId",
                                                                cause = throwable
                                                        )
                                                )
                                        )
                                }
                        }
                        .isSuccess
        }

        private suspend fun forwardEnvelope(envelope: MeshEnvelope, excludeEndpointId: String?) {
                if (envelope.hopCount >= envelope.maxHops) return
                broadcast(envelope.conversationId, envelope, excludeEndpointId = excludeEndpointId)
        }

        public fun events(): SharedFlow<NearbyEvent> = eventFlow
        public fun isMemberConnected(memberId: MemberId): Boolean =
                connectedEndpoints.containsKey(memberId)

        private fun startAdvertisingAndDiscovery(info: EndpointInfo) {
                connectionsClient.startAdvertising(
                                formatEndpointPayload(info),
                                SERVICE_ID,
                                connectionLifecycleCallback,
                                buildAdvertisingOptions()
                        )
                        .addOnFailureListener { throwable ->
                                externalScope.launch {
                                        eventFlow.emit(
                                                NearbyEvent.Error(
                                                        DiagnosticsEvent(
                                                                code = "advertising_failed",
                                                                message =
                                                                        "Unable to start advertising",
                                                                cause = throwable
                                                        )
                                                )
                                        )
                                }
                        }
                connectionsClient.startDiscovery(
                                SERVICE_ID,
                                discoveryCallback,
                                buildDiscoveryOptions()
                        )
                        .addOnFailureListener { throwable ->
                                externalScope.launch {
                                        eventFlow.emit(
                                                NearbyEvent.Error(
                                                        DiagnosticsEvent(
                                                                code = "discovery_failed",
                                                                message =
                                                                        "Unable to start discovery",
                                                                cause = throwable
                                                        )
                                                )
                                        )
                                }
                        }
        }

        private fun restartAdvertisingAndDiscovery() {
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                localEndpointInfo?.let { startAdvertisingAndDiscovery(it) }
        }

        private fun connectionInfoOrDefault(endpointName: String?): String =
                endpointName?.takeIf { it.isNotBlank() } ?: "Device-${Build.MODEL ?: "unknown"}"

        private fun defaultEndpointName(): String =
                formatEndpointPayload(
                        localEndpointInfo ?: EndpointInfo(nickname = Build.MODEL ?: "Android")
                )

        private fun formatEndpointPayload(info: EndpointInfo): String =
                "${info.memberId}|${info.nickname}"

        private fun parseEndpointPayload(raw: String?): EndpointInfo? {
                if (raw.isNullOrBlank()) return null
                val parts = raw.split("|", limit = 2)
                return if (parts.size == 2) {
                        EndpointInfo(memberId = parts[0], nickname = parts[1])
                } else {
                        EndpointInfo(memberId = raw, nickname = raw)
                }
        }

        private fun buildAdvertisingOptions(): AdvertisingOptions =
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        private fun buildDiscoveryOptions(): DiscoveryOptions =
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
}

public sealed interface NearbyEvent {
        public data class MemberOnline(val memberId: MemberId, val nickname: String?) : NearbyEvent
        public data class MemberOffline(val memberId: MemberId) : NearbyEvent
        public data class MessageReceived(val envelope: MeshEnvelope) : NearbyEvent
        public data class Error(val diagnosticsEvent: DiagnosticsEvent) : NearbyEvent
}

public data class EndpointInfo(
        val memberId: MemberId = UUID.randomUUID().toString(),
        val nickname: String
)

private data class EndpointMetadata(
        val memberId: MemberId,
        val endpointId: String,
        val nickname: String?
)