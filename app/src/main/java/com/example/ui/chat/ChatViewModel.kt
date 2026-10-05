package com.example.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Chat
import com.example.data.model.ChatSetting
import com.example.data.model.Message
import com.example.data.model.Result
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val chats: List<Chat> = emptyList(),
    val chatSettings: Map<String, ChatSetting> = emptyMap(),
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val messages: List<Message> = emptyList(),
    val otherUser: User? = null,
    val isOtherUserTyping: Boolean = false,
    val userMap: Map<String, User> = emptyMap()
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private var currentChatId: String? = null
    private var searchJob: Job? = null
    private var messagesJob: Job? = null
    private var typingJob: Job? = null
    private var otherUserJob: Job? = null
    private var userChatsJob: Job? = null
    private var chatSettingsJob: Job? = null
    private val observedUsers = mutableSetOf<String>()

    private var currentUserJob: Job? = null
    private var currentUser: User? = null

    private val userObservationJobs = mutableMapOf<String, Job>()
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser != null) {
                observeChats()
                observeChatSettings()
                observeCurrentUser(firebaseUser.uid)
            } else {
                userChatsJob?.cancel()
                chatSettingsJob?.cancel()
                currentUserJob?.cancel()
                userObservationJobs.values.forEach { it.cancel() }
                userObservationJobs.clear()
                observedUsers.clear()
                currentUser = null
                com.example.data.security.PrivateChatSecurityManager.clearSession()
                _chatState.update { it.copy(chats = emptyList(), chatSettings = emptyMap(), userMap = emptyMap()) }
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun observeChatSettings() {
        chatSettingsJob?.cancel()
        chatSettingsJob = viewModelScope.launch {
            chatRepository.observeChatSettings().collect { settings ->
                _chatState.update { it.copy(chatSettings = settings) }
            }
        }
    }

    private fun observeCurrentUser(uid: String) {
        currentUserJob?.cancel()
        currentUserJob = viewModelScope.launch {
            userRepository.observeUser(uid).collect { user ->
                currentUser = user
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth.removeAuthStateListener(it) }
        userChatsJob?.cancel()
        chatSettingsJob?.cancel()
        currentUserJob?.cancel()
        userObservationJobs.values.forEach { it.cancel() }
        userObservationJobs.clear()
        observedUsers.clear()
    }

    private fun observeChats() {
        val currentUid = auth.currentUser?.uid ?: return
        userChatsJob?.cancel()
        userChatsJob = viewModelScope.launch {
            _chatState.update { it.copy(isLoading = true) }
            chatRepository.observeChats().collect { chats ->
                val activeChats = chats.filter { it.lastMessageTime > 0L || it.lastMessage.isNotEmpty() }
                _chatState.update { it.copy(chats = activeChats, isLoading = false) }
                
                val otherUserIds = activeChats.flatMap { it.participants }.filter { it != currentUid }.distinct()
                val newUserMap = _chatState.value.userMap.toMutableMap()
                
                _chatState.update { it.copy(userMap = newUserMap) }
                
                for (uid in otherUserIds) {
                    if (!observedUsers.contains(uid)) {
                        observedUsers.add(uid)
                        val job = viewModelScope.launch {
                            userRepository.observeUser(uid).collect { user ->
                                if (user != null) {
                                    _chatState.update { state ->
                                        val map = state.userMap.toMutableMap()
                                        map[uid] = user
                                        state.copy(userMap = map)
                                    }
                                }
                            }
                        }
                        userObservationJobs[uid] = job
                    }
                }
            }
        }
    }

    fun clearSearch() {
        _chatState.update { it.copy(searchResults = emptyList(), isSearching = false, error = null) }
    }

    fun searchUsers(query: String) {
        val clean = query.trim().trimStart('@')
        searchJob?.cancel()
        if (clean.isEmpty()) {
            _chatState.update { it.copy(searchResults = emptyList(), isSearching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _chatState.update { it.copy(isSearching = true) }
            when (val result = userRepository.searchUsers(query)) {
                is Result.Success -> {
                    _chatState.update { it.copy(searchResults = result.data, isSearching = false, error = null) }
                }
                is Result.Error -> {
                    _chatState.update { it.copy(searchResults = emptyList(), isSearching = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun startOrGetChat(otherUserId: String, onChatReady: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = chatRepository.getOrCreateChat(otherUserId)) {
                is Result.Success -> onChatReady(result.data)
                is Result.Error -> _chatState.update { it.copy(error = result.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun loadChatData(chatId: String, otherUserId: String) {
        currentChatId = chatId
        val currentUid = auth.currentUser?.uid ?: ""

        // 1. Resolve otherUserId if empty or equal to currentUid
        var targetUserId = otherUserId
        if (targetUserId.isBlank() || targetUserId == currentUid) {
            val existingChat = _chatState.value.chats.firstOrNull { it.id == chatId }
            targetUserId = existingChat?.participants?.firstOrNull { it != currentUid } ?: ""
        }

        // Pre-populate otherUser immediately if available in userMap
        val initialUser = if (targetUserId.isNotBlank()) _chatState.value.userMap[targetUserId] else null
        _chatState.update { it.copy(messages = emptyList(), otherUser = initialUser, isOtherUserTyping = false) }

        otherUserJob?.cancel()
        otherUserJob = viewModelScope.launch {
            // If targetUserId is still blank, resolve from Firestore chat document directly
            if (targetUserId.isBlank()) {
                try {
                    val chatDoc = FirebaseFirestore.getInstance().collection("chats").document(chatId).get().await()
                    val participants = chatDoc.get("participants") as? List<*>
                    targetUserId = participants?.mapNotNull { it as? String }?.firstOrNull { it != currentUid } ?: ""
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Failed to resolve other user from chat doc $chatId", e)
                }
            }

            if (targetUserId.isNotBlank()) {
                // If we didn't have initialUser, check userMap again or emit immediately
                _chatState.value.userMap[targetUserId]?.let { cached ->
                    _chatState.update { it.copy(otherUser = cached) }
                }

                userRepository.observeUser(targetUserId).collect { user ->
                    if (user != null) {
                        _chatState.update { state ->
                            val updatedUserMap = state.userMap.toMutableMap()
                            updatedUserMap[targetUserId] = user
                            state.copy(otherUser = user, userMap = updatedUserMap)
                        }
                    }
                }
            }
        }

        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.resetUnreadCount(chatId)
            chatRepository.observeMessages(chatId).collect { msgs ->
                _chatState.update { it.copy(messages = msgs) }
            }
        }

        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            if (targetUserId.isNotBlank()) {
                chatRepository.observeTypingStatus(chatId, targetUserId).collect { isTyping ->
                    _chatState.update { it.copy(isOtherUserTyping = isTyping) }
                }
            }
        }
    }

    private var typingTimeoutJob: kotlinx.coroutines.Job? = null

    fun setTyping(isTyping: Boolean) {
        val chatId = currentChatId ?: return
        
        val onlineStatusEnabled = currentUser?.settings?.showOnlineStatus ?: true
        if (!onlineStatusEnabled) {
            typingTimeoutJob?.cancel()
            viewModelScope.launch {
                chatRepository.setTypingStatus(chatId, false)
            }
            return
        }
        
        if (isTyping) {
            viewModelScope.launch {
                chatRepository.setTypingStatus(chatId, true)
            }
            typingTimeoutJob?.cancel()
            typingTimeoutJob = viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                chatRepository.setTypingStatus(chatId, false)
            }
        } else {
            typingTimeoutJob?.cancel()
            viewModelScope.launch {
                chatRepository.setTypingStatus(chatId, false)
            }
        }
    }

    fun clearChatData() {
        val chatId = currentChatId
        if (chatId != null) {
            viewModelScope.launch {
                chatRepository.setTypingStatus(chatId, false)
            }
        }
        currentChatId = null
        otherUserJob?.cancel()
        otherUserJob = null
        messagesJob?.cancel()
        messagesJob = null
        typingJob?.cancel()
        typingJob = null
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        _chatState.update { it.copy(messages = emptyList(), otherUser = null, isOtherUserTyping = false) }
    }

    
    fun resetUnreadCount() {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.resetUnreadCount(chatId)
        }
    }

    fun markMessageAsRead(messageId: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.markMessageAsRead(messageId, chatId)
            chatRepository.resetUnreadCount(chatId)
        }
    }

    fun markMessageAsDelivered(messageId: String, chatId: String) {
        viewModelScope.launch {
            chatRepository.markMessageAsDelivered(messageId, chatId)
        }
    }

    fun sendMessage(chatId: String, content: String, replyTo: Message? = null) {
        val replyId = replyTo?.id
        val replyAuthor = if (replyTo != null) {
            if (replyTo.senderId == auth.currentUser?.uid) {
                "You"
            } else {
                val authorUser = _chatState.value.userMap[replyTo.senderId]
                    ?: _chatState.value.otherUser?.takeIf { it.uid == replyTo.senderId }
                authorUser?.canonicalUsername?.takeIf { it.isNotBlank() }?.let { "@$it" }
                    ?: authorUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: "@User"
            }
        } else null
        val replyContent = replyTo?.content

        viewModelScope.launch {
            chatRepository.sendMessage(
                chatId = chatId,
                content = content,
                replyToMessageId = replyId,
                replyToSenderName = replyAuthor,
                replyToContent = replyContent
            )
        }
    }

    fun pinMessage(chatId: String, messageId: String, durationMillis: Long?) {
        viewModelScope.launch {
            chatRepository.pinMessage(chatId, messageId, durationMillis)
        }
    }

    fun unpinMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.unpinMessage(chatId, messageId)
        }
    }

    fun forwardMessage(targetChatId: String, originalMessage: Message, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = chatRepository.sendMessage(
                chatId = targetChatId,
                content = originalMessage.content,
                isForwarded = true,
                forwardedFromMessageId = originalMessage.id,
                forwardedFromChatId = originalMessage.chatId
            )
            if (result is Result.Success) {
                onSuccess()
            }
        }
    }

    fun togglePinChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.togglePinChat(chatId)
        }
    }

    fun toggleArchiveChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.toggleArchiveChat(chatId)
        }
    }

    fun deleteMessage(chatId: String, messageId: String, forEveryone: Boolean) {
        val currentUid = auth.currentUser?.uid ?: ""
        // Immediate optimistic UI update so the message reacts instantly
        _chatState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == messageId) {
                    if (forEveryone) {
                        msg.copy(isDeletedForEveryone = true)
                    } else {
                        msg.copy(deletedFor = (msg.deletedFor + currentUid).distinct())
                    }
                } else {
                    msg
                }
            }
            state.copy(messages = updatedMessages)
        }

        viewModelScope.launch {
            val result = chatRepository.deleteMessage(chatId, messageId, forEveryone)
            if (result is Result.Error) {
                android.util.Log.e("DELETE", "[DELETE] Repository deleteMessage error: ${result.message}")
            }
        }
    }

    fun deleteChat(chatId: String, forEveryone: Boolean = true) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId, forEveryone)
        }
    }

    fun blockUser(uid: String) {
        viewModelScope.launch {
            userRepository.blockUser(uid)
        }
    }

    fun unblockUser(uid: String) {
        viewModelScope.launch {
            userRepository.unblockUser(uid)
        }
    }

    fun setChatPrivate(chatId: String, isPrivate: Boolean) {
        val currentUid = auth.currentUser?.uid ?: ""
        // Immediate optimistic UI update so the chat moves instantly without delay
        _chatState.update { state ->
            val updatedSettings = state.chatSettings.toMutableMap()
            val existing = updatedSettings[chatId] ?: ChatSetting(chatId = chatId)
            updatedSettings[chatId] = existing.copy(isPrivate = isPrivate, updatedAt = System.currentTimeMillis())

            val updatedChats = state.chats.map { chat ->
                if (chat.id == chatId) {
                    val newPrivateBy = if (isPrivate) {
                        (chat.privateBy + currentUid).distinct()
                    } else {
                        chat.privateBy - currentUid
                    }
                    chat.copy(privateBy = newPrivateBy)
                } else {
                    chat
                }
            }
            state.copy(chatSettings = updatedSettings, chats = updatedChats)
        }

        viewModelScope.launch {
            val result = chatRepository.setChatPrivate(chatId, isPrivate)
            if (result is Result.Error) {
                android.util.Log.e("PRIVATE_MOVE", "[PRIVATE_MOVE] Repository setChatPrivate failed: ${result.message}")
            }
        }
    }

    fun setChatMuted(chatId: String, isMuted: Boolean, muteUntil: Long = -1L) {
        android.util.Log.d("MUTE", "chatId = $chatId\nmuted = $isMuted")
        _chatState.update { state ->
            val settings = state.chatSettings.toMutableMap()
            val existing = settings[chatId] ?: com.example.data.model.ChatSetting(chatId = chatId)
            settings[chatId] = existing.copy(isMuted = isMuted, muteUntil = muteUntil)
            state.copy(chatSettings = settings)
        }
        viewModelScope.launch {
            chatRepository.setChatMuted(chatId, isMuted, muteUntil)
        }
    }
}

class ChatViewModelFactory(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
