package com.example.data.repository

import com.example.data.model.Chat
import com.example.data.model.ChatSetting
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.Result
import com.example.data.local.AppPreferences
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val appPreferences: AppPreferences? = null
) : ChatRepository {

    override fun observeChats(): Flow<List<Chat>> = callbackFlow {
        val uid = auth.currentUser?.uid
        
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        if (uid != null) {
            listener = firestore.collection("chats")
                .whereArrayContains("participants", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    val chats = snapshot?.documents?.mapNotNull { it.toObject(Chat::class.java)?.copy(id = it.id) } ?: emptyList()
                    trySend(chats)
                }
        } else {
            trySend(emptyList())
        }
            
        awaitClose { listener?.remove() }
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MESSAGES", "[MESSAGES] Snapshot listener error: ${error.message}", error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val msg = doc.toObject(Message::class.java)?.copy(id = doc.id)
                        if (msg != null) {
                            val rawDeletedFor = (doc.get("deletedFor") as? List<*>)?.mapNotNull { it as? String }
                                ?: msg.deletedFor
                            val isDeletedEveryone = doc.getBoolean("isDeletedForEveryone") == true
                                || doc.getBoolean("deletedForEveryone") == true
                                || msg.isDeletedForEveryone
                            val isFwd = doc.getBoolean("isForwarded") == true
                                || doc.getBoolean("forwarded") == true
                                || msg.isForwarded
                            msg.copy(
                                deletedFor = rawDeletedFor,
                                isDeletedForEveryone = isDeletedEveryone,
                                isForwarded = isFwd
                            )
                        } else null
                    } catch (e: Exception) {
                        android.util.Log.e("MESSAGES", "[MESSAGES] Error parsing message ${doc.id}: ${e.message}", e)
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }
            
        awaitClose { listener.remove() }
    }

    override suspend fun getOrCreateChat(otherUserId: String): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.Error("Not authenticated")
            
            // Check if chat exists
            val snapshot = firestore.collection("chats")
                .whereArrayContains("participants", uid)
                .get()
                .await()
                
            val existingChat = snapshot.documents.firstOrNull { doc ->
                val participants = doc.get("participants") as? List<*>
                participants?.contains(otherUserId) == true
            }
            
            if (existingChat != null) {
                return Result.Success(existingChat.id)
            }
            
            // Create new chat
            val newChatId = UUID.randomUUID().toString()
            val chat = Chat(
                id = newChatId,
                participants = listOf(uid, otherUserId),
                lastMessage = "",
                lastMessageTime = System.currentTimeMillis(),
                unreadCounts = mapOf(uid to 0, otherUserId to 0)
            )
            
            firestore.collection("chats").document(newChatId).set(chat).await()
            Result.Success(newChatId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Failed to create chat: ${e.message}")
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        content: String,
        replyToMessageId: String?,
        replyToSenderName: String?,
        replyToContent: String?,
        isForwarded: Boolean,
        forwardedFromMessageId: String?,
        forwardedFromChatId: String?
    ): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.Error("Not authenticated")
            
            val chatDoc = firestore.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<*>
            val otherUserId = participants?.firstOrNull { it != uid } as? String ?: return Result.Error("Invalid chat")
            
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            val message = Message(
                id = messageId,
                chatId = chatId,
                senderId = uid,
                receiverId = otherUserId,
                content = content,
                timestamp = now,
                status = MessageStatus.SENT,
                replyToMessageId = replyToMessageId,
                replyToSenderName = replyToSenderName,
                replyToContent = replyToContent,
                isForwarded = isForwarded,
                forwardedFromMessageId = forwardedFromMessageId,
                forwardedFromChatId = forwardedFromChatId
            )
            
            val batch = firestore.batch()
            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document(messageId)
            batch.set(messageRef, message)
            
            val chatRef = firestore.collection("chats").document(chatId)
            
            val currentUnread = (chatDoc.get("unreadCounts") as? Map<String, Long>)?.get(otherUserId)?.toInt() ?: 0
            
            batch.update(chatRef, mapOf(
                "lastMessage" to content,
                "lastMessageTime" to now,
                "lastSenderId" to uid,
                "unreadCounts.$otherUserId" to currentUnread + 1
            ))
            
            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Failed to send message")
        }
    }

        override suspend fun resetUnreadCount(chatId: String) {
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("chats").document(chatId)
                .update("unreadCounts.$uid", 0)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun markMessageAsDelivered(messageId: String, chatId: String) {
        try {
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val currentStatus = snapshot.getString("status")
                if (currentStatus == MessageStatus.SENT.name) {
                    transaction.update(messageRef, "status", MessageStatus.DELIVERED.name)
                }
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override suspend fun markMessageAsRead(messageId: String, chatId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("status", MessageStatus.READ.name)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun setTypingStatus(chatId: String, isTyping: Boolean) {
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("chats").document(chatId)
                .update("typing.$uid", isTyping)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun observeTypingStatus(chatId: String, otherUserId: String): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val typingMap = snapshot?.get("typing") as? Map<String, Boolean>
                val isTyping = typingMap?.get(otherUserId) ?: false
                trySend(isTyping)
            }
        awaitClose { listener.remove() }
    }
    
    override suspend fun togglePinChat(chatId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.Error("Unauthenticated")
            val chatRef = firestore.collection("chats").document(chatId)
            val chatDoc = chatRef.get().await()
            val pinnedBy = chatDoc.get("pinnedBy") as? List<String> ?: emptyList()
            if (pinnedBy.contains(uid)) {
                chatRef.update("pinnedBy", com.google.firebase.firestore.FieldValue.arrayRemove(uid)).await()
            } else {
                chatRef.update("pinnedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to toggle pin")
        }
    }

    override suspend fun toggleArchiveChat(chatId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.Error("Unauthenticated")
            val chatRef = firestore.collection("chats").document(chatId)
            val chatDoc = chatRef.get().await()
            val archivedBy = chatDoc.get("archivedBy") as? List<String> ?: emptyList()
            if (archivedBy.contains(uid)) {
                chatRef.update("archivedBy", com.google.firebase.firestore.FieldValue.arrayRemove(uid)).await()
            } else {
                chatRef.update("archivedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to toggle archive")
        }
    }

    override suspend fun deleteChat(chatId: String, forEveryone: Boolean): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.Error("Unauthenticated")
            if (forEveryone) {
                // Delete messages subcollection
                val messages = firestore.collection("chats").document(chatId).collection("messages").get().await()
                val batch = firestore.batch()
                for (doc in messages.documents) {
                    batch.delete(doc.reference)
                }
                batch.delete(firestore.collection("chats").document(chatId))
                batch.commit().await()
            } else {
                firestore.collection("chats").document(chatId)
                    .update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                    .await()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to delete chat")
        }
    }
    override suspend fun deleteMessage(chatId: String, messageId: String, forEveryone: Boolean): Result<Unit> {
        val actionName = if (forEveryone) "DELETE_FOR_EVERYONE" else "DELETE_FOR_ME"
        try {
            val currentUid = auth.currentUser?.uid ?: run {
                android.util.Log.e("DELETE", "[DELETE] action = $actionName FAILED: Unauthenticated")
                return Result.Error("Unauthenticated")
            }
            android.util.Log.d("DELETE", "[DELETE] action = $actionName")
            android.util.Log.d("DELETE", "[DELETE] userId = $currentUid")
            android.util.Log.d("DELETE", "[DELETE] chatId = $chatId")
            android.util.Log.d("DELETE", "[DELETE] messageId = $messageId")
            android.util.Log.d("DELETE", "[DELETE] Firestore write started")

            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document(messageId)
            
            if (forEveryone) {
                val messageSnapshot = messageRef.get().await()
                if (!messageSnapshot.exists()) {
                    android.util.Log.e("DELETE", "[DELETE] Firestore write FAILED: Message $messageId not found in chat $chatId")
                    return Result.Error("Message not found")
                }
                val senderId = messageSnapshot.getString("senderId")
                if (senderId != currentUid) {
                    android.util.Log.e("DELETE", "[DELETE] Permission rejected: senderId ($senderId) != currentUid ($currentUid)")
                    return Result.Error("You can only delete your own messages for everyone")
                }
                
                val updates = mapOf<String, Any>(
                    "isDeletedForEveryone" to true,
                    "deletedForEveryone" to true,
                    "deletedAt" to System.currentTimeMillis(),
                    "deletedBy" to currentUid
                )
                messageRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                
                // Also update the chat's lastMessage if this was the last message of the chat
                val chatRef = firestore.collection("chats").document(chatId)
                val chatDoc = chatRef.get().await()
                if (chatDoc.exists()) {
                    val lastMessageTime = chatDoc.getLong("lastMessageTime") ?: 0L
                    val messageTimestamp = messageSnapshot.getLong("timestamp") ?: 0L
                    if (lastMessageTime == messageTimestamp) {
                        chatRef.update("lastMessage", "This message was deleted.").await()
                    }
                }
                android.util.Log.d("DELETE", "[DELETE] Firestore write SUCCESS")
            } else {
                android.util.Log.d("DELETE", "[DELETE] Adding $currentUid to deletedFor array on message $messageId")
                messageRef.set(
                    mapOf("deletedFor" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUid)),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
                android.util.Log.d("DELETE", "[DELETE] Firestore write SUCCESS")
            }
            return Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("DELETE", "[DELETE] Firestore write FAILED: ${e.message}", e)
            e.printStackTrace()
            return Result.Error("Failed to delete message: ${e.message}")
        }
    }

    override suspend fun pinMessage(chatId: String, messageId: String, durationMillis: Long?): Result<Unit> {
        return try {
            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document(messageId)
            val pinnedUntil = if (durationMillis != null && durationMillis > 0L) {
                System.currentTimeMillis() + durationMillis
            } else {
                null
            }
            val updates = mutableMapOf<String, Any?>("isPinned" to true)
            if (pinnedUntil != null) {
                updates["pinnedUntil"] = pinnedUntil
            } else {
                updates["pinnedUntil"] = com.google.firebase.firestore.FieldValue.delete()
            }
            messageRef.update(updates).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Failed to pin message")
        }
    }

    override suspend fun unpinMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document(messageId)
            messageRef.update(mapOf(
                "isPinned" to false,
                "pinnedUntil" to com.google.firebase.firestore.FieldValue.delete()
            )).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error("Failed to unpin message")
        }
    }

    override fun observeChatSettings(): Flow<Map<String, ChatSetting>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        val settingsMap = mutableMapOf<String, ChatSetting>()

        // 1. Immediately emit from local DataStore cache so state is available instantly
        try {
            val localPrivate = appPreferences?.getAllPrivateChats(uid) ?: emptySet()
            val localMuted = appPreferences?.getAllMutedChats(uid) ?: emptySet()
            localPrivate.forEach { chatId ->
                settingsMap[chatId] = ChatSetting(chatId = chatId, isPrivate = true)
            }
            localMuted.forEach { chatId ->
                val existing = settingsMap[chatId] ?: ChatSetting(chatId = chatId)
                settingsMap[chatId] = existing.copy(isMuted = true)
            }
            if (settingsMap.isNotEmpty()) {
                trySend(settingsMap.toMap())
            }
        } catch (e: Exception) {
            Log.w("PRIVATE_MOVE", "[PRIVATE_MOVE] Failed reading local cached settings: ${e.message}")
        }

        // 2. Listen to user document: users/{uid} for chatSettings map (safe & permitted by Firestore rules)
        val userDocListener = firestore.collection("users").document(uid)
            .addSnapshotListener { userDoc, userErr ->
                if (userErr != null || userDoc == null || !userDoc.exists()) return@addSnapshotListener
                val raw = userDoc.get("chatSettings") as? Map<*, *>
                if (raw != null) {
                    raw.forEach { (k, v) ->
                        val chatId = k as? String ?: return@forEach
                        val data = v as? Map<*, *> ?: return@forEach
                        val isPrivate = data["isPrivate"] as? Boolean ?: false
                        val isMuted = data["isMuted"] as? Boolean ?: false
                        val muteUntil = (data["muteUntil"] as? Number)?.toLong() ?: 0L
                        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                        settingsMap[chatId] = ChatSetting(
                            chatId = chatId,
                            isPrivate = isPrivate,
                            isMuted = isMuted,
                            muteUntil = muteUntil,
                            updatedAt = updatedAt
                        )
                    }
                    trySend(settingsMap.toMap())
                }
            }

        awaitClose {
            userDocListener.remove()
        }
    }

    override suspend fun setChatPrivate(chatId: String, isPrivate: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.Error("Unauthenticated")
        val now = System.currentTimeMillis()

        Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] START")
        Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] currentUserId = $uid")
        Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] chatId = $chatId")
        Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Writing private state...")
        Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Firestore path = users/$uid/chatSettings/$chatId")
        Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Field = isPrivate")
        Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Value = $isPrivate")

        var writeSucceeded = false
        var lastError: Exception? = null

        // 1. Local DataStore persistence
        try {
            appPreferences?.setChatPrivate(chatId, isPrivate, uid)
            writeSucceeded = true
            Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Saved to local DataStore")
        } catch (e: Exception) {
            Log.e("PRIVATE_MOVE", "[PRIVATE_MOVE] Local DataStore write failed: ${e.message}", e)
        }

        // 2. Primary Firestore path: users/{uid}/chatSettings/{chatId}
        try {
            val docRef = firestore.collection("users").document(uid).collection("chatSettings").document(chatId)
            docRef.set(
                mapOf(
                    "chatId" to chatId,
                    "isPrivate" to isPrivate,
                    "updatedAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            writeSucceeded = true
            Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Subcollection write successful (users/$uid/chatSettings/$chatId)")
        } catch (e: Exception) {
            lastError = e
            Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Subcollection not permitted by Firestore rules (${e.message}), relying on user doc and DataStore")
        }

        // 3. User document field fallback: users/{uid}.chatSettings.{chatId}
        try {
            val userDocRef = firestore.collection("users").document(uid)
            val settingMap = mapOf(
                "chatId" to chatId,
                "isPrivate" to isPrivate,
                "updatedAt" to now
            )
            userDocRef.set(
                mapOf(
                    "chatSettings" to mapOf(
                        chatId to settingMap
                    )
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            writeSucceeded = true
            Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] User doc chatSettings write successful")
        } catch (e: Exception) {
            Log.w("PRIVATE_MOVE", "[PRIVATE_MOVE] User doc chatSettings write failed: ${e.message}")
        }

        // 4. Update chat document privateBy array
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            if (isPrivate) {
                chatRef.update("privateBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
            } else {
                chatRef.update("privateBy", com.google.firebase.firestore.FieldValue.arrayRemove(uid)).await()
            }
            writeSucceeded = true
            Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] Chat doc privateBy updated")
        } catch (e: Exception) {
            // non-fatal
        }

        return if (writeSucceeded) {
            Log.d("PRIVATE_MOVE", "[PRIVATE_MOVE] WRITE SUCCESS")
            Result.Success(Unit)
        } else {
            Log.e("PRIVATE_MOVE", "[PRIVATE_MOVE] WRITE FAILED")
            Log.e("PRIVATE_MOVE", "[PRIVATE_MOVE] ERROR = ${lastError?.message}")
            Result.Error(lastError?.message ?: "Failed to write private state")
        }
    }

    override suspend fun setChatMuted(chatId: String, isMuted: Boolean, muteUntil: Long): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.Error("Unauthenticated")
        val now = System.currentTimeMillis()

        try {
            appPreferences?.setChatMuted(chatId, isMuted, muteUntil, uid)
        } catch (e: Exception) {
            Log.e("PRIVATE_MOVE", "Local DataStore setChatMuted error: ${e.message}", e)
        }

        try {
            val docRef = firestore.collection("users").document(uid).collection("chatSettings").document(chatId)
            docRef.set(
                mapOf(
                    "chatId" to chatId,
                    "isMuted" to isMuted,
                    "muteUntil" to muteUntil,
                    "updatedAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w("PRIVATE_MOVE", "Subcollection setChatMuted error: ${e.message}")
        }

        try {
            val userDocRef = firestore.collection("users").document(uid)
            val settingMap = mapOf(
                "chatId" to chatId,
                "isMuted" to isMuted,
                "muteUntil" to muteUntil,
                "updatedAt" to now
            )
            userDocRef.set(
                mapOf("chatSettings" to mapOf(chatId to settingMap)),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w("PRIVATE_MOVE", "User doc setChatMuted error: ${e.message}")
        }

        return Result.Success(Unit)
    }

    override suspend fun getChatSetting(chatId: String): ChatSetting? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("users").document(uid).collection("chatSettings").document(chatId).get().await()
            if (doc.exists()) {
                ChatSetting(
                    chatId = doc.id,
                    isPrivate = doc.getBoolean("isPrivate") ?: false,
                    isMuted = doc.getBoolean("isMuted") ?: false,
                    muteUntil = doc.getLong("muteUntil") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: 0L
                )
            } else {
                val isPriv = appPreferences?.isChatPrivate(chatId, uid) ?: false
                val isMut = appPreferences?.isChatMuted(chatId, uid) ?: false
                ChatSetting(chatId = chatId, isPrivate = isPriv, isMuted = isMut)
            }
        } catch (e: Exception) {
            val isPriv = appPreferences?.isChatPrivate(chatId, uid) ?: false
            val isMut = appPreferences?.isChatMuted(chatId, uid) ?: false
            ChatSetting(chatId = chatId, isPrivate = isPriv, isMuted = isMut)
        }
    }
}
