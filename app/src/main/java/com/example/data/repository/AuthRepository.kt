package com.example.data.repository

import com.example.data.model.Result
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getCurrentUser(): Flow<User?>
    suspend fun login(username: String, password: String): Result<User>
    suspend fun register(username: String, dob: String, password: String, pin: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun checkUsernameAvailable(username: String): Result<Boolean>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun recoverPassword(username: String, dob: String, pin: String, newPassword: String): Result<Unit>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun uploadProfilePhoto(imageBytes: ByteArray): Result<String>
}
