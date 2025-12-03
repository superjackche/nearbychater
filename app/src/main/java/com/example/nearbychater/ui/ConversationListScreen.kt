package com.example.nearbychat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nearbychat.core.model.ConversationSummary
import com.example.nearbychat.ui.state.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ConversationListScreen: 会话列表界面
// 显示所有聊天会话的列表，类似微信的会话列表
// 关键功能:
// 1. 下拉刷新 (Pull-to-refresh)
// 2. 侧滑操作 (置顶/删除)
// 3. 点击进入聊天
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationListScreen(
        modifier: Modifier = Modifier,
        viewModel: ChatViewModel,
        onConversationSelected: (String) -> Unit, // 选中会话的回调
        onOpenSettings: () -> Unit, // 打开设置的回调
        onOpenLogs: () -> Unit // 打开日志的回调
) {
    // 订阅会话摘要列表
    val summaries by viewModel.conversationSummaries.collectAsStateWithLifecycle()

    // 显示添加联系人对话框的状态
    var showAddContact by remember { mutableStateOf(false) }
    // 显示创建群聊对话框的状态
    var showCreateGroup by remember { mutableStateOf(false) }
    // 显示添加菜单的状态
    var showAddMenu by remember { mutableStateOf(false) }

    // filtered: 过滤后的会话列表
    // 这里直接使用所有会话，没有过滤
    val filtered = summaries

    // aliases: 会话别名 (用户自定义的名称)
    val aliases by viewModel.conversationAliases.collectAsStateWithLifecycle()

    // isRefreshing: 是否正在刷新
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // safeInsets: 安全区域内边距
    val safeInsets =
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val safePadding = safeInsets.asPaddingValues()

    // indicatorTopPadding: 刷新指示器的顶部内边距
    // LocalDensity.current获取当前屏幕密度，用于将px转换为dp
    val indicatorTopPadding = with(LocalDensity.current) { safeInsets.getTop(this).toDp() }

    // Box: 最外层容器
    // 用于堆叠布局，把刷新指示器放在列表上方
    Box(
            modifier =
                    modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(safePadding).padding(horizontal = 12.dp)) {
            // 顶部栏: 显示应用名称和操作按钮
            TopBar(
                    onAddMenuToggle = { showAddMenu = !showAddMenu },
                    onAddMenuDismiss = { showAddMenu = false },
                    onAddContact = { showAddContact = true },
                    onCreateGroup = { showCreateGroup = true },
                    onLogs = onOpenLogs,
                    onSettings = onOpenSettings,
                    isAddMenuExpanded = showAddMenu
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Surface: 会话列表容器
            Surface(
                    modifier = Modifier.weight(1f), // weight(1f)占据剩余空间
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp, // 轻微高度效果
                    shape = MaterialTheme.shapes.small
            ) {
                // PullToRefreshBox: 下拉刷新容器
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshConversations() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 如果没有会话，显示提示文字
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "暂无会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        // LazyColumn: 懒加载列表
                        // 只渲染可见的项，性能好
                        LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // items()遍历会话列表
                            // key参数帮助Compose优化重组
                            // contentType参数帮助Compose复用布局，提高性能
                            items(filtered, key = { it.conversationId }, contentType = { "conversation" }) { summary ->
                                // onDelete: 删除回调
                                // 如果是自己的会话null（不能删除），否则提供删除函数
                                val onDelete = if (summary.isSelf) null else { { viewModel.deleteConversation(summary.conversationId) } }
                                // displayTitle: 显示名称
                                // 优先使用别名，其次使用默认标题
                                val displayTitle = aliases[summary.conversationId] ?: summary.title

                                // ConversationRow: 会话行组件
                                // 支持点击、删除、置顶
                                ConversationRow(
                                        summary = summary,
                                        displayTitle = displayTitle,
                                        onClick = {
                                            viewModel.selectConversation(summary.conversationId)
                                            onConversationSelected(summary.conversationId)
                                        },
                                        onDelete = onDelete,
                                        onTogglePinned = {
                                            viewModel.setConversationPinned(
                                                    summary.conversationId,
                                                    !summary.isPinned
                                            )
                                        }
                                )
                                // HorizontalDivider: 分割线
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            // 如果显示添加联系人对话框
            if (showAddContact) {
                AddContactDialog(
                        selfId = viewModel.selfMemberId,
                        onDismiss = { showAddContact = false },
                        onConfirm = { memberIds ->
                            if (memberIds.isNotEmpty()) {
                                val conversationId = viewModel.ensureConversation(memberIds)
                                onConversationSelected(conversationId)
                            }
                            showAddContact = false
                        }
                )
            }
            // 如果显示创建群聊对话框
            if (showCreateGroup) {
                CreateGroupDialog(
                        viewModel = viewModel,
                        onDismiss = { showCreateGroup = false },
                        onConfirm = { selectedMembers ->
                            if (selectedMembers.isNotEmpty()) {
                                val conversationId = viewModel.ensureConversation(selectedMembers)
                                onConversationSelected(conversationId)
                            }
                            showCreateGroup = false
                        }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
        onAddMenuToggle: () -> Unit,
        onAddMenuDismiss: () -> Unit,
        onAddContact: () -> Unit,
        onCreateGroup: () -> Unit,
        onLogs: () -> Unit,
        onSettings: () -> Unit,
        isAddMenuExpanded: Boolean
) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    text = "myMiniChat",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = onAddMenuToggle) {
                    Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加",
                            tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                        expanded = isAddMenuExpanded,
                        onDismissRequest = onAddMenuDismiss
                ) {
                    DropdownMenuItem(
                            text = { Text("添加联系人") },
                            onClick = {
                                onAddMenuDismiss()
                                onAddContact()
                            }
                    )
                    DropdownMenuItem(
                            text = { Text("创建群聊") },
                            onClick = {
                                onAddMenuDismiss()
                                onCreateGroup()
                            }
                    )
                }
            }
            IconButton(onClick = onLogs) {
                Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Logs",
                        tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0xFFF6F6F6)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(text = "搜索", color = Color(0xFF9B9B9B)) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF9B9B9B)) },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true
        )
    }
}

// ConversationRow: 会话行组件
// 支持侧滑操作:
// - 向右滑: 置顶/取消置顶
// - 向左滑: 删除会话
// @OptIn使用SwipeToDismissBox实验性API
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationRow(
        summary: ConversationSummary,
        displayTitle: String,
        onClick: () -> Unit,
        onDelete: (() -> Unit)?, // 为null表示不能删除
        onTogglePinned: () -> Unit
) {
    // dismissState: 侧滑状态
    // rememberSwipeToDismissBoxState创建侧滑状态管理器
    // confirmValueChange在侧滑完成时调用
    val dismissState =
            rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        when (value) {
                            // StartToEnd: 向右滑 -> 置顶/取消置顶
                            SwipeToDismissBoxValue.StartToEnd -> {
                                onTogglePinned()
                                false // 返回false表示不消失，只执行操作
                            }
                            // EndToStart: 向左滑 -> 删除
                            SwipeToDismissBoxValue.EndToStart -> {
                                if (onDelete != null) {
                                    onDelete()
                                }
                                false
                            }
                            else -> true
                        }
                    }
            )

    // showPinBackground: 是否显示置顶背景
    // derivedStateOf{}创建计算状态，当依赖状态变化时自动重算
    val showPinBackground by remember {
        derivedStateOf {
            dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd ||
                    dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
        }
    }

    // showDeleteBackground: 是否显示删除背景
    val showDeleteBackground by remember {
        derivedStateOf {
            dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
                    dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
        }
    }

    Box(modifier = Modifier.padding(horizontal = 4.dp).clip(MaterialTheme.shapes.small)) {
        // SwipeToDismissBox: 支持侧滑的容器
        SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.clip(MaterialTheme.shapes.small),
                enableDismissFromStartToEnd = true, // 启用向右滑
                enableDismissFromEndToStart = onDelete != null, // 只有能删除的才启用向左滑
                // backgroundContent: 背景内容(侧滑时显示)
                backgroundContent = {
                    when {
                        showPinBackground -> PinBackground(isPinned = summary.isPinned)
                        showDeleteBackground -> DeleteBackground()
                    }
                },
                // content: 主内容(会话行内容)
                content = {
                    ConversationRowContent(
                            summary = summary,
                            displayTitle = displayTitle,
                            onClick = onClick
                    )
                }
        )
    }
}

@Composable
private fun PinBackground(isPinned: Boolean) {
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.tertiary)
                            .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary
            )
            Text(
                    text = if (isPinned) "取消置顶" else "置顶",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeleteBackground() {
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterEnd
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError
            )
            Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConversationRowContent(
        summary: ConversationSummary,
        displayTitle: String,
        onClick: () -> Unit
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarBubble(seed = summary.avatarSeed, title = summary.title)
        Spacer(modifier = Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                )
                if (summary.isPinned) {
                    Text(
                            text = "置顶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                    Modifier.clip(MaterialTheme.shapes.small)
                                            .background(
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.12f
                                                    )
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                    text = summary.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                            if (summary.isSelf) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
        Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                    text = formatTimestamp(summary.lastTimestamp).takeIf { it.isNotEmpty() }
                                    ?: if (summary.isSelf) "置顶" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (summary.unreadCount > 0) {
                UnreadBadge(count = summary.unreadCount)
            }
        }
    }
}

@Composable
private fun AvatarBubble(seed: String, title: String) {
    val initials = title.firstOrNull()?.uppercaseChar()?.toString() ?: seed.takeLast(2)
    Box(
            modifier =
                    Modifier.size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = initials,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
            modifier =
                    Modifier.clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
                text = if (count > 99) "99+" else count.toString(),
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AddContactDialog(
        selfId: String,
        onDismiss: () -> Unit,
        onConfirm: (List<String>) -> Unit
) {
    var memberInput by remember { mutableStateOf("") }
    val parsedMembers = remember(selfId, memberInput) { parseMemberIds(memberInput, selfId) }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "添加联系人") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "输入一个或多个设备 ID，使用逗号、空格或换行分隔。")
                    OutlinedTextField(
                            value = memberInput,
                            onValueChange = { memberInput = it },
                            singleLine = false,
                            maxLines = 4,
                            label = { Text("成员 ID 列表") },
                            placeholder = { Text("例如：abc123, def456") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                        onClick = { onConfirm(parsedMembers) },
                        enabled = parsedMembers.isNotEmpty()
                ) { Text("创建会话") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun parseMemberIds(raw: String, selfId: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val delimiters = charArrayOf(',', ';', ' ', '\n')
    return raw.split(*delimiters)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != selfId }
            .distinct()
}

// CreateGroupDialog: 创建群聊对话框
// 支持选择多个成员
@Composable
private fun CreateGroupDialog(
        viewModel: ChatViewModel,
        onDismiss: () -> Unit,
        onConfirm: (List<String>) -> Unit
) {
    // 订阅成员列表
    val members by viewModel.members.collectAsStateWithLifecycle()
    // 过滤掉自己
    val availableMembers = members.filter { it.memberId != viewModel.selfMemberId }
    // 选中的成员ID集合
    var selectedMemberIds by remember { mutableStateOf(emptySet<String>()) }
    
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "创建群聊") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "选择要添加到群聊的成员：")
                    // 成员选择列表
                    LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(count = availableMembers.size) { index ->
                            val member = availableMembers[index]
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedMemberIds = 
                                        if (selectedMemberIds.contains(member.memberId)) {
                                            selectedMemberIds - member.memberId
                                        } else {
                                            selectedMemberIds + member.memberId
                                        }
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 头像气泡
                                AvatarBubble(seed = member.memberId, title = member.memberId)
                                // 成员信息
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.localNickname ?: member.remoteNickname ?: member.memberId.take(6),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (member.isOnline) "在线" else "离线",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (member.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // 复选框
                                Checkbox(
                                    checked = selectedMemberIds.contains(member.memberId),
                                    onCheckedChange = { _ ->
                                        selectedMemberIds = 
                                            if (selectedMemberIds.contains(member.memberId)) {
                                                selectedMemberIds - member.memberId
                                            } else {
                                                selectedMemberIds + member.memberId
                                            }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                        onClick = { onConfirm(selectedMemberIds.toList()) },
                        enabled = selectedMemberIds.isNotEmpty()
                ) {
                    Text("创建群聊")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(date)
}