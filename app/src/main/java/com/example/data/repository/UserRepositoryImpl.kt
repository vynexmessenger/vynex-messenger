package com.example.data.repository
import kotlinx.coroutines.tasks.await

import com.example.data.model.Result
import com.example.data.model.User
import com.example.data.model.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UserRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : UserRepository {

    private val deviceSessionId: String = java.util.UUID.randomUUID().toString().take(12)
    private var heartbeatJob: Job? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastKnownUid: String? = auth.currentUser?.uid

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                lastKnownUid = user.uid
            } else {
                stopHeartbeatSync()
                val prevUid = lastKnownUid
                if (prevUid != null) {
                    try {
                        firestore.collection("users").document(prevUid).update(
                            mapOf(
                                "activeSessions.$deviceSessionId" to FieldValue.delete(),
                                "isOnline" to false,
                                "lastSeen" to System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    lastKnownUid = null
                }
            }
        }
    }

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
            val sessionsMap = (this.get("activeSessions") as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String
                val value = (v as? Number)?.toLong()
                if (key != null && value != null) key to value else null
            }?.toMap() ?: emptyMap()
            
            val settingsMap = this.get("settings") as? Map<*, *>
            val settings = if (settingsMap != null) {
                com.example.data.model.UserSettings(
                    notificationsEnabled = settingsMap["notificationsEnabled"] as? Boolean ?: true,
                    readReceiptsEnabled = settingsMap["readReceiptsEnabled"] as? Boolean ?: true,
                    showOnlineStatus = settingsMap["showOnlineStatus"] as? Boolean ?: true,
                    appLockEnabled = settingsMap["appLockEnabled"] as? Boolean ?: false,
                    showMessageContent = settingsMap["showMessageContent"] as? Boolean ?: true,
                    blockedUsers = (settingsMap["blockedUsers"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                )
            } else {
                com.example.data.model.UserSettings()
            }
            
            User(
                uid = uid,
                username = username,
                email = email,
                displayName = displayName,
                bio = bio,
                profilePhoto = profilePhoto,
                isOnline = isOnline,
                lastSeen = lastSeen,
                activeSessions = sessionsMap,
                fcmToken = fcmToken,
                settings = settings
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val cleanQuery = query.trim().trimStart('@').lowercase()
            if (cleanQuery.isEmpty()) return Result.Success(emptyList())

            val currentUid = auth.currentUser?.uid ?: return Result.Error("Not authenticated")

            // Query by username prefix:
            // Searches for usernames formatted as "@query..." as well as "query..."
            // Both are strictly query-driven Firestore queries with limit(20)
            val withAtSnapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo("username", "@$cleanQuery")
                .whereLessThanOrEqualTo("username", "@$cleanQuery\uf8ff")
                .limit(20)
                .get()
                .await()

            val withoutAtSnapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo("username", cleanQuery)
                .whereLessThanOrEqualTo("username", "$cleanQuery\uf8ff")
                .limit(20)
                .get()
                .await()

            val users = (withAtSnapshot.documents + withoutAtSnapshot.documents)
                .mapNotNull { it.toUserSafe() }
                .distinctBy { it.uid }
                .filter { it.uid != currentUid }
                .take(20)

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
        var currentUserDoc: User? = null
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("UserRepository", "Snapshot error for online status $uid: ${error.message}")
                    return@addSnapshotListener
                }
                val user = snapshot?.toUserSafe()
                currentUserDoc = user
                trySend(user?.isCurrentlyOnline ?: false)
            }
        // Periodic ticker to ensure offline transition triggers even without server writes
        val tickerJob = launch {
            while (isActive) {
                delay(20_000L)
                currentUserDoc?.let { trySend(it.isCurrentlyOnline) }
            }
        }
        awaitClose { 
            listener.remove()
            tickerJob.cancel()
        }
    }

    override fun observeUser(uid: String): Flow<User?> = callbackFlow {
        var currentUserDoc: User? = null
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("UserRepository", "Snapshot error for user $uid: ${error.message}")
                    return@addSnapshotListener
                }
                val user = snapshot?.toUserSafe()
                currentUserDoc = user
                trySend(user)
            }
        // Periodic ticker to evaluate presence freshness
        val tickerJob = launch {
            while (isActive) {
                delay(20_000L)
                currentUserDoc?.let { trySend(it) }
            }
        }
        awaitClose { 
            listener.remove()
            tickerJob.cancel()
        }
    }

    override suspend fun setUserOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: lastKnownUid ?: return
        if (isOnline) {
            startHeartbeat(uid)
        } else {
            stopHeartbeat(uid)
        }
    }

    private fun startHeartbeat(uid: String) {
        heartbeatJob?.cancel()
        heartbeatJob = repositoryScope.launch {
            writePresence(uid, true)
            while (isActive) {
                delay(User.HEARTBEAT_INTERVAL_MS)
                writePresence(uid, true)
            }
        }
    }

    private suspend fun writePresence(uid: String, isOnline: Boolean) {
        try {
            val now = System.currentTimeMillis()
            val updates = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to now,
                "activeSessions.$deviceSessionId" to now
            )
            firestore.collection("users").document(uid).update(updates).await()
        } catch (e: Exception) {
            try {
                val now = System.currentTimeMillis()
                val updates = mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to now,
                    "activeSessions" to mapOf(deviceSessionId to now)
                )
                firestore.collection("users").document(uid).set(updates, SetOptions.merge()).await()
            } catch (ex: Exception) {
                Log.w("UserRepository", "Failed to update presence: ${ex.message}")
            }
        }
    }

    private fun stopHeartbeatSync() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun stopHeartbeat(uid: String) {
        stopHeartbeatSync()
        try {
            val now = System.currentTimeMillis()
            val userRef = firestore.collection("users").document(uid)
            val snapshot = userRef.get().await()
            val activeSessions = (snapshot.get("activeSessions") as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String
                val value = (v as? Number)?.toLong()
                if (key != null && value != null) key to value else null
            }?.toMap() ?: emptyMap()

            val otherActive = activeSessions.filter { (id, ts) ->
                id != deviceSessionId && (now - ts) < User.PRESENCE_TIMEOUT_MS
            }

            val updates = mutableMapOf<String, Any>(
                "activeSessions.$deviceSessionId" to FieldValue.delete(),
                "lastSeen" to now
            )
            if (otherActive.isEmpty()) {
                updates["isOnline"] = false
            }
            userRef.update(updates).await()
        } catch (e: Exception) {
            Log.w("UserRepository", "Failed to clear session on stop: ${e.message}")
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
