package com.example

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.data.local.AppPreferences
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.UserRepository
import com.example.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface AppContainer {
    val authRepository: AuthRepository
    val userRepository: UserRepository
    val chatRepository: ChatRepository
    val appPreferences: AppPreferences
}

class DefaultAppContainer(private val application: Application) : AppContainer {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    override val appPreferences: AppPreferences by lazy { AppPreferences(application) }
    
    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(auth, firestore, appPreferences)
    }
    
    override val userRepository: UserRepository by lazy {
        com.example.data.repository.UserRepositoryImpl(auth, firestore)
    }
    
    override val chatRepository: ChatRepository by lazy {
        com.example.data.repository.ChatRepositoryImpl(auth, firestore, appPreferences)
    }
}

class VynexApplication : Application(), DefaultLifecycleObserver {
    lateinit var container: AppContainer
    private var offlineJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super<Application>.onCreate()
        container = DefaultAppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Keep FCM auto-init disabled to prevent background registration failure loops on devices/emulators without FCM registration support
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = false
        } catch (e: Exception) {
            android.util.Log.w("VynexApp", "FCM auto-init config skipped: ${e.message}")
        }

        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onLost(network: android.net.Network) {
                super.onLost(network)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    container.userRepository.setUserOnlineStatus(false)
                }
            }
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        container.userRepository.setUserOnlineStatus(true)
                    }
                }
            }
        })
    }

    override fun onStart(owner: LifecycleOwner) {
        offlineJob?.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            container.userRepository.setUserOnlineStatus(true)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        com.example.data.security.PrivateChatSecurityManager.lock()
        offlineJob = CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(3000)
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                return@launch
            }
            container.userRepository.setUserOnlineStatus(false)
        }
    }
}
