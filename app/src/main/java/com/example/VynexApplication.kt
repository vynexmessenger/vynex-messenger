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
}

class DefaultAppContainer(private val application: Application) : AppContainer {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val appPreferences: AppPreferences by lazy { AppPreferences(application) }
    
    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(auth, firestore, appPreferences)
    }
    
    override val userRepository: UserRepository by lazy {
        com.example.data.repository.UserRepositoryImpl(auth, firestore)
    }
    
    override val chatRepository: ChatRepository by lazy {
        com.example.data.repository.ChatRepositoryImpl(auth, firestore)
    }
}

class VynexApplication : Application(), DefaultLifecycleObserver {
    lateinit var container: AppContainer

    override fun onCreate() {
        super<Application>.onCreate()
        container = DefaultAppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        
        // App in foreground
        CoroutineScope(Dispatchers.IO).launch {
            container.userRepository.setUserOnlineStatus(true)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // App in background
        CoroutineScope(Dispatchers.IO).launch {
            container.userRepository.setUserOnlineStatus(false)
        }
        
    }
}
