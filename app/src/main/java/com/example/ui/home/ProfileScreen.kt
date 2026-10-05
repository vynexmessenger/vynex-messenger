package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.AuthViewModel
import com.example.ui.components.AvatarImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val user = authState.user
    
    var isEditing by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var profilePhoto by remember { mutableStateOf("") }

    LaunchedEffect(user, isEditing) {
        if (!isEditing && user != null) {
            displayName = user.displayName
            bio = user.bio
            profilePhoto = user.profilePhoto
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Profile") },
                actions = {
                    if (isEditing) {
                        TextButton(onClick = { 
                            // Save logic (we can just update local state or need a repository method, 
                            // let's assume we need to update it via a new method in AuthViewModel)
                            // For now, let's just toggle back, user object update should happen in ViewModel
                            viewModel.updateUserProfile(displayName, bio, profilePhoto); isEditing = false 
                        }) {
                            Text("Save", color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        TextButton(onClick = { isEditing = true }) {
                            Text("Edit", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (user != null) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val joinDate = if (user.createdAt > 0) sdf.format(Date(user.createdAt)) else "Unknown"

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Avatar
                var showPhotoDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            val baos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                            val imageBytes = baos.toByteArray()
                            viewModel.uploadProfilePhoto(imageBytes) { url ->
                                profilePhoto = url
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                var showViewer by remember { mutableStateOf(false) }

                if (showPhotoDialog) {
                    AlertDialog(
                        onDismissRequest = { showPhotoDialog = false },
                        title = { Text("Profile Photo") },
                        text = {
                            Column {
                                Text("Choose an option for your profile photo.")
                                if (!user.profilePhoto.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    TextButton(
                                        onClick = {
                                            showPhotoDialog = false
                                            showViewer = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("View Photo", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showPhotoDialog = false
                                launcher.launch("image/*")
                            }) {
                                Text("Choose from Gallery")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showPhotoDialog = false
                                profilePhoto = ""
                                viewModel.updateUserProfile(displayName, bio, "")
                            }) {
                                Text("Remove Photo", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }

                Box(modifier = Modifier.clickable { showPhotoDialog = true }) {
                    AvatarImage(displayName = user.displayName.ifEmpty { user.canonicalUsername }, username = user.canonicalUsername, size = 120, profilePhoto = user.profilePhoto)
                }
                
                if (showViewer && !user.profilePhoto.isNullOrEmpty()) {
                    com.example.ui.components.MediaViewer(
                        imageUrl = user.profilePhoto!!,
                        onDismiss = { showViewer = false }
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                
                // 2. Display Name (Editable)
                OutlinedTextField(
                    value = if (isEditing) displayName else user.displayName.ifEmpty { "No display name" },
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    readOnly = !isEditing,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    enabled = isEditing
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 3. Username (Locked)
                OutlinedTextField(
                    value = user.canonicalUsername,
                    onValueChange = { },
                    label = { Text("Username") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // 4. Joined Date (Locked)
                OutlinedTextField(
                    value = joinDate,
                    onValueChange = { },
                    label = { Text("Joined Date") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                
                
                // 6. Bio (Editable)
                OutlinedTextField(
                    value = if (isEditing) bio else user.bio.ifEmpty { "No bio provided" },
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    readOnly = !isEditing,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    enabled = isEditing
                )
            }
        }
    }
}
