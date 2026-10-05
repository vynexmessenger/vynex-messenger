package com.example.data.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.local.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom

class PrivateChatSecurityManager(private val context: Context) {

    companion object {
        private val _isUnlocked = MutableStateFlow(false)
        val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

        fun unlock() {
            _isUnlocked.value = true
        }

        fun lock() {
            _isUnlocked.value = false
        }

        fun clearSession() {
            _isUnlocked.value = false
        }
    }

    private fun pinHashKey(userId: String) = stringPreferencesKey("private_pin_hash_$userId")
    private fun pinSaltKey(userId: String) = stringPreferencesKey("private_pin_salt_$userId")

    suspend fun hasPrivatePin(userId: String): Boolean {
        if (userId.isEmpty()) return false
        val prefs = context.dataStore.data.first()
        val hash = prefs[pinHashKey(userId)]
        return !hash.isNullOrEmpty()
    }

    suspend fun savePrivatePin(userId: String, pin: String): Boolean {
        if (userId.isEmpty() || pin.length != 4) return false
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hashBase64 = hashPin(pin, saltBytes)

        context.dataStore.edit { prefs ->
            prefs[pinHashKey(userId)] = hashBase64
            prefs[pinSaltKey(userId)] = saltBase64
        }
        unlock()
        return true
    }

    suspend fun verifyPrivatePin(userId: String, pin: String): Boolean {
        if (userId.isEmpty() || pin.length != 4) return false
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[pinHashKey(userId)] ?: return false
        val storedSaltBase64 = prefs[pinSaltKey(userId)] ?: return false

        val saltBytes = try {
            Base64.decode(storedSaltBase64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return false
        }

        val computedHash = hashPin(pin, saltBytes)
        val valid = computedHash == storedHash
        if (valid) {
            unlock()
        }
        return valid
    }

    suspend fun resetPrivatePin(userId: String) {
        if (userId.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs.remove(pinHashKey(userId))
            prefs.remove(pinSaltKey(userId))
        }
        lock()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
