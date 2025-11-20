package com.example.miniwechat.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.miniwechat.core.model.Attachment
import com.example.miniwechat.core.model.AttachmentType
import com.example.miniwechat.core.model.ChatMessage
import com.example.miniwechat.core.model.ConversationId
import com.example.miniwechat.core.model.MemberId
import com.example.miniwechat.core.model.MemberProfile
import com.example.miniwechat.core.model.MessageStatus
import com.example.miniwechat.ui.state.ChatViewModel
import com.example.miniwechat.ui.state.DiagnosticsBubbleState
import com.example.miniwechat.ui.theme.BubbleGray
import com.example.miniwechat.ui.theme.SentBubbleDark
import com.example.miniwechat.ui.theme.SentBubbleLight
import java.text.SimpleDateFormat
import java.util.Locale

// @Composable：标记UI构建函数 (类比Python装饰器)
// 声明式UI入口
@Composable
internal fun ChatScreen(
        modifier: Modifier = Modifier,
        conversationId: ConversationId?,
        onBack: () -> Unit,
        onOpenSettings: () -> Unit,
        onOpenLogs: () -> Unit,
        viewModel: ChatViewModel = viewModel()
) {
    // collectAsStateWithLifecycle：生命周期感知的状态收集
    // ViewModel负责持有数据，屏幕旋转不丢失 (类比MVC Controller)
    val members by viewModel.members.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnosticsBubble.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val aliases by viewModel.conversationAliases.collectAsStateWithLifecycle()
    val snapshots by viewModel.conversationSnapshots.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var composerText by rememberSaveable(activeConversationId) { mutableStateOf("") }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<Attachment?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val photoPickerLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let { viewModel.sendPhoto(it) }
            }

    // LaunchedEffect：启动协程副作用
    // 监听conversationId变化，触发会话选中逻辑
    LaunchedEffect(conversationId) { conversationId?.let { viewModel.selectConversation(it) } }

    val currentConversationId = activeConversationId
    val activeSnapshot = currentConversationId?.let { snapshots[it] }
    val remoteMemberIds =
            activeSnapshot?.memberIds?.filterNot { it == viewModel.selfMemberId } ?: emptyList()
    val activeMembers =
            remember(remoteMemberIds, members) {
                remoteMemberIds.mapNotNull { id -> members.firstOrNull { it.memberId == id } }
            }
    val connectedCount = remember(activeMembers) { activeMembers.count { it.isOnline } }
    val aliasTitle = aliases[currentConversationId]?.takeIf { it.isNotBlank() }
    val title = aliasTitle ?: defaultConversationTitle(activeMembers, remoteMemberIds)
    val subtitle =
            remember(remoteMemberIds, connectedCount, messages) {
                when {
                    remoteMemberIds.isEmpty() -> "本机收藏夹"
                    connectedCount == remoteMemberIds.size && connectedCount > 0 ->
                            "已连接 · ${connectedCount}人"
                    connectedCount in 1 until remoteMemberIds.size ->
                            "部分在线 · $connectedCount/${remoteMemberIds.size} 人"
                    messages.any { it.senderId != viewModel.selfMemberId } -> {
                        val recent = messages.lastOrNull { it.senderId != viewModel.selfMemberId }
                        recent?.let { "最近活跃 · ${formatTimestamp(it.timestamp)}" } ?: "最近活跃"
                    }
                    else -> "等待连接"
                }
            }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
                Modifier.fillMaxSize()
                        .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                )
                        )
                        .imePadding()
        ) {
            ChatTopBar(
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    menuExpanded = overflowMenuExpanded,
                    onToggleMenu = { overflowMenuExpanded = !overflowMenuExpanded },
                    onDismissMenu = { overflowMenuExpanded = false },
                    canRename = currentConversationId != null,
                    onRenameConversation = {
                        overflowMenuExpanded = false
                        if (currentConversationId != null) {
                            showRenameDialog = true
                        }
                    },
                    onOpenSettings = {
                        overflowMenuExpanded = false
                        onOpenSettings()
                    },
                    onOpenLogs = {
                        overflowMenuExpanded = false
                        onOpenLogs()
                    }
            )
            HorizontalDivider(color = Color(0x1F000000))
            MessageList(
                    modifier = Modifier.weight(1f),
                    messages = messages,
                    members = members,
                    selfId = viewModel.selfMemberId,
                    onCancel = { viewModel.cancelMessage(it) },
                    onAttachmentClick = { previewAttachment = it }
            )
            HorizontalDivider(color = Color(0x1F000000))
            MessageComposerBar(
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                    text = composerText,
                    onTextChange = { composerText = it },
                    onSend = {
                        if (composerText.isNotBlank()) {
                            viewModel.sendChatMessage(composerText.trim())
                            composerText = ""
                        }
                    },
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                        )
                    }
            )
        }

        DiagnosticsBubble(
                state = diagnostics,
                onDismiss = { viewModel.dismissDiagnosticsBubble() },
                modifier =
                        Modifier.align(Alignment.TopCenter)
                                .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                                )
                                .padding(top = 16.dp)
        )

        previewAttachment?.let { attachment ->
            PhotoPreviewDialog(
                    attachment = attachment,
                    onDismiss = { previewAttachment = null },
                    onSave = {
                        val success = saveAttachmentToGallery(context, attachment)
                        Toast.makeText(
                                        context,
                                        if (success) "已保存到相册" else "保存失败",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                        if (success) {
                            previewAttachment = null
                        }
                    }
            )
        }

        if (showRenameDialog && currentConversationId != null) {
            RenameConversationDialog(
                    initialValue = aliasTitle.orEmpty(),
                    onConfirm = { name ->
                        if (name.isBlank()) {
                            viewModel.clearConversationAlias(currentConversationId)
                        } else {
                            viewModel.setConversationAlias(currentConversationId, name)
                        }
                        showRenameDialog = false
                    },
                    onReset = {
                        viewModel.clearConversationAlias(currentConversationId)
                        showRenameDialog = false
                    },
                    onDismiss = { showRenameDialog = false }
            )
        }
    }
}

@Composable
private fun ChatTopBar(
        title: String,
        subtitle: String,
        onBack: () -> Unit,
        menuExpanded: Boolean,
        onToggleMenu: () -> Unit,
        onDismissMenu: () -> Unit,
        canRename: Boolean,
        onRenameConversation: () -> Unit,
        onOpenSettings: () -> Unit,
        onOpenLogs: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = onToggleMenu) {
                    Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "More")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                    if (canRename) {
                        DropdownMenuItem(text = { Text("重命名聊天") }, onClick = onRenameConversation)
                    }
                    DropdownMenuItem(text = { Text("设置") }, onClick = onOpenSettings)
                    DropdownMenuItem(text = { Text("开发者日志") }, onClick = onOpenLogs)
                }
            }
        }
    }
}

@Composable
private fun MessageList(
        modifier: Modifier = Modifier,
        messages: List<ChatMessage>,
        members: List<MemberProfile>,
        selfId: String,
        onCancel: (String) -> Unit,
        onAttachmentClick: (Attachment) -> Unit
) {
    val listState = rememberLazyListState()
    var shouldAutoScroll by remember { mutableStateOf(true) }

    // 自动滚动逻辑：当有新消息且当前位于底部时，自动滚动至最新
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // 阈值判断：是否接近底部
            val isNearBottom =
                    listState.firstVisibleItemIndex >= listState.layoutInfo.totalItemsCount - 10
            if (isNearBottom || listState.firstVisibleItemIndex == 0) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    // LazyColumn：按需渲染列表项，优化长列表性能 (类比生成器)
    // 仅渲染可见区域，避免OOM
    LazyColumn(
            modifier =
                    modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        itemsIndexed(
                items = messages,
                // key：唯一标识符，优化Compose重组性能 (类似数据库主键)
                key = { _, item -> item.id },
                // contentType：复用视图类型，提升滚动流畅度
                contentType = { _, item -> if (item.senderId == selfId) 1 else 2 }
        ) { index, message ->
            val profile = members.firstOrNull { it.memberId == message.senderId }
            ChatBubble(
                    message = message,
                    isOwn = message.senderId == selfId,
                    profile = profile,
                    onCancel = { onCancel(message.id) },
                    onAttachmentClick = onAttachmentClick
            )
        }
    }
}

// @OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatBubble(
        message: ChatMessage,
        isOwn: Boolean,
        profile: MemberProfile?,
        onCancel: () -> Unit,
        onAttachmentClick: (Attachment) -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bubbleColor =
            when {
                isOwn && isDarkTheme -> SentBubbleDark
                isOwn -> SentBubbleLight
                isDarkTheme -> MaterialTheme.colorScheme.surface
                else -> BubbleGray // 使用BubbleGray颜色而不是MaterialTheme.colorScheme.surface
            }
    val bubbleContentColor =
            when {
                isOwn && isDarkTheme -> Color.White
                isOwn -> Color.Black
                else -> MaterialTheme.colorScheme.onSurface
            }
    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
    ) {
        Column(
                horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 300.dp) // 限制气泡最大宽度
        ) {
            if (!isOwn && profile != null) {
                Text(
                        text = profile.localNickname ?: profile.memberId.take(6),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                        color = bubbleColor,
                        contentColor = bubbleContentColor,
                        shape = MaterialTheme.shapes.large
                ) {
                    Column(
                            modifier =
                                    Modifier.clip(MaterialTheme.shapes.large)
                                            .combinedClickable(
                                                    onClick = { showActions = false },
                                                    onLongClick = { showActions = true }
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                            .widthIn(max = 260.dp)
                    ) {
                        if (message.attachment?.type == AttachmentType.PHOTO) {
                            PhotoAttachmentView(
                                    attachment = message.attachment,
                                    onClick = { onAttachmentClick(message.attachment) }
                            )
                            if (message.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        if (message.content.isNotBlank()) {
                            Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = formatTimestamp(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (isOwn) {
                    StatusIcon(status = message.status)
                }
            }
            // 长按显示操作菜单
            // AnimatedVisibility：处理显示/隐藏过渡动画
            if (showActions) {
                AnimatedVisibility(visible = showActions) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                                onClick = {
                                    onCancel()
                                    showActions = false
                                }
                        ) { Text("Cancel send") }
                        TextButton(onClick = { showActions = false }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.QUEUED -> {
            Icon(
                    imageVector = Icons.Rounded.Pending,
                    contentDescription = "Queued",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
            )
        }
        MessageStatus.SENDING ->
                CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                )
        MessageStatus.SENT ->
                Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Sent",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                )
        MessageStatus.DELIVERED ->
                Icon(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = "Delivered",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                )
        MessageStatus.CANCELLED ->
                Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                )
        MessageStatus.FAILED ->
                Icon(
                        imageVector = Icons.Rounded.Pending,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                )
    }
}

@Composable
internal fun DiagnosticsBubble(
        state: DiagnosticsBubbleState,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
) {
    AnimatedVisibility(
            visible = state.isVisible && state.latestEvent != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = modifier
    ) {
        Surface(
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        ) {
            Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = state.latestEvent?.code ?: "", fontWeight = FontWeight.Bold)
                    Text(
                            text = state.latestEvent?.message ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss diagnostics"
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageComposerBar(
        modifier: Modifier = Modifier,
        text: String,
        onTextChange: (String) -> Unit,
        onSend: () -> Unit,
        onPickPhoto: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
    ) {
        Row(
                modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("发个消息…") },
                    maxLines = 4,
                    colors =
                            androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedContainerColor =
                                            MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor =
                                            MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    focusedPlaceholderColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    unfocusedPlaceholderColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant
                            )
            )

            IconButton(onClick = onPickPhoto) {
                Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "发送图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PhotoAttachmentView(attachment: Attachment, onClick: () -> Unit) {
    // Box占位，避免图片加载时布局跳动
    // 使用Coil (AsyncImage) 异步加载图片，自动处理缓存与后台解码
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable(onClick = onClick)
    ) {
        AsyncImage(
                model =
                        ImageRequest.Builder(LocalContext.current)
                                .data(Base64.decode(attachment.dataBase64, Base64.DEFAULT))
                                .crossfade(true)
                                .build(),
                contentDescription = "图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun RenameConversationDialog(
        initialValue: String,
        onConfirm: (String) -> Unit,
        onReset: () -> Unit,
        onDismiss: () -> Unit
) {
    var text by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("设置聊天名称") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            label = { Text("聊天名称") },
                            placeholder = { Text("默认使用对方设备名") }
                    )
                    if (initialValue.isNotBlank()) {
                        TextButton(onClick = onReset) { Text("恢复默认") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun defaultConversationTitle(
        members: List<MemberProfile>,
        memberIds: List<MemberId>
): String {
    if (memberIds.isEmpty()) return "我"
    if (members.isEmpty()) return memberIds.joinToString(", ") { it.take(6) }
    if (members.size == 1) {
        return members.first().preferredName(memberIds.first())
    }
    val names = members.map { profile -> profile.preferredName(profile.memberId) }
    return if (names.size <= 3) names.joinToString("、")
    else names.take(2).joinToString("、") + " 等${names.size}人"
}

private fun MemberProfile.preferredName(fallbackId: MemberId): String {
    return localNickname?.takeIf { it.isNotBlank() }
            ?: remoteNickname?.takeIf { it.isNotBlank() } ?: deviceModel?.takeIf { it.isNotBlank() }
                    ?: fallbackId.take(6)
}

@Composable
private fun PhotoPreviewDialog(attachment: Attachment, onDismiss: () -> Unit, onSave: () -> Unit) {
    // 图片预览弹窗
    // 使用Coil加载，避免手动Bitmap解码导致的OOM风险

    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp.dp * 0.8f)
    val scrollState = rememberScrollState()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
            Column(
                    Modifier.padding(16.dp).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = 180.dp, max = maxHeight)
                                        .clip(MaterialTheme.shapes.medium)
                ) {
                    AsyncImage(
                            model =
                                    ImageRequest.Builder(LocalContext.current)
                                            .data(
                                                    Base64.decode(
                                                            attachment.dataBase64,
                                                            Base64.DEFAULT
                                                    )
                                            )
                                            .build(),
                            contentDescription = "图片预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                    )
                }
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(onClick = onSave) { Text("保存到相册") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

private fun formatTimestamp(time: Long): String {
    if (time == 0L) return ""
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(time)
}

private fun saveAttachmentToGallery(context: Context, attachment: Attachment): Boolean {
    if (attachment.type != AttachmentType.PHOTO) return false
    val mime = attachment.mimeType.ifBlank { "image/jpeg" }
    val extension = if (mime.contains("png", ignoreCase = true)) "png" else "jpg"
    val name = "miniwechat_${System.currentTimeMillis()}.$extension"
    val imageCollection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
    val resolver = context.contentResolver
    val pendingValues =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
    return runCatching {
        val uri = resolver.insert(imageCollection, pendingValues) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { stream ->
            val data = Base64.decode(attachment.dataBase64, Base64.DEFAULT)
            stream.write(data)
        }
                ?: return@runCatching false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val readyValues = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, readyValues, null, null)
        }
        true
    }
            .getOrElse { false }
}