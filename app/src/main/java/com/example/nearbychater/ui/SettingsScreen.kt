package com.example.nearbychater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nearbychater.ui.state.SettingsViewModel
import com.example.nearbychater.util.ClipboardUtils
import kotlinx.coroutines.launch

// SettingsScreen: 设置界面
// 提供各种应用设置选项:
// - 后台运行开关
// - 诊断模式开关
// - 测试数据生成
@Composable
internal fun SettingsScreen(
        modifier: Modifier = Modifier,
        viewModel: SettingsViewModel,
        selfMemberId: String // 本机设备ID，用于显示和复制
) {
        // 订阅ViewModel中的状态
        // by关键字是属性委托，让我们可以直接使用Boolean而不是State<Boolean>
        val diagnosticsEnabled by viewModel.diagnosticsEnabled.collectAsStateWithLifecycle()
        val backgroundServiceEnabled by
                viewModel.backgroundServiceEnabled.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        // safeInsets: 安全区域内边距
        // 确保内容不被状态栏/导航栏遮挡
        val safeInsets =
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

        // 对话框显示状态
        // remember{}保存状态，mutableStateOf()创建可变状态
        // value属性用于访问/修改值
        val showGenerateSamplesDialog = remember { mutableStateOf(false) }
        val showDeleteSamplesDialog = remember { mutableStateOf(false) }

        // 使用WindowInsets.ime来处理键盘弹出，确保布局正确
        val windowInsets = WindowInsets.safeDrawing
        
        Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
        ) {
                Column(
                        modifier =
                                Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(windowInsets.asPaddingValues())
                                        .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        // === 标题部分 ===
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = "设置",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        // === 设备ID部分 ===
                        Text(text = "设备信息", style = MaterialTheme.typography.headlineSmall)
                        MemberIdCard(memberId = selfMemberId)

                        // === 服务选项部分 ===
                        Text(text = "服务选项", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.fillMaxWidth()) {
                                Text(text = "保持后台运行", style = MaterialTheme.typography.titleMedium)
                                Text(text = "关闭后，应用进入后台将停止接收消息。")
                                RowWithSwitch(
                                        checked = backgroundServiceEnabled,
                                        onCheckedChange = {
                                                viewModel.setBackgroundServiceEnabled(it)
                                        }
                                )
                        }

                        // === 诊断选项部分 ===
                        Text(text = "诊断选项", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.fillMaxWidth()) {
                                Text(text = "诊断气泡", style = MaterialTheme.typography.titleMedium)
                                Text(text = "在演示或调试时于界面顶部显示错误提示。")
                                RowWithSwitch(
                                        checked = diagnosticsEnabled,
                                        onCheckedChange = { viewModel.setDiagnosticsEnabled(it) }
                                )
                        }

                        // === 性能测试部分 ===
                        Text(text = "性能测试", style = MaterialTheme.typography.headlineSmall)
                        Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Button(
                                        onClick = { showGenerateSamplesDialog.value = true },
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = "生成测试数据")
                                }
                                Button(
                                        onClick = { showDeleteSamplesDialog.value = true },
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = "删除测试数据")
                                }
                        }
                }
        }

        // === 生成测试数据确认对话框 ===
        // 只有when showGenerateSamplesDialog.value为true时才显示
        if (showGenerateSamplesDialog.value) {
                // AlertDialog: 警告对话框
                androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showGenerateSamplesDialog.value = false },
                        title = { Text("生成会话列表") },
                        text = { Text("即将生成 50 个测试群聊，每个群聊包含3-20个随机用户，每个群聊有10-30条消息（包括文本和表情符号）。确定继续吗？") },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                showGenerateSamplesDialog.value = false
                                                viewModel.generateAllSamples() // 调用ViewModel生成数据（同时生成单聊和群聊）
                                        }
                                ) { Text("生成") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showGenerateSamplesDialog.value = false }) {
                                        Text("取消")
                                }
                        }
                )
        }

        // === 删除测试数据确认对话框 ===
        if (showDeleteSamplesDialog.value) {
                androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDeleteSamplesDialog.value = false },
                        title = { Text("删除生成会话列表") },
                        text = { Text("将删除已生成的会话及相关成员/消息，操作不可撤销。") },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                showDeleteSamplesDialog.value = false
                                                viewModel.deleteSamples() // 调用ViewModel删除数据
                                        }
                                ) { Text("删除") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showDeleteSamplesDialog.value = false }) {
                                        Text("取消")
                                }
                        }
                )
        }
        
        // === 加载状态弹窗 ===
        if (isLoading) {
                androidx.compose.material3.AlertDialog(
                        onDismissRequest = {}, // 加载中不可关闭
                        title = { Text("操作进行中") },
                        text = {
                                Column(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        androidx.compose.material3.CircularProgressIndicator()
                                        Text("正在处理，请稍候...")
                                }
                        },
                        confirmButton = {}, // 加载中不显示按钮
                        dismissButton = {} // 加载中不显示按钮
                )
        }
}

// MemberIdCard: 设备ID卡片组件
// 显示用户的设备ID，并提供复制功能
@Composable
private fun MemberIdCard(memberId: String) {
        // rememberCoroutineScope: 创建协程作用域
        // 用于在Composable中启动协程
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large, // 更大的圆角
                color = MaterialTheme.colorScheme.surfaceVariant, // 使用surfaceVariant颜色增强区分度
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 2.dp, // 添加轻微阴影
                shadowElevation = 2.dp
        ) {
                // Row: 水平布局
                Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, // 两端对齐
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // 左侧：设备ID和提示文字
                        Column(Modifier.weight(1f)) { // weight(1f)占据剩余空间
                                Text(
                                        text = "设备ID",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                        Text(
                                                text = memberId,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(8.dp)
                                        )
                                }
                                Text(
                                        text = "分享给联系人以便添加你",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        // 右侧：复制按钮
                        TextButton(
                                onClick = {
                                        // scope.launch启动协程调用ClipboardUtils
                                        scope.launch { ClipboardUtils.copyText(context, memberId) }
                                },
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.textButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                        ) {
                                Text(
                                        text = "复制",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                )
                        }
                }
        }
}

// 剪贴板功能由ClipboardUtils处理

// RowWithSwitch: 带开关的行组件
// 左侧显示状态文字，右侧是开关
@Composable
private fun RowWithSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // 根据开关状态显示不同文字
                Text(
                        text = if (checked) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyLarge
                )
                // Switch: Material Design的开关组件
                // checked控制开关状态
                // onCheckedChange在用户切换时调用
                Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
}