package com.example.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chat
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.AvatarImage
import com.example.ui.util.formatMessageTime
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
    onNavigateToChat: (chatId: String, otherUserId: String) -> Unit
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatState.collectAsStateWithLifecycle()
    val user = authState.user
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Unread", "Archived")
    
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    if (chatToDelete != null && !showDeleteConfirmDialog) {
        val currentUserId = user?.uid ?: ""
        ModalBottomSheet(
            onDismissRequest = { chatToDelete = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth()) {
                Text("Chat Options", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
                
                val isPinned = chatToDelete?.pinnedBy?.contains(currentUserId) == true
                ListItem(
                    headlineContent = { Text(if (isPinned) "Unpin Chat" else "Pin Chat") },
                    leadingContent = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    modifier = Modifier.clickable {
                        chatViewModel.togglePinChat(chatToDelete!!.id)
                        chatToDelete = null
                    }
                )
                
                val isArchived = chatToDelete?.archivedBy?.contains(currentUserId) == true
                ListItem(
                    headlineContent = { Text(if (isArchived) "Unarchive Chat" else "Archive Chat") },
                    leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) }, // Mute could use NotificationsOff, for now just using an icon
                    modifier = Modifier.clickable {
                        chatViewModel.toggleArchiveChat(chatToDelete!!.id)
                        chatToDelete = null
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
            val displayChats = when (selectedTabIndex) {
                1 -> chatState.chats.filter { (it.unreadCounts[currentUserId] ?: 0) > 0 && !it.archivedBy.contains(currentUserId) }
                2 -> chatState.chats.filter { it.archivedBy.contains(currentUserId) }
                else -> chatState.chats.filter { !it.archivedBy.contains(currentUserId) }
            }.sortedWith(
                compareByDescending<com.example.data.model.Chat> { it.pinnedBy.contains(currentUserId) }
                .thenByDescending { it.lastMessageTime }
            )

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
                        .weight(1f),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(displayChats, key = { it.id }) { chat ->
                        val otherUserId = chat.participants.firstOrNull { it != user?.uid } ?: ""
                        val otherUser = chatState.userMap[otherUserId]
                        ChatListItem(
                            chat = chat,
                            otherUser = otherUser,
                            otherUserId = otherUserId,
                            isPinned = chat.pinnedBy.contains(currentUserId),
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
    unreadCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    
    val timeString = formatMessageTime(chat.lastMessageTime)

    val displayName = otherUser?.displayName?.takeIf { it.isNotBlank() } ?: otherUser?.username ?: "User: ${otherUserId.take(8)}"
    val username = otherUser?.username ?: ""
    
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
        AvatarImage(displayName = displayName, username = username, size = 56, profilePhoto = otherUser?.profilePhoto, isOnline = otherUser?.isOnline ?: false)
        
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
                Text(
                    text = if (chat.typing[otherUserId] == true) "Typing..." else chat.lastMessage.ifEmpty { "New chat started" },
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
