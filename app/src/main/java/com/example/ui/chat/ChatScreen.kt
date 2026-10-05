package com.example.ui.chat
import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ActiveChatTracker

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Message
import com.example.ui.components.AvatarImage
import java.text.SimpleDateFormat
import java.util.*

fun formatLastSeen(timestamp: Long): String {
    if (timestamp == 0L) return "Offline"
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 60_000L) {
        return "Last seen just now"
    }
    if (diff < 3600_000L) {
        val minutes = (diff / 60_000L).toInt()
        return "Last seen ${minutes}m ago"
    }

    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    
    val now = Calendar.getInstance()
    
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(timestamp))
    
    return if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
        "Last seen today at $formattedTime"
    } else if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1) {
        "Last seen yesterday at $formattedTime"
    } else {
        val dateFormat = SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault())
        "Last seen " + dateFormat.format(Date(timestamp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String,
    otherUserId: String,
    currentUserId: String,
    isBlocked: Boolean = false,
    readReceiptsEnabled: Boolean = true,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val showBlueTicks = readReceiptsEnabled && (chatState.otherUser?.settings?.readReceiptsEnabled ?: true)
    val actualCurrentUserId = currentUserId.ifBlank { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    

    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppInForeground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(lifecycleOwner, chatId) {
        ActiveChatTracker.activeChatId = chatId
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAppInForeground = true
                ActiveChatTracker.activeChatId = chatId
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isAppInForeground = false
                ActiveChatTracker.activeChatId = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ActiveChatTracker.activeChatId = null
            viewModel.clearChatData()
        }
    }

    LaunchedEffect(chatState.messages, isAppInForeground, readReceiptsEnabled) {
        if (isAppInForeground) {
            val unreadMsgs = chatState.messages.filter { it.receiverId == actualCurrentUserId && it.status != com.example.data.model.MessageStatus.READ }
            if (readReceiptsEnabled) {
                unreadMsgs.forEach { msg ->
                    viewModel.markMessageAsRead(msg.id)
                }
            } else {
                if (unreadMsgs.isNotEmpty()) {
                    viewModel.resetUnreadCount()
                }
            }
        }
    }

    LaunchedEffect(chatId, otherUserId) {
        viewModel.loadChatData(chatId, otherUserId)
    }
    
    var messageText by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var messageToForward by remember { mutableStateOf<Message?>(null) }
    var messageToPin by remember { mutableStateOf<Message?>(null) }
    
    LaunchedEffect(messageText) {
        viewModel.setTyping(messageText.isNotBlank())
        if (messageText.isNotBlank()) {
            kotlinx.coroutines.delay(3000)
            viewModel.setTyping(false)
        }
    }
    
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible
    val messageCount = chatState.messages.size

    LaunchedEffect(isImeVisible, messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(0)
        }
    }

    val otherUser = chatState.otherUser
    val showOnline = otherUser?.settings?.showOnlineStatus ?: true
    val amIBlocked = otherUser?.settings?.blockedUsers?.contains(actualCurrentUserId) == true
    
    val barDisplayName = if (amIBlocked) {
        otherUser?.canonicalUsername ?: if (otherUserId.isNotBlank()) "@$otherUserId" else "Chat"
    } else {
        otherUser?.displayName?.takeIf { it.isNotBlank() }
            ?: otherUser?.canonicalUsername
            ?: if (otherUserId.isNotBlank()) "@$otherUserId" else "Loading..."
    }
    val barProfilePhoto = if (amIBlocked) null else otherUser?.profilePhoto
    val barIsOnline = if (amIBlocked) false else (otherUser?.isCurrentlyOnline == true && showOnline)
    val barIsTyping = if (amIBlocked) false else (chatState.isOtherUserTyping && showOnline)

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    val chatSetting = chatState.chatSettings[chatId]
    val currentChat = chatState.chats.firstOrNull { it.id == chatId }
    val isPrivateChat = chatSetting?.isPrivate == true || currentChat?.privateBy?.contains(actualCurrentUserId) == true
    val isChatMuted = chatSetting?.isCurrentlyMuted == true

    if (showMuteDialog) {
        AlertDialog(
            onDismissRequest = { showMuteDialog = false },
            title = { Text("Mute Notifications") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose duration to mute notifications for this chat:")
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val options = listOf(
                        "1 hour" to (1 * 3600 * 1000L),
                        "8 hours" to (8 * 3600 * 1000L),
                        "1 week" to (7 * 24 * 3600 * 1000L),
                        "Always" to -1L
                    )

                    for ((label, duration) in options) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val muteUntil = if (duration == -1L) -1L else System.currentTimeMillis() + duration
                                    viewModel.setChatMuted(chatId, true, muteUntil)
                                    android.widget.Toast.makeText(context, "Notifications muted", android.widget.Toast.LENGTH_SHORT).show()
                                    showMuteDialog = false
                                },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMuteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !isBlocked) { onNavigateToProfile(otherUserId) }
                    ) {
                        AvatarImage(
                            displayName = if (amIBlocked) (otherUser?.canonicalUsername ?: "") else (otherUser?.displayName?.ifEmpty { otherUser?.canonicalUsername } ?: ""), 
                            username = otherUser?.canonicalUsername ?: otherUserId, 
                            size = 40, 
                            profilePhoto = barProfilePhoto,
                            isOnline = barIsOnline
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isPrivateChat) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Private Chat",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = barDisplayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (isChatMuted) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsOff,
                                        contentDescription = "Muted",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (!isBlocked && !amIBlocked) {
                                if (barIsTyping) {
                                    Text("typing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                } else if (barIsOnline) {
                                    Text("Online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                } else if (!showOnline) {
                                    Text("Last seen unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    val lastSeenText = otherUser?.lastSeen?.let { formatLastSeen(it) } ?: "Offline"
                                    Text(lastSeenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPrivateChat) "Remove from Private Chats" else "Move to Private Chats") },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = if (isPrivateChat) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                        contentDescription = null
                                    ) 
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.setChatPrivate(chatId, !isPrivateChat)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isChatMuted) "Unmute Notifications" else "Mute Notifications") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isChatMuted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    if (isChatMuted) {
                                        viewModel.setChatMuted(chatId, false, 0L)
                                        android.widget.Toast.makeText(context, "Notifications unmuted", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        showMuteDialog = true
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        
    ) { padding ->
        var messageToDelete by remember { mutableStateOf<Message?>(null) }

        // 1. Delete Dialog
        if (messageToDelete != null) {
            val msg = messageToDelete!!
            val isSender = msg.senderId == actualCurrentUserId
            
            AlertDialog(
                onDismissRequest = { messageToDelete = null },
                title = { 
                    Text(
                        "Delete Message",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Choose how you want to delete this message:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.deleteMessage(chatId, msg.id, forEveryone = false)
                                    if (replyingToMessage?.id == msg.id) {
                                        replyingToMessage = null
                                    }
                                    Toast.makeText(context, "Deleted for you", Toast.LENGTH_SHORT).show()
                                    messageToDelete = null
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Delete for me",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }

                        if (isSender) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.deleteMessage(chatId, msg.id, forEveryone = true)
                                        if (replyingToMessage?.id == msg.id) {
                                            replyingToMessage = null
                                        }
                                        Toast.makeText(context, "Deleted for everyone", Toast.LENGTH_SHORT).show()
                                        messageToDelete = null
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Delete for everyone",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { messageToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 2. Pin Message Dialog
        if (messageToPin != null) {
            val targetMsg = messageToPin!!
            AlertDialog(
                onDismissRequest = { messageToPin = null },
                title = {
                    Text(
                        "Pin Message",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "How long should this message stay pinned?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.pinMessage(chatId, targetMsg.id, 24 * 3600 * 1000L)
                                    Toast.makeText(context, "Pinned for 1 day", Toast.LENGTH_SHORT).show()
                                    messageToPin = null
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "1 day",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.pinMessage(chatId, targetMsg.id, null)
                                    Toast.makeText(context, "Pinned always", Toast.LENGTH_SHORT).show()
                                    messageToPin = null
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Always",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { messageToPin = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 3. Forward Message Dialog
        if (messageToForward != null) {
            val forwardContent = messageToForward!!.content
            val otherChats = chatState.chats.filter { it.id != chatId }

            AlertDialog(
                onDismissRequest = { messageToForward = null },
                title = {
                    Text(
                        "Forward Message",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        Text(
                            text = "\"${forwardContent.take(60)}${if (forwardContent.length > 60) "..." else ""}\"",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (otherChats.isEmpty()) {
                            Text(
                                "No other conversations available to forward to.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn {
                                items(otherChats) { chat ->
                                    val participantId = chat.participants.firstOrNull { it != currentUserId } ?: ""
                                    val chatUser = chatState.userMap[participantId]
                                    val chatName = chatUser?.displayName?.takeIf { it.isNotBlank() } ?: chatUser?.canonicalUsername ?: "User"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val msg = messageToForward
                                                if (msg != null) {
                                                    viewModel.forwardMessage(chat.id, msg) {
                                                        Toast.makeText(context, "Message forwarded", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                messageToForward = null
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarImage(
                                            displayName = chatName,
                                            username = chatUser?.canonicalUsername ?: "",
                                            size = 38,
                                            profilePhoto = chatUser?.profilePhoto
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = chatName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { messageToForward = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                val visibleMessages = chatState.messages.filter { !it.deletedFor.contains(actualCurrentUserId) }
                val reversedMessages = visibleMessages.reversed()
                itemsIndexed(reversedMessages, key = { _, it -> it.id.ifEmpty { UUID.randomUUID().toString() } }) { index, message ->
                    val olderMessage = if (index < reversedMessages.size - 1) reversedMessages[index + 1] else null
                    val showDateSeparator = olderMessage == null || !isSameDay(message.timestamp, olderMessage.timestamp)
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (showDateSeparator) {
                            DateSeparator(message.timestamp)
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                        MessageBubble(
                            message = message, 
                            isCurrentUser = message.senderId == actualCurrentUserId,
                            showBlueTicks = showBlueTicks,
                            onLongClick = { showMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .widthIn(min = 170.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Reply", 
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "Reply",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = { 
                                    replyingToMessage = message
                                    showMenu = false 
                                    focusRequester.requestFocus()
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Copy", 
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = { 
                                    clipboardManager.setText(AnnotatedString(message.content))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    showMenu = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Forward", 
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Forward,
                                        contentDescription = "Forward",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = { 
                                    messageToForward = message
                                    showMenu = false 
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Delete", 
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = { 
                                    messageToDelete = message
                                    showMenu = false 
                                }
                            )
                        }
                    }
                    }
                }
            }
            
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBlocked) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("You have blocked this user", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(visible = replyingToMessage != null) {
                            if (replyingToMessage != null) {
                                val replyTarget = replyingToMessage!!
                                val isSelf = replyTarget.senderId == actualCurrentUserId
                                val replyAuthor = if (isSelf) {
                                    "yourself"
                                } else {
                                    val user = chatState.userMap[replyTarget.senderId] ?: otherUser
                                    user?.canonicalUsername?.takeIf { it.isNotBlank() }?.let { "@$it" }
                                        ?: user?.displayName?.takeIf { it.isNotBlank() }
                                        ?: "@User"
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    tonalElevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Replying to $replyAuthor",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = replyTarget.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { replyingToMessage = null },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel reply",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Message...") },
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        maxLines = 5
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(chatId, messageText.trim(), replyingToMessage)
                                messageText = ""
                                replyingToMessage = null
                            },
                            enabled = messageText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, 
                                contentDescription = "Send",
                                tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean, showBlueTicks: Boolean = true, onLongClick: () -> Unit) {
    val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    val timeString = sdf.format(java.util.Date(message.timestamp))
    
    val isDeleted = message.isDeletedForEveryone
    
    val bubbleColor = if (isDeleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = if (isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val bubbleShape = if (isCurrentUser) {
        androidx.compose.foundation.shape.RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }
    
    val displayText = if (isDeleted) {
        if (isCurrentUser) "You deleted this message." else "This message was deleted."
    } else {
        message.content
    }
    
    val fontStyle = if (isDeleted) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        contentAlignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { if (!isDeleted) onLongClick() }
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        bottom = 14.dp,
                        end = if (isCurrentUser && !isDeleted) 32.dp else 24.dp
                    )
                ) {
                    if (!isDeleted && message.isForwarded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Forward,
                                contentDescription = "Forwarded",
                                modifier = Modifier.size(13.dp),
                                tint = textColor.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Forwarded",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontSize = 11.sp
                                ),
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (!isDeleted && !message.replyToSenderName.isNullOrBlank() && !message.replyToContent.isNullOrBlank()) {
                        val replyBarColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        val replyBgColor = if (isCurrentUser) 
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f) 
                        else 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        val replyTitleColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = replyBgColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(replyBarColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.replyToSenderName ?: "",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = replyTitleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = message.replyToContent ?: "",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = textColor.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = displayText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = fontStyle)
                    )
                }
                
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.7f)
                    )
                    
                    if (isCurrentUser && !isDeleted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = when (message.status) {
                                com.example.data.model.MessageStatus.READ -> Icons.Default.DoneAll
                                com.example.data.model.MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                else -> Icons.Default.Check
                            },
                            contentDescription = "Status",
                            modifier = Modifier.size(14.dp),
                            tint = if (message.status == com.example.data.model.MessageStatus.READ && showBlueTicks) Color(0xFF4FC3F7) else textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun DateSeparator(timestamp: Long) {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    val dateString = sdf.format(java.util.Date(timestamp))
    
    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dateString,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
