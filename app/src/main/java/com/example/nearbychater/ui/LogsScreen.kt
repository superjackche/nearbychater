package com.example.nearbychat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nearbychat.ui.state.SettingsViewModel

// LogsScreen: 日志查看界面
// 显示应用运行过程中的诊断日志
// 用于调试和排查问题
@Composable
internal fun LogsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel) {
    // collectAsStateWithLifecycle订阅ViewModel中的日志列表
    // 当日志更新时，UI会自动刷新
    val logs by viewModel.logLines.collectAsStateWithLifecycle()

    // safeInsets: 安全内边距
    // 避免内容被系统栏(状态栏/导航栏)遮挡
    // only()指定只应用顶部和左右的安全区域
    val safeInsets =
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background // 使用主题背景色适配暗黑模式
    ) {
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(safeInsets.asPaddingValues()) // 应用安全内边距
                                .padding(16.dp), // 额外的内边距
                verticalArrangement = Arrangement.spacedBy(12.dp) // 子元素间距12dp
        ) {
            // 标题
            Text(text = "Diagnostics Log", style = MaterialTheme.typography.headlineSmall)

            // 操作按钮行：刷新和清空
            RowOfButtons(onRefresh = { viewModel.refreshLogs() }, onClear = { viewModel.clearLogs() })

            // 日志内容区域
            // Surface提供轻微的高度效果(tonalElevation)
            Surface(
                tonalElevation = 2.dp, 
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface // 使用surface color适配暗黑模式
            ) {
                // LazyColumn: 懒加载列表
                // 只渲染可见的日志行，性能好
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // items()遍历日志列表
                    // 为每一行日志创建UI
                    items(logs) { line ->
                        Text(
                            text = line, 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface // 使用onSurface color适配暗黑模式
                        )
                        // HorizontalDivider: 水平分割线
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }
    }
}

// RowOfButtons: 按钮行组件
// 包含刷新和清空按钮
@Composable
private fun RowOfButtons(onRefresh: () -> Unit, onClear: () -> Unit) {
    androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp) // 按钮间距
    ) {
        Button(onClick = onRefresh) { Text("Refresh") }
        Button(onClick = onClear) { Text("Clear Logs") }
    }
}