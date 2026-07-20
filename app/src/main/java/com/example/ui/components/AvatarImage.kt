package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs

@Composable
fun AvatarImage(displayName: String = "", username: String = "", size: Int = 48, profilePhoto: String? = null, isOnline: Boolean = false) {
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        if (!profilePhoto.isNullOrBlank()) {
            AsyncImage(
                model = profilePhoto,
                contentDescription = "Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
        } else {
            val initial = when {
                displayName.isNotBlank() -> {
                    displayName.firstOrNull { it.isLetter() }?.uppercase() ?: "?"
                }
                username.isNotBlank() -> {
                    username.replace("@", "").firstOrNull { it.isLetter() }?.uppercase() ?: "?"
                }
                else -> "?"
            }
            
            val nameToHash = if (displayName.isNotBlank()) displayName else username
            val hash = abs(nameToHash.hashCode())
            
            val colors = listOf(
                Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), 
                Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6), 
                Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC), 
                Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFF8A65),
                Color(0xFFD4E157), Color(0xFFFFD54F), Color(0xFFFFB74D)
            )
            
            val bgColor = if (nameToHash.isNotEmpty()) colors[hash % colors.size] else Color.Gray
            
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size * 0.4).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size((size * 0.28).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}
