package com.example.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chat
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.AvatarImage
import com.example.ui.util.formatMessageTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToPrivateChats: () -> Unit = {},
    onNavigateToChat: (chatId: String, otherUserId: String) -> Unit
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatState.collectAsStateWithLifecycle()
    val user = authState.user
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Unread", "Archived")
    
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }
    var showMoveToPrivateConfirmDialog by remember { mutableStateOf(false) }
    var chatToMoveToPrivate by remember { mutableStateOf<Chat?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    if (chatToDelete != null && !showDeleteConfirmDialog) {
        val currentUserId = user?.uid ?: ""
        val targetChat = chatToDelete!!
        val isPrivate = chatState.chatSettings[targetChat.id]?.isPrivate == true || targetChat.privateBy.contains(currentUserId)

        ModalBottomSheet(
            onDismissRequest = { chatToDelete = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth()) {
                Text("Chat Options", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
                
                val isPinned = targetChat.pinnedBy.contains(currentUserId)
                ListItem(
                    headlineContent = { Text(if (isPinned) "Unpin Chat" else "Pin Chat") },
                    leadingContent = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    modifier = Modifier.clickable {
                        chatViewModel.togglePinChat(targetChat.id)
                        chatToDelete = null
                    }
                )
                
                val isArchived = targetChat.archivedBy.contains(currentUserId)
                ListItem(
                    headlineContent = { Text(if (isArchived) "Unarchive Chat" else "Archive Chat") },
                    leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                    modifier = Modifier.clickable {
                        chatViewModel.toggleArchiveChat(targetChat.id)
                        chatToDelete = null
                    }
                )

                ListItem(
                    headlineContent = { Text(if (isPrivate) "Remove from Private Chats" else "Move to Private Chats") },
                    leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    modifier = Modifier.clickable {
                        chatToDelete = null
                        if (!isPrivate) {
                            chatToMoveToPrivate = targetChat
                            showMoveToPrivateConfirmDialog = true
                        } else {
                            android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] REMOVE START")
                            android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] currentUserId = $currentUserId")
                            android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] chatId = ${targetChat.id}")
                            chatViewModel.setChatPrivate(targetChat.id, false)
                        }
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        showDeleteConfirmDialog = true
                    }
                )
            }
        }
    }

    if (showMoveToPrivateConfirmDialog && chatToMoveToPrivate != null) {
        val targetChat = chatToMoveToPrivate!!
        AlertDialog(
            onDismissRequest = {
                showMoveToPrivateConfirmDialog = false
                chatToMoveToPrivate = null
            },
            title = { Text("Move to Private Chats?") },
            text = { Text("This chat will be hidden from the main chat list and protected by your Private PIN.") },
            confirmButton = {
                Button(
                    onClick = {
                        val currentUserId = user?.uid ?: ""
                        val chatId = targetChat.id
                        val existingPrivateState = chatState.chatSettings[chatId]?.isPrivate ?: targetChat.privateBy.contains(currentUserId)
                        android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] START")
                        android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] currentUserId = $currentUserId")
                        android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] chatId = $chatId")
                        android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] chat object = $targetChat")
                        android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] existing private state = $existingPrivateState")
                        chatViewModel.setChatPrivate(chatId, true)
                        showMoveToPrivateConfirmDialog = false
                        chatToMoveToPrivate = null
                    }
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMoveToPrivateConfirmDialog = false
                        chatToMoveToPrivate = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog && chatToDelete != null) {
        Dialog(onDismissRequest = { showDeleteConfirmDialog = false; chatToDelete = null }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text("Delete Chat", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Are you sure you want to delete this chat?")
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    TextButton(
                        onClick = {
                            chatViewModel.deleteChat(chatToDelete!!.id, forEveryone = false)
                            showDeleteConfirmDialog = false
                            chatToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for Me", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            chatViewModel.deleteChat(chatToDelete!!.id, forEveryone = true)
                            showDeleteConfirmDialog = false
                            chatToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for Everyone", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showDeleteConfirmDialog = false; chatToDelete = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Vynex", 
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search users")
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            val currentUserId = user?.uid ?: ""
            val nonPrivateChats = chatState.chats.filter { chat ->
                val isPrivateInSettings = chatState.chatSettings[chat.id]?.isPrivate == true
                val isPrivateInChat = chat.privateBy.contains(currentUserId)
                !isPrivateInSettings && !isPrivateInChat
            }
            val displayChats = when (selectedTabIndex) {
                1 -> nonPrivateChats.filter { (it.unreadCounts[currentUserId] ?: 0) > 0 && !it.archivedBy.contains(currentUserId) }
                2 -> nonPrivateChats.filter { it.archivedBy.contains(currentUserId) }
                else -> nonPrivateChats.filter { !it.archivedBy.contains(currentUserId) }
            }.sortedWith(
                compareByDescending<com.example.data.model.Chat> { it.pinnedBy.contains(currentUserId) }
                .thenByDescending { it.lastMessageTime }
            )

            val listState = rememberLazyListState()
            val pullOffset = remember { Animatable(0f) }
            val coroutineScope = rememberCoroutineScope()
            val density = LocalDensity.current
            val thresholdPx = with(density) { 85.dp.toPx() }
            val maxPullPx = with(density) { 130.dp.toPx() }

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // User pulling down while at the top of the chat list
                        if (available.y > 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                            val newOffset = (pullOffset.value + available.y * 0.45f).coerceIn(0f, maxPullPx)
                            coroutineScope.launch { pullOffset.snapTo(newOffset) }
                            return Offset(0f, available.y)
                        }
                        // User pushing back up while pullOffset is active
                        if (available.y < 0 && pullOffset.value > 0f) {
                            val newOffset = (pullOffset.value + available.y * 0.45f).coerceIn(0f, maxPullPx)
                            coroutineScope.launch { pullOffset.snapTo(newOffset) }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (pullOffset.value >= thresholdPx) {
                            coroutineScope.launch {
                                pullOffset.animateTo(0f)
                                onNavigateToPrivateChats()
                            }
                            return available
                        } else if (pullOffset.value > 0f) {
                            coroutineScope.launch {
                                pullOffset.animateTo(0f)
                            }
                            return available
                        }
                        return Velocity.Zero
                    }
                }
            }

            // Pull-down Private Chats reveal header
            if (pullOffset.value > 6f) {
                val progress = (pullOffset.value / thresholdPx).coerceIn(0f, 1f)
                val isPastThreshold = pullOffset.value >= thresholdPx
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { pullOffset.value.toDp() })
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f * progress)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha(progress)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Private Chats",
                            tint = if (isPastThreshold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp).scale(0.85f + 0.25f * progress)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Private Chats",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isPastThreshold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isPastThreshold) "Release to open" else "Pull down to open",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (chatState.isLoading && chatState.chats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (displayChats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "No chats",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (chatState.chats.isEmpty()) "No chats yet" else "No chats in ${tabs[selectedTabIndex]}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (selectedTabIndex == 0) {
                            Text(
                                text = "Tap the search icon to find users and start messaging.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    items(displayChats, key = { it.id }) { chat ->
                        val otherUserId = chat.participants.firstOrNull { it != user?.uid } ?: ""
                        val otherUser = chatState.userMap[otherUserId]
                        val isMuted = chatState.chatSettings[chat.id]?.isCurrentlyMuted == true
                        ChatListItem(
                            chat = chat,
                            otherUser = otherUser,
                            otherUserId = otherUserId,
                            isPinned = chat.pinnedBy.contains(currentUserId),
                            isMuted = isMuted,
                            unreadCount = chat.unreadCounts[currentUserId] ?: 0,
                            onClick = {
                                onNavigateToChat(chat.id, otherUserId)
                            },
                            onLongClick = {
                                chatToDelete = chat
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: Chat, 
    otherUser: com.example.data.model.User?, 
    otherUserId: String,
    isPinned: Boolean, 
    isMuted: Boolean = false,
    unreadCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val amIBlocked = otherUser?.settings?.blockedUsers?.contains(currentUserId) == true
    val showOnline = otherUser?.settings?.showOnlineStatus ?: true
    
    val timeString = formatMessageTime(chat.lastMessageTime)

    val displayName = if (amIBlocked) {
        otherUser?.canonicalUsername ?: "User: ${otherUserId.take(8)}"
    } else {
        otherUser?.displayName?.takeIf { it.isNotBlank() } ?: otherUser?.canonicalUsername ?: "User: ${otherUserId.take(8)}"
    }
    val username = otherUser?.canonicalUsername ?: ""
    val profilePhoto = if (amIBlocked) null else otherUser?.profilePhoto
    val isOnline = if (amIBlocked) false else (otherUser?.isCurrentlyOnline ?: false) && showOnline
    val isTyping = if (amIBlocked) false else (chat.typing[otherUserId] == true) && showOnline
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(displayName = displayName, username = username, size = 56, profilePhoto = profilePhoto, isOnline = isOnline)
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isMuted) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = "Muted",
                            modifier = Modifier.size(14.dp).padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (timeString.isNotEmpty()) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayLastMessage = if (chat.lastMessage == "This message was deleted.") {
                    if (chat.lastSenderId == currentUserId) "You deleted this message." else "This message was deleted."
                } else {
                    chat.lastMessage.ifEmpty { "New chat started" }
                }
                Text(
                    text = if (isTyping) "Typing..." else displayLastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
