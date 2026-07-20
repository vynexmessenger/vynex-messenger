package com.example.ui.chat
import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
    onNavigateBack: () -> Unit
) {
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    

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
        }
    }

    LaunchedEffect(chatState.messages, isAppInForeground) {
        if (isAppInForeground) {
            val unreadMsgs = chatState.messages.filter { it.receiverId == currentUserId && it.status != com.example.data.model.MessageStatus.READ }
            unreadMsgs.forEach { msg ->
                viewModel.markMessageAsRead(msg.id)
            }
        }
    }

    LaunchedEffect(chatId, otherUserId) {
        viewModel.loadChatData(chatId, otherUserId)
    }
    
    var messageText by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(displayName = chatState.otherUser?.displayName?.ifEmpty { chatState.otherUser?.username } ?: "", username = chatState.otherUser?.username ?: otherUserId, size = 40, profilePhoto = chatState.otherUser?.profilePhoto)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = chatState.otherUser?.displayName?.takeIf { it.isNotBlank() } ?: chatState.otherUser?.username ?: "Loading...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (chatState.isOtherUserTyping) {
                                Text("typing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            } else if (chatState.otherUser?.isOnline == true) {
                                Text("Online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                val lastSeenText = chatState.otherUser?.lastSeen?.let { formatLastSeen(it) } ?: "Offline"
                                Text(lastSeenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        
    ) { padding ->
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
                val visibleMessages = chatState.messages.filter { !it.isDeletedForEveryone && !it.deletedFor.contains(currentUserId) }
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
                            isCurrentUser = message.senderId == currentUserId,
                            onLongClick = { showMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                onClick = { 
                                    clipboardManager.setText(AnnotatedString(message.content))
                                    showMenu = false 
                                }
                            )
                            if (message.senderId == currentUserId) {
                                DropdownMenuItem(
                                    text = { Text("Delete for Me") },
                                    onClick = { 
                                        viewModel.deleteMessage(chatId, message.id, forEveryone = false)
                                        showMenu = false 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete for Everyone") },
                                    onClick = { 
                                        viewModel.deleteMessage(chatId, message.id, forEveryone = true)
                                        showMenu = false 
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Delete for Me") },
                                    onClick = { 
                                        viewModel.deleteMessage(chatId, message.id, forEveryone = false)
                                        showMenu = false 
                                    }
                                )
                            }
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
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
                                viewModel.sendMessage(chatId, messageText)
                                messageText = ""
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean, onLongClick: () -> Unit) {
    val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    val timeString = sdf.format(java.util.Date(message.timestamp))
    
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    val bubbleShape = if (isCurrentUser) {
        androidx.compose.foundation.shape.RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }
    
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
                        onLongClick = onLongClick
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 14.dp, end = 24.dp)
                )
                
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.7f)
                    )
                    
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = when (message.status) {
                                com.example.data.model.MessageStatus.READ -> Icons.Default.DoneAll
                                com.example.data.model.MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                else -> Icons.Default.Check
                            },
                            contentDescription = "Status",
                            modifier = Modifier.size(14.dp),
                            tint = if (message.status == com.example.data.model.MessageStatus.READ) Color(0xFF4FC3F7) else textColor.copy(alpha = 0.7f)
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
