package com.example.data.model

data class ChatSetting(
    val chatId: String = "",
    val isPrivate: Boolean = false,
    val isMuted: Boolean = false,
    val muteUntil: Long = 0L, // 0L: unmuted, -1L: always muted, > 0L: timestamp until which chat is muted
    val updatedAt: Long = 0L
) {
    val isCurrentlyMuted: Boolean
        get() {
            if (!isMuted) return false
            if (muteUntil == -1L || muteUntil == 0L) return isMuted
            return System.currentTimeMillis() < muteUntil
        }
}
