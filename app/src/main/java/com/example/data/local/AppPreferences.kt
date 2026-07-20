package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {
    companion object {
        val LOGGED_IN_UID = stringPreferencesKey("logged_in_uid")
        val APP_LOCKED = booleanPreferencesKey("app_locked")
    }

    val loggedInUidFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LOGGED_IN_UID]
    }

    suspend fun saveSession(uid: String) {
        context.dataStore.edit { preferences ->
            preferences[LOGGED_IN_UID] = uid
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(LOGGED_IN_UID)
        }
    }
}
