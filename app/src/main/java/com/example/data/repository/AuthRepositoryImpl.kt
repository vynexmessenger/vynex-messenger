package com.example.data.repository

import com.example.data.local.AppPreferences
import com.example.data.model.Result
import com.example.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val appPreferences: AppPreferences
) : AuthRepository {

    private val AUTH_SECRET = "Vynex@2026!Auth"

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null
        
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                firestoreListener?.remove()
                firestoreListener = null
                trySend(null)
            } else {
                firestoreListener?.remove()
                firestoreListener = firestore.collection("users").document(firebaseUser.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(null)
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            trySend(snapshot.toObject(User::class.java))
                        } else {
                            trySend(null)
                        }
                    }
            }
        }
        
        auth.addAuthStateListener(authListener)
        awaitClose { 
            auth.removeAuthStateListener(authListener)
            firestoreListener?.remove()
        }
    }

    override suspend fun login(username: String, password: String): Result<User> {
        return try {
            val lowercaseUsername = username.trim().lowercase()
            val email = "${lowercaseUsername.removePrefix("@")}@vynex.local"
            
            // Login with generated email and static secret
            val authResult = auth.signInWithEmailAndPassword(email, AUTH_SECRET).await()
            val uid = authResult.user?.uid ?: return Result.Error("Authentication failed")

            // Get user details
            val userDoc = firestore.collection("users").document(uid).get().await()
            val user = userDoc.toObject(User::class.java) ?: run {
                auth.signOut()
                return Result.Error("User data not found")
            }
            
            // Verify real password
            if (user.passwordHash != hashString(password)) {
                auth.signOut()
                return Result.Error("Invalid username or password")
            }

            // Save session
            appPreferences.saveSession(uid)

            try {
                val token = FirebaseMessaging.getInstance().getToken().await()
                Log.d("FCM_AUDIT", "[ANDROID] Token fetched on login: $token")
                firestore.collection("users").document(uid).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
                Log.d("FCM_AUDIT", "[ANDROID] Token successfully uploaded to Firestore on login.")
            } catch (e: Exception) {
                Log.e("FCM_AUDIT", "[ANDROID] Failed to fetch/upload token on login: ${e.message}", e)
            }

            Result.Success(user)
        } catch (e: FirebaseAuthException) {
            val message = if (e.errorCode == "ERROR_USER_NOT_FOUND" || e.errorCode == "ERROR_INVALID_CREDENTIAL") {
                "Invalid username or password"
            } else {
                getFriendlyErrorMessage(e.errorCode)
            }
            Result.Error(message)
        } catch (e: Exception) {
            Result.Error("Login failed. Please try again later.")
        }
    }

    override suspend fun register(username: String, dob: String, password: String, pin: String): Result<User> {
        return try {
            val lowercaseUsername = username.trim().lowercase()
            val email = "${lowercaseUsername.removePrefix("@")}@vynex.local"

            // 1. Create Auth Account with static secret
            val authResult = try {
                 auth.createUserWithEmailAndPassword(email, AUTH_SECRET).await()
            } catch (e: FirebaseAuthException) {
                if (e.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") {
                     return Result.Error("Username is already taken")
                }
                throw e
            }
            
            val uid = authResult.user?.uid ?: return Result.Error("Registration failed")

            val hashedPin = hashString(pin)
            val hashedPassword = hashString(password)
            val now = System.currentTimeMillis()
            val user = User(
                uid = uid,
                username = lowercaseUsername,
                email = email,
                dateOfBirth = dob,
                securityPin = hashedPin,
                passwordHash = hashedPassword,
                createdAt = now,
                updatedAt = now
            )

            // 2. Save to users collection
            firestore.collection("users").document(uid).set(user).await()

            // 3. Save to usernames collection mapping
            val usernameMap = hashMapOf(
                "uid" to uid,
                "email" to email
            )
            firestore.collection("usernames").document(lowercaseUsername.removePrefix("@")).set(usernameMap).await()

            // 4. Save session
            appPreferences.saveSession(uid)

            try {
                val token = FirebaseMessaging.getInstance().getToken().await()
                Log.d("FCM_AUDIT", "[ANDROID] Token fetched on register: $token")
                firestore.collection("users").document(uid).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
                Log.d("FCM_AUDIT", "[ANDROID] Token successfully uploaded to Firestore on register.")
            } catch (e: Exception) {
                Log.e("FCM_AUDIT", "[ANDROID] Failed to fetch/upload token on register: ${e.message}", e)
            }

            Result.Success(user)
        } catch (e: FirebaseAuthException) {
            Result.Error(getFriendlyErrorMessage(e.errorCode))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Registration failed: ${e.message}")
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            auth.signOut()
            appPreferences.clearSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to logout")
        }
    }

    override suspend fun checkUsernameAvailable(username: String): Result<Boolean> {
        return try {
            val lowercaseUsername = username.trim().lowercase().removePrefix("@")
            val doc = firestore.collection("usernames").document(lowercaseUsername).get().await()
            Result.Success(!doc.exists())
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.Error("Unable to verify username. Please try again later.")
            } else {
                Result.Error("Database connection error. Please try again.")
            }
        } catch (e: Exception) {
            Result.Error("Unable to verify username. Please try again later.")
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.Error("Not logged in")
            val uid = user.uid
            
            // Get user to find their username
            val userDoc = firestore.collection("users").document(uid).get().await()
            val username = userDoc.getString("username")
            
            // Delete data
            if (username != null) {
                firestore.collection("usernames").document(username.removePrefix("@")).delete().await()
            }
            
            // Remove user from chats
            val chatsSnapshot = firestore.collection("chats")
                .whereArrayContains("participants", uid)
                .get().await()
            for (chatDoc in chatsSnapshot.documents) {
                // If it's a 1 on 1 chat, we can just delete the whole chat or remove participation.
                // Let's delete the chat completely to leave no trace as requested for privacy/deletion.
                chatDoc.reference.delete().await()
            }
            
            firestore.collection("users").document(uid).delete().await()
            
            // Delete auth user
            user.delete().await()
            
            appPreferences.clearSession()
            Result.Success(Unit)
        } catch (e: FirebaseAuthException) {
            Result.Error(getFriendlyErrorMessage(e.errorCode))
        } catch (e: Exception) {
            Result.Error("Failed to delete account. Please try again.")
        }
    }
    
    override suspend fun recoverPassword(username: String, dob: String, pin: String, newPassword: String): Result<Unit> {
        return try {
            val lowercaseUsername = username.trim().lowercase()
            val email = "${lowercaseUsername.removePrefix("@")}@vynex.local"
            
            // Sign in with static secret to access user doc
            val authResult = auth.signInWithEmailAndPassword(email, AUTH_SECRET).await()
            val uid = authResult.user?.uid ?: return Result.Error("Account not found")

            // Get user details
            val userDoc = firestore.collection("users").document(uid).get().await()
            val user = userDoc.toObject(User::class.java) ?: run {
                auth.signOut()
                return Result.Error("User data not found")
            }

            // Verify DOB and PIN
            if (user.dateOfBirth != dob || user.securityPin != hashString(pin)) {
                auth.signOut()
                return Result.Error("Invalid account details provided")
            }

            // Update password hash
            firestore.collection("users").document(uid)
                .update("passwordHash", hashString(newPassword)).await()

            auth.signOut()
            Result.Success(Unit)
        } catch (e: FirebaseAuthException) {
            val message = if (e.errorCode == "ERROR_USER_NOT_FOUND" || e.errorCode == "ERROR_INVALID_CREDENTIAL") {
                "Invalid account details provided"
            } else {
                getFriendlyErrorMessage(e.errorCode)
            }
            Result.Error(message)
        } catch (e: Exception) {
            Result.Error("Recovery failed. Please try again later.")
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            firestore.collection("users").document(user.uid).set(user).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to update user")
        }
    }

    private fun hashString(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getFriendlyErrorMessage(errorCode: String): String {
        return when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "The email address is badly formatted."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "This email address is already in use by another account."
            "ERROR_USER_NOT_FOUND" -> "No user found with this email."
            "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please try again."
            "ERROR_WEAK_PASSWORD" -> "The password is too weak."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Please check your connection."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many unsuccessful attempts. Please try again later."
            else -> "An authentication error occurred. Please try again."
        }
    }


    override suspend fun uploadProfilePhoto(imageBytes: ByteArray): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.Error("Not logged in")
            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
            val photoRef = storageRef.child("profile_photos/$uid.jpg")
            photoRef.putBytes(imageBytes).await()
            val downloadUrl = photoRef.downloadUrl.await().toString()
            Result.Success(downloadUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Failed to upload photo")
        }
    }


}
