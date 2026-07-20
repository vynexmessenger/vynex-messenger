package com.example.data.repository

import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
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
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { it.toObject(Message::class.java)?.copy(id = it.id) } ?: emptyList()
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

    override suspend fun sendMessage(chatId: String, content: String): Result<Unit> {
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
                status = MessageStatus.SENT
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
        try {
            val currentUid = auth.currentUser?.uid ?: return Result.Error("Unauthenticated")
            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document(messageId)
            
            if (forEveryone) {
                messageRef.update("isDeletedForEveryone", true).await()
            } else {
                messageRef.update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid)).await()
            }
            return Result.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.Error("Failed to delete message")
        }
    }
}
