package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {
    companion object {
        val LOGGED_IN_UID = stringPreferencesKey("logged_in_uid")
        val APP_LOCKED = booleanPreferencesKey("app_locked")
        val SHOW_MESSAGE_CONTENT = booleanPreferencesKey("show_message_content")
        val UNREAD_NOTIFICATION_COUNT = intPreferencesKey("unread_notification_count")
        val LAST_NOTIFICATION_CHAT_ID = stringPreferencesKey("last_notification_chat_id")
        val MUTED_CHATS_DEVICE = stringSetPreferencesKey("muted_chats_device")
    }

    val loggedInUidFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LOGGED_IN_UID]
    }

    val showMessageContentFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val uid = preferences[LOGGED_IN_UID]
        if (uid != null && preferences.contains(showMessageContentKey(uid))) {
            preferences[showMessageContentKey(uid)] ?: true
        } else {
            preferences[SHOW_MESSAGE_CONTENT] ?: true
        }
    }

    fun getShowMessageContentFlow(currentUid: String? = null): Flow<Boolean> = context.dataStore.data.map { preferences ->
        val uid = currentUid ?: preferences[LOGGED_IN_UID]
        if (uid != null && preferences.contains(showMessageContentKey(uid))) {
            preferences[showMessageContentKey(uid)] ?: true
        } else {
            preferences[SHOW_MESSAGE_CONTENT] ?: true
        }
    }

    val unreadNotificationCountFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[UNREAD_NOTIFICATION_COUNT] ?: 0
    }

    suspend fun setShowMessageContent(enabled: Boolean, currentUid: String? = null) {
        android.util.Log.d("NOTIFICATION_SETTINGS", "showMessageContent = $enabled")
        val uid = currentUid ?: context.dataStore.data.first()[LOGGED_IN_UID]
        context.dataStore.edit { preferences ->
            preferences[SHOW_MESSAGE_CONTENT] = enabled
            if (uid != null) {
                preferences[showMessageContentKey(uid)] = enabled
            }
        }
    }

    suspend fun getShowMessageContent(currentUid: String? = null): Boolean {
        val prefs = context.dataStore.data.first()
        val uid = currentUid ?: prefs[LOGGED_IN_UID]
        return if (uid != null && prefs.contains(showMessageContentKey(uid))) {
            prefs[showMessageContentKey(uid)] ?: true
        } else {
            prefs[SHOW_MESSAGE_CONTENT] ?: true
        }
    }

    suspend fun incrementNotificationCount(chatId: String?): Int {
        var newCount = 1
        context.dataStore.edit { preferences ->
            val current = preferences[UNREAD_NOTIFICATION_COUNT] ?: 0
            newCount = current + 1
            preferences[UNREAD_NOTIFICATION_COUNT] = newCount
            if (chatId != null) {
                val existingChatId = preferences[LAST_NOTIFICATION_CHAT_ID]
                if (current == 0 || existingChatId == chatId) {
                    preferences[LAST_NOTIFICATION_CHAT_ID] = chatId
                } else {
                    preferences[LAST_NOTIFICATION_CHAT_ID] = ""
                }
            }
        }
        return newCount
    }

    suspend fun resetNotificationCount() {
        context.dataStore.edit { preferences ->
            preferences[UNREAD_NOTIFICATION_COUNT] = 0
            preferences[LAST_NOTIFICATION_CHAT_ID] = ""
        }
    }

    suspend fun getLastNotificationChatId(): String? {
        val chatId = context.dataStore.data.first()[LAST_NOTIFICATION_CHAT_ID]
        return if (chatId.isNullOrEmpty()) null else chatId
    }

    private fun showMessageContentKey(uid: String) = booleanPreferencesKey("show_message_content_$uid")
    private fun mutedChatsKey(uid: String) = stringSetPreferencesKey("muted_chats_$uid")
    private fun privateChatsKey(uid: String) = stringSetPreferencesKey("private_chats_$uid")
    private fun muteUntilKey(uid: String, chatId: String) = longPreferencesKey("mute_until_${uid}_$chatId")
    private fun muteUntilDeviceKey(chatId: String) = longPreferencesKey("mute_until_device_$chatId")

    suspend fun getChatMuteUntil(chatId: String, currentUid: String? = null): Long {
        val prefs = context.dataStore.data.first()
        val uid = currentUid ?: prefs[LOGGED_IN_UID]
        if (uid != null && prefs.contains(muteUntilKey(uid, chatId))) {
            return prefs[muteUntilKey(uid, chatId)] ?: 0L
        }
        return prefs[muteUntilDeviceKey(chatId)] ?: 0L
    }

    suspend fun isChatMuted(chatId: String, currentUid: String? = null): Boolean {
        val prefs = context.dataStore.data.first()
        val uid = currentUid ?: prefs[LOGGED_IN_UID]
        val now = System.currentTimeMillis()

        // 1. Strict user isolation if uid is known
        if (uid != null) {
            val mutedChats = prefs[mutedChatsKey(uid)] ?: emptySet()
            if (!mutedChats.contains(chatId)) return false
            val muteUntil = prefs[muteUntilKey(uid, chatId)] ?: 0L
            if (muteUntil == -1L || muteUntil == 0L) return true
            return now < muteUntil
        }

        // 2. Fallback to device-level mute setting ONLY when uid is completely unknown
        val deviceMutedChats = prefs[MUTED_CHATS_DEVICE] ?: emptySet()
        if (deviceMutedChats.contains(chatId)) {
            val muteUntil = prefs[muteUntilDeviceKey(chatId)] ?: 0L
            if (muteUntil == -1L || muteUntil == 0L) return true
            return now < muteUntil
        }

        return false
    }

    suspend fun setChatMuted(chatId: String, isMuted: Boolean, muteUntil: Long = -1L, currentUid: String? = null) {
        android.util.Log.d("MUTE", "chatId = $chatId\nmuted = $isMuted")
        val uid = currentUid ?: context.dataStore.data.first()[LOGGED_IN_UID]
        context.dataStore.edit { preferences ->
            // Update device-level set
            val deviceSet = (preferences[MUTED_CHATS_DEVICE] ?: emptySet()).toMutableSet()
            if (isMuted) {
                deviceSet.add(chatId)
                preferences[muteUntilDeviceKey(chatId)] = muteUntil
            } else {
                deviceSet.remove(chatId)
                preferences.remove(muteUntilDeviceKey(chatId))
            }
            preferences[MUTED_CHATS_DEVICE] = deviceSet

            // Update user-level set if uid is known
            if (uid != null) {
                val userSet = (preferences[mutedChatsKey(uid)] ?: emptySet()).toMutableSet()
                if (isMuted) {
                    userSet.add(chatId)
                    preferences[muteUntilKey(uid, chatId)] = muteUntil
                } else {
                    userSet.remove(chatId)
                    preferences.remove(muteUntilKey(uid, chatId))
                }
                preferences[mutedChatsKey(uid)] = userSet
            }
        }
    }

    suspend fun isChatPrivate(chatId: String, currentUid: String? = null): Boolean {
        val prefs = context.dataStore.data.first()
        val uid = currentUid ?: prefs[LOGGED_IN_UID] ?: return false
        val privateChats = prefs[privateChatsKey(uid)] ?: emptySet()
        return privateChats.contains(chatId)
    }

    suspend fun getAllPrivateChats(currentUid: String? = null): Set<String> {
        val prefs = context.dataStore.data.first()
        val uid = currentUid ?: prefs[LOGGED_IN_UID] ?: return emptySet()
        return prefs[privateChatsKey(uid)] ?: emptySet()
    }

    suspend fun getAllMutedChats(currentUid: String? = null): Set<String> {
        val prefs = context.dataStore.data.first()
        val uid = currentUid ?: prefs[LOGGED_IN_UID] ?: return emptySet()
        return prefs[mutedChatsKey(uid)] ?: emptySet()
    }

    suspend fun setChatPrivate(chatId: String, isPrivate: Boolean, currentUid: String? = null) {
        val uid = currentUid ?: context.dataStore.data.first()[LOGGED_IN_UID] ?: return
        context.dataStore.edit { preferences ->
            val set = (preferences[privateChatsKey(uid)] ?: emptySet()).toMutableSet()
            if (isPrivate) {
                set.add(chatId)
            } else {
                set.remove(chatId)
            }
            preferences[privateChatsKey(uid)] = set
        }
    }

    suspend fun saveSession(uid: String) {
        context.dataStore.edit { preferences ->
            preferences[LOGGED_IN_UID] = uid
        }
    }

    suspend fun getLoggedInUid(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[LOGGED_IN_UID]
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(LOGGED_IN_UID)
        }
    }
}
