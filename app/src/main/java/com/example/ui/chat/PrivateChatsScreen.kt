package com.example.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chat
import com.example.data.security.PrivateChatSecurityManager
import com.example.ui.components.AvatarImage
import com.example.ui.util.formatMessageTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PrivateChatsScreen(
    currentUserId: String,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (chatId: String, otherUserId: String) -> Unit
) {
    val chatState by chatViewModel.chatState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var selectedChatForOptions by remember { mutableStateOf<Chat?>(null) }
    var showMuteDialogForChat by remember { mutableStateOf<Chat?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }

    val sheetState = rememberModalBottomSheetState()

    val isUnlocked by PrivateChatSecurityManager.isUnlocked.collectAsStateWithLifecycle()
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            onNavigateBack()
        }
    }

    val handleBack = {
        PrivateChatSecurityManager.lock()
        onNavigateBack()
    }

    BackHandler {
        handleBack()
    }

    // Filter to only private chats
    val privateChats = remember(chatState.chats, chatState.chatSettings, currentUserId) {
        chatState.chats.filter { chat ->
            val isPrivateInSettings = chatState.chatSettings[chat.id]?.isPrivate == true
            val isPrivateInChat = chat.privateBy.contains(currentUserId)
            isPrivateInSettings || isPrivateInChat
        }.sortedByDescending { it.lastMessageTime }
    }

    val filteredPrivateChats = remember(privateChats, searchQuery) {
        if (searchQuery.isBlank()) {
            privateChats
        } else {
            val q = searchQuery.trim().lowercase()
            privateChats.filter { chat ->
                val otherUid = chat.participants.firstOrNull { it != currentUserId } ?: ""
                val otherUser = chatState.userMap[otherUid]
                val name = otherUser?.displayName?.lowercase() ?: ""
                val username = otherUser?.canonicalUsername?.lowercase() ?: ""
                val lastMsg = chat.lastMessage.lowercase()
                name.contains(q) || username.contains(q) || lastMsg.contains(q)
            }
        }
    }

    // Options bottom sheet
    if (selectedChatForOptions != null) {
        val chat = selectedChatForOptions!!
        val isMuted = chatState.chatSettings[chat.id]?.isCurrentlyMuted == true

        ModalBottomSheet(
            onDismissRequest = { selectedChatForOptions = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth()) {
                Text(
                    text = "Private Chat Options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("Remove from Private Chats") },
                    leadingContent = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
                    modifier = Modifier.clickable {
                        android.util.Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] REMOVE START from PrivateChatsScreen: chatId = ${chat.id}")
                        chatViewModel.setChatPrivate(chat.id, false)
                        selectedChatForOptions = null
                    }
                )

                ListItem(
                    headlineContent = { Text(if (isMuted) "Unmute Notifications" else "Mute Notifications") },
                    leadingContent = { 
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                            contentDescription = null
                        ) 
                    },
                    modifier = Modifier.clickable {
                        val targetChat = selectedChatForOptions
                        selectedChatForOptions = null
                        if (isMuted) {
                            chatViewModel.setChatMuted(targetChat!!.id, false, 0L)
                        } else {
                            showMuteDialogForChat = targetChat
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        chatToDelete = chat
                        selectedChatForOptions = null
                        showDeleteConfirmDialog = true
                    }
                )
            }
        }
    }

    // Mute duration dialog
    if (showMuteDialogForChat != null) {
        val targetChat = showMuteDialogForChat!!
        AlertDialog(
            onDismissRequest = { showMuteDialogForChat = null },
            title = { Text("Mute Notifications") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how long notifications should be muted for this private chat:")
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
                                    chatViewModel.setChatMuted(targetChat.id, true, muteUntil)
                                    showMuteDialogForChat = null
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
                TextButton(onClick = { showMuteDialogForChat = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog && chatToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                chatToDelete = null
            },
            title = { Text("Delete Chat?") },
            text = { Text("Are you sure you want to delete this conversation?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.deleteChat(chatToDelete!!.id, forEveryone = true)
                        showDeleteConfirmDialog = false
                        chatToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    chatToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Chats")
                    }
                },
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search private chats...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                    }
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Private Chats",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = handleBack) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock now")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (filteredPrivateChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "No Private Chats",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching private chats" else "No Private Chats Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try a different search query." 
                            else "Long press any chat on your main chat list and select 'Move to Private Chats' to protect it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(filteredPrivateChats, key = { it.id }) { chat ->
                    val otherUserId = chat.participants.firstOrNull { it != currentUserId } ?: ""
                    val otherUser = chatState.userMap[otherUserId]
                    val isMuted = chatState.chatSettings[chat.id]?.isCurrentlyMuted == true
                    val unreadCount = chat.unreadCounts[currentUserId] ?: 0

                    PrivateChatListItem(
                        chat = chat,
                        otherUser = otherUser,
                        otherUserId = otherUserId,
                        isMuted = isMuted,
                        unreadCount = unreadCount,
                        onClick = {
                            onNavigateToChat(chat.id, otherUserId)
                        },
                        onLongClick = {
                            selectedChatForOptions = chat
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrivateChatListItem(
    chat: Chat,
    otherUser: com.example.data.model.User?,
    otherUserId: String,
    isMuted: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val showOnline = otherUser?.settings?.showOnlineStatus ?: true
    val isOnline = otherUser?.isCurrentlyOnline == true && showOnline
    val displayName = otherUser?.displayName?.takeIf { it.isNotBlank() } ?: otherUser?.canonicalUsername ?: otherUserId

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
        AvatarImage(
            displayName = displayName,
            username = otherUser?.canonicalUsername ?: otherUserId,
            size = 52,
            profilePhoto = otherUser?.profilePhoto,
            isOnline = isOnline
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Private",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (chat.lastMessageTime > 0L) {
                    Text(
                        text = formatMessageTime(chat.lastMessageTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessage.ifEmpty { "No messages yet" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isMuted) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
