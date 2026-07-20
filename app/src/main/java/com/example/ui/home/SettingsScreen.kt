package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val user = authState.user
    
    var notificationsEnabled by remember { mutableStateOf(user?.settings?.notificationsEnabled ?: true) }
    var readReceiptsEnabled by remember { mutableStateOf(user?.settings?.readReceiptsEnabled ?: true) }
    var showOnlineStatus by remember { mutableStateOf(user?.settings?.showOnlineStatus ?: true) }
    var appLockEnabled by remember { mutableStateOf(user?.settings?.appLockEnabled ?: false) }

    fun saveSettings() {
        viewModel.updateUserSettings(
            notificationsEnabled = notificationsEnabled,
            readReceiptsEnabled = readReceiptsEnabled,
            showOnlineStatus = showOnlineStatus,
            appLockEnabled = appLockEnabled
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
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
                onClick = { viewModel.deleteAccount(onLogout) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete Account")
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
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
            onCheckedChange = onCheckedChange
        )
    }
}
