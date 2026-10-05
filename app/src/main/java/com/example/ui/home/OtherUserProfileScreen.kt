package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.AvatarImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherUserProfileScreen(
    userId: String,
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit
) {
    val chatState by chatViewModel.chatState.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    
    val user = if (chatState.otherUser?.uid == userId) chatState.otherUser else chatState.userMap[userId]
    val currentUserId = authState.user?.uid
    val isBlockedByMe = authState.user?.settings?.blockedUsers?.contains(userId) == true
    val amIBlocked = user?.settings?.blockedUsers?.contains(currentUserId) == true
    
    var showBlockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        if (!chatState.userMap.containsKey(userId)) {
            // It will be loaded via ChatViewModel or we can rely on it being there
            // Usually it's in the map if we came from chat
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
        if (amIBlocked) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("User not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (user != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var showViewer by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier.clickable(enabled = !user.profilePhoto.isNullOrEmpty()) {
                        showViewer = true
                    }
                ) {
                    AvatarImage(
                        displayName = user.displayName.ifEmpty { user.canonicalUsername },
                        username = user.canonicalUsername,
                        size = 120,
                        profilePhoto = user.profilePhoto
                    )
                }
                
                if (showViewer && !user.profilePhoto.isNullOrEmpty()) {
                    com.example.ui.components.MediaViewer(
                        imageUrl = user.profilePhoto!!,
                        onDismiss = { showViewer = false }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = user.displayName.ifEmpty { user.canonicalUsername },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Text(
                    text = user.canonicalUsername,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (user.bio.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Bio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = user.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                if (isBlockedByMe) {
                    Button(
                        onClick = {
                            chatViewModel.unblockUser(userId)
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unblock User")
                    }
                } else {
                    Button(
                        onClick = { showBlockDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Block User")
                    }
                }
            }
            
            if (showBlockDialog) {
                AlertDialog(
                    onDismissRequest = { showBlockDialog = false },
                    title = { Text("Block this user?") },
                    text = { Text("They won't be able to message you or see your profile information.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBlockDialog = false
                                chatViewModel.blockUser(userId)
                                onNavigateBack()
                            }
                        ) {
                            Text("Block", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBlockDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
