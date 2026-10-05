package com.example.data.repository

import com.example.data.model.Result
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun searchUsers(query: String): Result<List<User>>
    suspend fun getUserById(uid: String): Result<User>
    suspend fun updateUserProfile(user: User): Result<Unit>
    fun observeUserOnlineStatus(uid: String): Flow<Boolean>
    fun observeUser(uid: String): Flow<com.example.data.model.User?>
    suspend fun setUserOnlineStatus(isOnline: Boolean)
    suspend fun blockUser(uid: String): Result<Unit>
    suspend fun unblockUser(uid: String): Result<Unit>
}
