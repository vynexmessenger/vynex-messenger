package com.example.data.model

import com.google.firebase.firestore.PropertyName

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(), // UIDs
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val pinnedBy: List<String> = emptyList(),
    val archivedBy: List<String> = emptyList(),
    val privateBy: List<String> = emptyList(),
    val typing: Map<String, Boolean> = emptyMap()
)

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val status: MessageStatus = MessageStatus.SENT,
    val deletedFor: List<String> = emptyList(), // UIDs
    @get:PropertyName("isDeletedForEveryone")
    @set:PropertyName("isDeletedForEveryone")
    var isDeletedForEveryone: Boolean = false,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToContent: String? = null,
    val isPinned: Boolean = false,
    val pinnedUntil: Long? = null,
    @get:PropertyName("isForwarded")
    @set:PropertyName("isForwarded")
    var isForwarded: Boolean = false,
    val forwardedFromMessageId: String? = null,
    val forwardedFromChatId: String? = null
)

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}
