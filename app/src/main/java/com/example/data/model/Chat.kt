package com.example.data.model

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(), // UIDs
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val pinnedBy: List<String> = emptyList(),
    val archivedBy: List<String> = emptyList(),
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
    val isDeletedForEveryone: Boolean = false,
    val replyToMessageId: String? = null,
    val isPinned: Boolean = false
)

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}
