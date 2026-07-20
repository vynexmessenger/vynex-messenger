package com.example.data.repository

import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.Result
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun getOrCreateChat(otherUserId: String): Result<String>
    suspend fun sendMessage(chatId: String, content: String): Result<Unit>
    suspend fun resetUnreadCount(chatId: String)
    suspend fun markMessageAsRead(messageId: String, chatId: String)
    suspend fun markMessageAsDelivered(messageId: String, chatId: String)
    suspend fun setTypingStatus(chatId: String, isTyping: Boolean)
    fun observeTypingStatus(chatId: String, otherUserId: String): Flow<Boolean>
    suspend fun togglePinChat(chatId: String): Result<Unit>
    suspend fun toggleArchiveChat(chatId: String): Result<Unit>
    suspend fun deleteChat(chatId: String, forEveryone: Boolean = true): Result<Unit>
    suspend fun deleteMessage(chatId: String, messageId: String, forEveryone: Boolean): Result<Unit>
}
