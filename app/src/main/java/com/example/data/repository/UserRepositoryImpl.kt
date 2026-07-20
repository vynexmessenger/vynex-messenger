package com.example.data.repository
import kotlinx.coroutines.tasks.await


import com.example.data.model.Result
import com.example.data.model.User
import com.example.data.model.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


class UserRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : UserRepository {

    private fun DocumentSnapshot.toUserSafe(): User? {
        return try {
            val uid = this.getString("uid") ?: this.id
            val username = this.getString("username") ?: ""
            val email = this.getString("email") ?: ""
            val displayName = this.getString("displayName") ?: ""
            val bio = this.getString("bio") ?: ""
            val profilePhoto = this.getString("profilePhoto") ?: ""
            val isOnline = this.getBoolean("isOnline") ?: false
            val lastSeen = this.getLong("lastSeen") ?: 0L
            val fcmToken = this.getString("fcmToken") ?: ""
            
            User(
                uid = uid,
                username = username,
                email = email,
                displayName = displayName,
                bio = bio,
                profilePhoto = profilePhoto,
                isOnline = isOnline,
                lastSeen = lastSeen,
                fcmToken = fcmToken
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val lowercaseQuery = query.trim().lowercase()
            if (lowercaseQuery.isEmpty() || lowercaseQuery == "@") return Result.Success(emptyList())

            val exactUsername = lowercaseQuery.removePrefix("@")
            val queryWithoutAt = exactUsername
            val queryWithAt = "@$exactUsername"

            val currentUid = auth.currentUser?.uid ?: return Result.Error("Not authenticated")

            // Exact match only using IN query
            val snapshot = firestore.collection("users")
                .whereIn("username", listOf(queryWithoutAt, queryWithAt))
                .limit(1)
                .get()
                .await()

            val users = snapshot.documents
                .mapNotNull { it.toUserSafe() }
                .distinctBy { it.uid }
                .filter { it.uid != currentUid }

            Result.Success(users)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error(e.message ?: "Failed to search users")
        }
    }

    override suspend fun getUserById(uid: String): Result<User> {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val user = doc.toUserSafe()
            if (user != null) {
                Result.Success(user)
            } else {
                Result.Error("User not found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Failed to get user")
        }
    }

    override suspend fun updateUserProfile(user: User): Result<Unit> {
        return try {
            firestore.collection("users").document(user.uid).set(user).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Failed to update profile")
        }
    }

    override fun observeUserOnlineStatus(uid: String): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val isOnline = snapshot?.getBoolean("isOnline") ?: false
                trySend(isOnline)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun setUserOnlineStatus(isOnline: Boolean) {
        try {
            val uid = auth.currentUser?.uid ?: return
            val updates = mutableMapOf<String, Any>(
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis()
            )
            
            if (isOnline) {
                try {
                    val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().await()
                    updates["fcmToken"] = token
                    Log.d("FCM_AUDIT", "[ANDROID] Token fetched on status update: $token")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            firestore.collection("users").document(uid).set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override suspend fun blockUser(uid: String): Result<Unit> {
        return try {
            val currentUid = auth.currentUser?.uid ?: return Result.Error("Not authenticated")
            firestore.collection("users").document(currentUid)
                .update("settings.blockedUsers", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to block user")
        }
    }

    override suspend fun unblockUser(uid: String): Result<Unit> {
        return try {
            val currentUid = auth.currentUser?.uid ?: return Result.Error("Not authenticated")
            firestore.collection("users").document(currentUid)
                .update("settings.blockedUsers", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to unblock user")
        }
    }
}
