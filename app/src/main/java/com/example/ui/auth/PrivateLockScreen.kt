package com.example.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.security.PrivateChatSecurityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateLockScreen(
    currentUserId: String,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { PrivateChatSecurityManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var hasPin by remember { mutableStateOf<Boolean?>(null) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var accountPinInput by remember { mutableStateOf("") }
    var accountPinError by remember { mutableStateOf<String?>(null) }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(currentUserId) {
        hasPin = securityManager.hasPrivatePin(currentUserId)
    }

    // Auto-verify / auto-confirm on 4 digits
    LaunchedEffect(pin, isConfirming, confirmPin) {
        if (hasPin == false) {
            // First-time setup mode
            if (!isConfirming && pin.length == 4) {
                delay(150)
                isConfirming = true
            } else if (isConfirming && confirmPin.length == 4) {
                delay(150)
                if (pin == confirmPin) {
                    val saved = securityManager.savePrivatePin(currentUserId, pin)
                    if (saved) {
                        onUnlockSuccess()
                    } else {
                        errorMessage = "Failed to save PIN"
                        pin = ""
                        confirmPin = ""
                        isConfirming = false
                    }
                } else {
                    errorMessage = "PINs do not match. Try again."
                    pin = ""
                    confirmPin = ""
                    isConfirming = false
                }
            }
        } else if (hasPin == true) {
            // Unlock mode
            if (pin.length == 4) {
                delay(100)
                val isValid = securityManager.verifyPrivatePin(currentUserId, pin)
                if (isValid) {
                    onUnlockSuccess()
                } else {
                    errorMessage = "Incorrect PIN"
                    pin = ""
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { 
                showResetDialog = false
                accountPinInput = ""
                accountPinError = null
            },
            title = { Text("Reset Private PIN") },
            text = {
                Column {
                    Text("Enter your Vynex account Security PIN to reset your Private Chats PIN.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = accountPinInput,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                accountPinInput = it
                            }
                        },
                        label = { Text("Account Security PIN") },
                        singleLine = true,
                        isError = accountPinError != null,
                        supportingText = accountPinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (authViewModel.verifyPin(accountPinInput)) {
                            coroutineScope.launch {
                                securityManager.resetPrivatePin(currentUserId)
                                hasPin = false
                                pin = ""
                                confirmPin = ""
                                isConfirming = false
                                showResetDialog = false
                                accountPinInput = ""
                                accountPinError = null
                            }
                        } else {
                            accountPinError = "Incorrect Security PIN"
                        }
                    }
                ) {
                    Text("Verify & Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showResetDialog = false
                    accountPinInput = ""
                    accountPinError = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    val activePin = if (isConfirming) confirmPin else pin

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        if (hasPin == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_vynex_logo_lock),
                    contentDescription = "Private Chats Lock Logo",
                    modifier = Modifier.size(80.dp),
                    tint = Color.Unspecified
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Private Chats",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                val subtitle = when {
                    hasPin == false && !isConfirming -> "Create a 4-digit Private PIN"
                    hasPin == false && isConfirming -> "Confirm your 4-digit Private PIN"
                    else -> "Enter your Private PIN"
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN dots matching App Lock screen
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < activePin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) Color.White else Color.White.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Numeric Keypad matching App Lock screen
                NumericKeypad(
                    onNumberClick = { num ->
                        errorMessage = null
                        if (isConfirming) {
                            if (confirmPin.length < 4) confirmPin += num
                        } else {
                            if (pin.length < 4) pin += num
                        }
                    },
                    onBackspaceClick = {
                        errorMessage = null
                        if (isConfirming) {
                            if (confirmPin.isNotEmpty()) {
                                confirmPin = confirmPin.dropLast(1)
                            } else {
                                isConfirming = false
                            }
                        } else {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                        }
                    }
                )

                if (hasPin == true) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showResetDialog = true }) {
                        Text(
                            text = "Forgot / Reset PIN",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
