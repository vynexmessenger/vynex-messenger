package com.example.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    viewModel: AuthViewModel,
    onUnlockSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    // Auto-submit when PIN length reaches 4
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(100)
            if (viewModel.verifyPin(pin)) {
                onUnlockSuccess()
            } else {
                errorMsg = "Incorrect PIN"
                pin = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13)) // Deep dark premium background
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_vynex_logo),
                contentDescription = "Vynex Logo",
                modifier = Modifier.size(80.dp),
                tint = androidx.compose.ui.graphics.Color.Unspecified
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Vynex Messenger",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Text(
                text = "Enter Security PIN",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Four PIN circles at the top
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < pin.length
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
            
            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bubble-style numeric keypad
            NumericKeypad(
                onNumberClick = { num ->
                    if (pin.length < 4) {
                        pin += num
                        errorMsg = null
                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        errorMsg = null
                    }
                }
            )
        }
    }
}

@Composable
fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit
) {
    val buttonModifier = Modifier
        .padding(8.dp)
        .size(72.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center) {
            KeypadButton("1", buttonModifier) { onNumberClick("1") }
            KeypadButton("2", buttonModifier) { onNumberClick("2") }
            KeypadButton("3", buttonModifier) { onNumberClick("3") }
        }
        Row(horizontalArrangement = Arrangement.Center) {
            KeypadButton("4", buttonModifier) { onNumberClick("4") }
            KeypadButton("5", buttonModifier) { onNumberClick("5") }
            KeypadButton("6", buttonModifier) { onNumberClick("6") }
        }
        Row(horizontalArrangement = Arrangement.Center) {
            KeypadButton("7", buttonModifier) { onNumberClick("7") }
            KeypadButton("8", buttonModifier) { onNumberClick("8") }
            KeypadButton("9", buttonModifier) { onNumberClick("9") }
        }
        Row(horizontalArrangement = Arrangement.Center) {
            Spacer(modifier = buttonModifier)
            KeypadButton("0", buttonModifier) { onNumberClick("0") }
            Box(
                modifier = buttonModifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBackspaceClick
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun KeypadButton(
    number: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, label = "scale")
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)) // Glassmorphism effect
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.2f
            ),
            color = Color.White
        )
    }
}
