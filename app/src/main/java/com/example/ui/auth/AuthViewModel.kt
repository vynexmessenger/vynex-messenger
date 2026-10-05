package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Result
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val user: User? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _authState.update { it.copy(user = user) }
            }
        }
    }

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            _authState.update { it.copy(error = "Username and password cannot be empty") }
            return
        }

        val normalizedUsername = if (username.startsWith("@")) username else "@$username"

        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.login(normalizedUsername, password)) {
                is Result.Success -> {
                    _authState.update { it.copy(isLoading = false, user = result.data, error = null) }
                    onSuccess()
                }
                is Result.Error -> {
                    _authState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun register(username: String, dob: String, password: String, confirmPassword: String, pin: String, onSuccess: () -> Unit) {
        if (username.isBlank() || dob.isBlank() || password.isBlank() || pin.isBlank()) {
            _authState.update { it.copy(error = "All fields are required") }
            return
        }
        val normalizedUsername = if (username.startsWith("@")) username else "@$username"
        if (!normalizedUsername.matches(Regex("^@[a-zA-Z0-9_]+$"))) {
            _authState.update { it.copy(error = "Username can only contain @, letters, numbers, and underscores") }
            return
        }
        if (password != confirmPassword) {
            _authState.update { it.copy(error = "Passwords do not match") }
            return
        }
        if (password.length < 6) {
            _authState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _authState.update { it.copy(error = "Security PIN must be exactly 4 digits") }
            return
        }

        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.register(normalizedUsername, dob, password, pin)) {
                is Result.Success -> {
                    _authState.update { it.copy(isLoading = false, user = result.data, error = null) }
                    onSuccess()
                }
                is Result.Error -> {
                    _authState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun recoverPassword(username: String, dob: String, pin: String, newPassword: String, confirmPassword: String, onSuccess: () -> Unit) {
        if (username.isBlank() || dob.isBlank() || pin.isBlank() || newPassword.isBlank()) {
            _authState.update { it.copy(error = "All fields are required") }
            return
        }
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _authState.update { it.copy(error = "Security PIN must be exactly 4 digits") }
            return
        }
        if (newPassword != confirmPassword) {
            _authState.update { it.copy(error = "Passwords do not match") }
            return
        }
        if (newPassword.length < 6) {
            _authState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        val normalizedUsername = if (username.startsWith("@")) username else "@$username"

        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.recoverPassword(normalizedUsername, dob, pin, newPassword)) {
                is Result.Success -> {
                    _authState.update { it.copy(isLoading = false, error = null) }
                    onSuccess()
                }
                is Result.Error -> {
                    _authState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.logout()) {
                is Result.Success -> {
                    _authState.update { it.copy(isLoading = false, user = null, error = null) }
                    onSuccess()
                }
                is Result.Error -> {
                    _authState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    _authState.update { it.copy(isLoading = false, user = null, error = null) }
                    onSuccess()
                }
                is Result.Error -> {
                    _authState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun updateUserProfile(displayName: String, bio: String, profilePhoto: String) {
        val user = _authState.value.user ?: return
        val updatedUser = user.copy(displayName = displayName, bio = bio, profilePhoto = profilePhoto)
        viewModelScope.launch {
            authRepository.updateUser(updatedUser)
            // Current user flow from firestore will automatically update the state, but we can do it proactively or just let firestore trigger it.
        }
    }
    
    fun updateUserSettings(
        notificationsEnabled: Boolean,
        readReceiptsEnabled: Boolean,
        showOnlineStatus: Boolean,
        appLockEnabled: Boolean,
        showMessageContent: Boolean = true
    ) {
        val user = _authState.value.user ?: return
        val updatedSettings = user.settings.copy(
            notificationsEnabled = notificationsEnabled,
            readReceiptsEnabled = readReceiptsEnabled,
            showOnlineStatus = showOnlineStatus,
            appLockEnabled = appLockEnabled,
            showMessageContent = showMessageContent
        )
        val updatedUser = user.copy(settings = updatedSettings)
        viewModelScope.launch {
            authRepository.updateUser(updatedUser)
        }
    }

    fun verifyPin(pin: String): Boolean {
        val user = _authState.value.user ?: return false
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        val hashedInput = bytes.joinToString("") { "%02x".format(it) }
        return hashedInput == user.securityPin
    }

    fun clearError() {
        _authState.update { it.copy(error = null) }
    }


    fun uploadProfilePhoto(imageBytes: ByteArray, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.uploadProfilePhoto(imageBytes)) {
                is Result.Success -> {
                    // Update user profile immediately
                    val user = _authState.value.user
                    if (user != null) {
                        val updatedUser = user.copy(profilePhoto = result.data)
                        authRepository.updateUser(updatedUser)
                        _authState.update { it.copy(user = updatedUser, isLoading = false) }
                        onSuccess(result.data)
                    } else {
                        _authState.update { it.copy(isLoading = false) }
                    }
                }
                is Result.Error -> {
                    _authState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }


}
