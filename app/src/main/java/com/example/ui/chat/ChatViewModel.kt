package com.example.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.Result
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val chats: List<Chat> = emptyList(),
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

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                observeChats()
            } else {
                userChatsJob?.cancel()
                _chatState.update { it.copy(chats = emptyList(), userMap = emptyMap()) }
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth.removeAuthStateListener(it) }
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
                
                for (uid in otherUserIds) {
                    if (!newUserMap.containsKey(uid)) {
                        when (val userResult = userRepository.getUserById(uid)) {
                            is Result.Success -> newUserMap[uid] = userResult.data
                            else -> {}
                        }
                    }
                }
                _chatState.update { it.copy(userMap = newUserMap) }
            }
        }
    }

    fun clearSearch() {
        _chatState.update { it.copy(searchResults = emptyList(), isSearching = false, error = null) }
    }

    fun searchUsers(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            Log.d("SearchAudit", "SearchViewModel: Started search for '$query'")
            delay(300)
            Log.d("SearchAudit", "SearchViewModel: After delay, executing search")
            _chatState.update { it.copy(isSearching = true) }
            when (val result = userRepository.searchUsers(query)) {
                is Result.Success -> {
                    Log.d("SearchAudit", "SearchViewModel: Success, received ${result.data.size} users. Updating state.")
                    _chatState.update { it.copy(searchResults = result.data, isSearching = false, error = null) }
                    Log.d("SearchAudit", "SearchViewModel: State updated. New searchResults size: ${_chatState.value.searchResults.size}")
                }
                is Result.Error -> {
                    Log.d("SearchAudit", "SearchViewModel: Error: ${result.message}")
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
        _chatState.update { it.copy(messages = emptyList(), otherUser = null, isOtherUserTyping = false) }
        
        otherUserJob?.cancel()
        otherUserJob = viewModelScope.launch {
            when (val result = userRepository.getUserById(otherUserId)) {
                is Result.Success -> _chatState.update { it.copy(otherUser = result.data) }
                else -> {}
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
            chatRepository.observeTypingStatus(chatId, otherUserId).collect { isTyping ->
                _chatState.update { it.copy(isOtherUserTyping = isTyping) }
            }
        }
    }

    fun setTyping(isTyping: Boolean) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.setTypingStatus(chatId, isTyping)
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

    fun sendMessage(chatId: String, content: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(chatId, content)
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
        viewModelScope.launch {
            chatRepository.deleteMessage(chatId, messageId, forEveryone)
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
