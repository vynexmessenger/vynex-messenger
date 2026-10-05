package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.AvatarImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatState.collectAsStateWithLifecycle()
    
    val blockedUserIds = authState.user?.settings?.blockedUsers ?: emptyList()

    LaunchedEffect(blockedUserIds) {
        // Just fetch them by searching or we might already have them. 
        // We will just show UID if not loaded, but let's try to load them if needed.
        blockedUserIds.forEach { uid ->
            if (!chatState.userMap.containsKey(uid)) {
                chatViewModel.startOrGetChat(uid) {} // this fetches the user into the map silently 
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Users") },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (blockedUserIds.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No blocked users", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(blockedUserIds) { uid ->
                        val user = chatState.userMap[uid]
                        var showUnblockDialog by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showUnblockDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarImage(
                                displayName = user?.displayName?.ifEmpty { user.canonicalUsername } ?: "Loading...",
                                username = user?.canonicalUsername ?: uid,
                                size = 48,
                                profilePhoto = user?.profilePhoto ?: ""
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user?.displayName?.ifEmpty { user.canonicalUsername } ?: "Loading...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = user?.canonicalUsername ?: "...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showUnblockDialog = true }) {
                                Text("Unblock")
                            }
                        }

                        if (showUnblockDialog) {
                            AlertDialog(
                                onDismissRequest = { showUnblockDialog = false },
                                title = { Text("Unblock this user?") },
                                text = { Text("They will be able to message you and see your profile.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showUnblockDialog = false
                                        chatViewModel.unblockUser(uid)
                                    }) {
                                        Text("Unblock")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showUnblockDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
