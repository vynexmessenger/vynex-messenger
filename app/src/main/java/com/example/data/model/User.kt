package com.example.data.model

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val passwordHash: String = "",
    val dateOfBirth: String = "",
    val securityPin: String = "",
    val displayName: String = "",
    val bio: String = "",
    val profilePhoto: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val activeSessions: Map<String, Long> = emptyMap(),
    val fcmToken: String = "",
    val settings: UserSettings = UserSettings()
) {
    val canonicalUsername: String
        get() {
            val clean = username.trim().trimStart('@')
            return if (clean.isEmpty()) "" else "@$clean"
        }

    /**
     * Determines whether the user is actively online.
     * Evaluates true only if isOnline flag is true AND there is a verified recent heartbeat within PRESENCE_TIMEOUT_MS.
     */
    val isCurrentlyOnline: Boolean
        get() {
            if (!isOnline) return false
            val now = System.currentTimeMillis()
            // If active sessions exist, check if at least one session updated recently
            if (activeSessions.isNotEmpty()) {
                val hasActiveSession = activeSessions.values.any { timestamp ->
                    (now - timestamp) < PRESENCE_TIMEOUT_MS
                }
                if (hasActiveSession) return true
            }
            // Fallback to lastSeen timestamp
            return lastSeen > 0L && (now - lastSeen) < PRESENCE_TIMEOUT_MS
        }

    companion object {
        const val PRESENCE_TIMEOUT_MS = 60_000L // 60 seconds timeout
        const val HEARTBEAT_INTERVAL_MS = 25_000L // 25 seconds heartbeat
    }
}

data class UserSettings(
    val notificationsEnabled: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val appLockEnabled: Boolean = false,
    val showMessageContent: Boolean = true,
    val blockedUsers: List<String> = emptyList()
)
