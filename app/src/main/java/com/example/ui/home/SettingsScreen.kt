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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    onLogout: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val user = authState.user
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val appPreferences = remember { com.example.data.local.AppPreferences(context) }
    val showMessageContent by remember(user?.uid) { 
        appPreferences.getShowMessageContentFlow(user?.uid) 
    }.collectAsStateWithLifecycle(initialValue = user?.settings?.showMessageContent ?: true)
    
    val coroutineScope = rememberCoroutineScope()
    
    var notificationsEnabled by remember { mutableStateOf(user?.settings?.notificationsEnabled ?: true) }
    var readReceiptsEnabled by remember { mutableStateOf(user?.settings?.readReceiptsEnabled ?: true) }
    var showOnlineStatus by remember { mutableStateOf(user?.settings?.showOnlineStatus ?: true) }
    var appLockEnabled by remember { mutableStateOf(user?.settings?.appLockEnabled ?: false) }

    fun saveSettings(contentPreview: Boolean = showMessageContent) {
        viewModel.updateUserSettings(
            notificationsEnabled = notificationsEnabled,
            readReceiptsEnabled = readReceiptsEnabled,
            showOnlineStatus = showOnlineStatus,
            appLockEnabled = appLockEnabled,
            showMessageContent = contentPreview
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!authState.isLoading) {
                        showDeleteConfirmDialog = false
                    }
                },
                title = {
                    Text(
                        text = "Delete Account?",
                        modifier = Modifier.testTag("delete_account_dialog_title")
                    )
                },
                text = {
                    Text("This action is permanent.\nYour account information will be removed.\nChat history for other users will remain.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteAccount(onLogout)
                        },
                        enabled = !authState.isLoading,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_account_dialog_confirm")
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = false },
                        enabled = !authState.isLoading,
                        modifier = Modifier.testTag("delete_account_dialog_cancel")
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Branding Header
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            id = if (appLockEnabled) com.example.R.drawable.ic_vynex_logo_lock else com.example.R.drawable.ic_vynex_logo
                        ),
                        contentDescription = "Vynex Logo",
                        modifier = Modifier.size(72.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified
                    )
                }

                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                SettingSwitchRow(
                    title = "App Lock",
                    subtitle = "Require security PIN to open Vynex",
                    checked = appLockEnabled,
                    onCheckedChange = { 
                        appLockEnabled = it
                        saveSettings()
                    }
                )
                
                SettingSwitchRow(
                    title = "Show Online Status",
                    subtitle = "Let others see when you are online",
                    checked = showOnlineStatus,
                    onCheckedChange = { 
                        showOnlineStatus = it
                        saveSettings()
                    }
                )
                
                SettingSwitchRow(
                    title = "Read Receipts",
                    subtitle = "Let others know when you've read their messages",
                    checked = readReceiptsEnabled,
                    onCheckedChange = { 
                        readReceiptsEnabled = it
                        saveSettings()
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                SettingSwitchRow(
                    title = "Push Notifications",
                    subtitle = "Receive notifications for new messages",
                    checked = notificationsEnabled,
                    onCheckedChange = { 
                        notificationsEnabled = it
                        saveSettings()
                    }
                )

                SettingSwitchRow(
                    title = "Show message content",
                    subtitle = "Show sender name and message text in notifications",
                    checked = showMessageContent,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            appPreferences.setShowMessageContent(checked, user?.uid)
                        }
                        saveSettings(checked)
                    },
                    switchTag = "show_message_content_switch"
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Button(
                    onClick = onNavigateToBlockedUsers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Blocked Users")
                }

                Button(
                    onClick = onNavigateToTerms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Terms & Conditions")
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Button(
                    onClick = { viewModel.logout(onLogout) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Logout")
                }
                
                OutlinedButton(
                    onClick = { if (!authState.isLoading) showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("delete_account_button"),
                    enabled = !authState.isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Account")
                }
            }

            if (authState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchTag: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (switchTag != null) Modifier.testTag(switchTag) else Modifier
        )
    }
}
