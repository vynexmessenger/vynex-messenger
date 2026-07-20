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
    val fcmToken: String = "",
    val settings: UserSettings = UserSettings()
)

data class UserSettings(
    val notificationsEnabled: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val appLockEnabled: Boolean = false,
    val blockedUsers: List<String> = emptyList()
)
