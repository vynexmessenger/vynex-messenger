package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppContainer
import com.example.ui.auth.*
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.chat.ChatViewModelFactory
import com.example.ui.home.HomeScreen
import com.example.ui.home.ProfileScreen
import com.example.ui.home.SearchScreen
import com.example.ui.home.SettingsScreen
import com.example.ui.splash.SplashScreen

object Destinations {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RECOVERY = "recovery"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val CHAT = "chat/{chatId}/{otherUserId}"
    const val OTHER_PROFILE = "other_profile/{userId}"
    const val BLOCKED_USERS = "blocked_users"
    const val PRIVATE_LOCK = "private_lock"
    const val PRIVATE_CHATS = "private_chats"
    const val TERMS = "terms"
}

@Composable
fun VynexApp(
    container: AppContainer,
    initialChatId: String? = null,
    initialOtherUserId: String? = null,
    onIntentHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(container.authRepository)
    )
    
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(container.chatRepository, container.userRepository)
    )

    val authState by authViewModel.authState.collectAsState()
    
    var isAppLocked by remember { mutableStateOf(false) }
    var hasUnlockedThisSession by remember { mutableStateOf(false) }
    var splashFinished by remember { mutableStateOf(false) }

    var pendingPrivateChatId by remember { mutableStateOf<String?>(null) }
    var pendingPrivateOtherUserId by remember { mutableStateOf<String?>(null) }

    fun openChatSecurely(chatId: String, otherUserId: String?) {
        val currentUserId = authState.user?.uid ?: ""
        val targetOtherUserId = otherUserId ?: ""
        val isPrivateInSettings = chatViewModel.chatState.value.chatSettings[chatId]?.isPrivate == true
        val isPrivateInChat = chatViewModel.chatState.value.chats.any { it.id == chatId && it.privateBy.contains(currentUserId) }
        val isLocallyPrivate = try {
            kotlinx.coroutines.runBlocking {
                container.appPreferences.isChatPrivate(chatId, currentUserId)
            }
        } catch (e: Exception) {
            false
        }

        val isPrivate = isPrivateInSettings || isPrivateInChat || isLocallyPrivate

        if (!isPrivate) {
            navController.navigate("chat/$chatId/$targetOtherUserId")
            return
        }

        if (com.example.data.security.PrivateChatSecurityManager.isUnlocked.value) {
            navController.navigate("chat/$chatId/$targetOtherUserId")
            return
        }

        // Chat is private and locked: remember destination and navigate to PIN lock
        pendingPrivateChatId = chatId
        pendingPrivateOtherUserId = targetOtherUserId
        navController.navigate(Destinations.PRIVATE_LOCK)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(authState.user?.settings?.appLockEnabled) {
        val activity = context as? android.app.Activity
        if (activity != null) {
            if (authState.user?.settings?.appLockEnabled == true && !com.example.BuildConfig.DEBUG) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
    
    DisposableEffect(lifecycleOwner, authState.user) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus()
            }
            if (event == Lifecycle.Event.ON_STOP) {
                if (authState.user?.settings?.appLockEnabled == true) {
                    isAppLocked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(authState.user) {
        if (authState.user == null) {
            isAppLocked = false
            hasUnlockedThisSession = false
        } else if (authState.user?.settings?.appLockEnabled == true && !hasUnlockedThisSession) {
            isAppLocked = true
        }
    }

    LaunchedEffect(splashFinished, authState.user) {
        if (!splashFinished) return@LaunchedEffect
        
        val currentRoute = navController.currentDestination?.route ?: Destinations.SPLASH
        
        if (authState.user != null) {
            if (currentRoute == Destinations.SPLASH || currentRoute == Destinations.LOGIN || currentRoute == Destinations.REGISTER || currentRoute == Destinations.RECOVERY) {
                navController.navigate(Destinations.HOME) {
                    popUpTo(0) { inclusive = true }
                }
                if (authState.user!!.settings.appLockEnabled && !hasUnlockedThisSession) {
                    isAppLocked = true
                }
            }
        } else {
            if (currentRoute != Destinations.LOGIN && currentRoute != Destinations.REGISTER && currentRoute != Destinations.RECOVERY) {
                navController.navigate(Destinations.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(initialChatId, initialOtherUserId, splashFinished, authState.user) {
        if (splashFinished && authState.user != null && !initialChatId.isNullOrBlank()) {
            val chatId = initialChatId
            val otherUserId = initialOtherUserId
            onIntentHandled()
            openChatSecurely(chatId, otherUserId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Destinations.SPLASH
        ) {
            composable(Destinations.SPLASH) {
                SplashScreen(onSplashFinished = { splashFinished = true })
            }
            
            composable(Destinations.LOGIN) {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = {
                        navController.navigate(Destinations.REGISTER)
                    },
                    onNavigateToRecovery = {
                        navController.navigate(Destinations.RECOVERY)
                    },
                    onLoginSuccess = {
                        // Handled by LaunchedEffect
                    }
                )
            }
            
            composable(Destinations.REGISTER) {
                RegisterScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.popBackStack()
                    },
                    onNavigateToTerms = {
                        navController.navigate(Destinations.TERMS)
                    },
                    onRegisterSuccess = {
                        // Handled by LaunchedEffect
                    }
                )
            }
            
            composable(Destinations.RECOVERY) {
                RecoveryScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(Destinations.HOME) {
                HomeScreen(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    onNavigateToSearch = {
                        navController.navigate(Destinations.SEARCH)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Destinations.SETTINGS)
                    },
                    onNavigateToProfile = {
                        navController.navigate(Destinations.PROFILE)
                    },
                    onNavigateToPrivateChats = {
                        if (com.example.data.security.PrivateChatSecurityManager.isUnlocked.value) {
                            navController.navigate(Destinations.PRIVATE_CHATS)
                        } else {
                            navController.navigate(Destinations.PRIVATE_LOCK)
                        }
                    },
                    onNavigateToChat = { chatId, otherUserId ->
                        openChatSecurely(chatId, otherUserId)
                    }
                )
            }
            
            composable(Destinations.SEARCH) {
                SearchScreen(
                    chatViewModel = chatViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToChat = { chatId, otherUserId ->
                        openChatSecurely(chatId, otherUserId)
                    }
                )
            }
            
            composable(Destinations.PROFILE) {
                ProfileScreen(
                    viewModel = authViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(
                route = Destinations.CHAT,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("otherUserId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""
                val currentUserId = authState.user?.uid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

                val isPrivateInSettings = chatViewModel.chatState.value.chatSettings[chatId]?.isPrivate == true
                val isPrivateInChat = chatViewModel.chatState.value.chats.any { it.id == chatId && it.privateBy.contains(currentUserId) }
                val isLocallyPrivate = remember(chatId, currentUserId) {
                    try {
                        kotlinx.coroutines.runBlocking { container.appPreferences.isChatPrivate(chatId, currentUserId) }
                    } catch (e: Exception) {
                        false
                    }
                }
                val isPrivate = isPrivateInSettings || isPrivateInChat || isLocallyPrivate
                val isUnlocked by com.example.data.security.PrivateChatSecurityManager.isUnlocked.collectAsState()

                // Security guard: If private and locked, redirect immediately to PIN lock
                LaunchedEffect(chatId, isPrivate, isUnlocked) {
                    if (isPrivate && !isUnlocked) {
                        navController.popBackStack()
                        openChatSecurely(chatId, otherUserId)
                    }
                }

                if (isPrivate && !isUnlocked) {
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    val otherUser = chatViewModel.chatState.value.userMap[otherUserId] ?: chatViewModel.chatState.value.otherUser
                    val amIBlocked = otherUser?.settings?.blockedUsers?.contains(currentUserId) == true
                    val isBlockedByMe = authState.user?.settings?.blockedUsers?.contains(otherUserId) == true
                    val isBlocked = isBlockedByMe || amIBlocked
                    val readReceiptsEnabled = authState.user?.settings?.readReceiptsEnabled ?: true
                    ChatScreen(
                        viewModel = chatViewModel,
                        chatId = chatId,
                        otherUserId = otherUserId,
                        currentUserId = currentUserId,
                        isBlocked = isBlocked,
                        readReceiptsEnabled = readReceiptsEnabled,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onNavigateToProfile = { uid ->
                            navController.navigate("other_profile/$uid")
                        }
                    )
                }
            }

            composable(
                route = Destinations.OTHER_PROFILE,
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                com.example.ui.home.OtherUserProfileScreen(
                    userId = userId,
                    chatViewModel = chatViewModel,
                    authViewModel = authViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(Destinations.SETTINGS) {
                SettingsScreen(
                    viewModel = authViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToBlockedUsers = {
                        navController.navigate(Destinations.BLOCKED_USERS)
                    },
                    onNavigateToTerms = {
                        navController.navigate(Destinations.TERMS)
                    },
                    onLogout = {
                        // Handled by LaunchedEffect
                    }
                )
            }
            
            composable(Destinations.BLOCKED_USERS) {
                com.example.ui.home.BlockedUsersScreen(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Destinations.PRIVATE_LOCK) {
                com.example.ui.auth.PrivateLockScreen(
                    currentUserId = authState.user?.uid ?: "",
                    authViewModel = authViewModel,
                    onNavigateBack = {
                        pendingPrivateChatId = null
                        pendingPrivateOtherUserId = null
                        navController.popBackStack()
                    },
                    onUnlockSuccess = {
                        val targetChatId = pendingPrivateChatId
                        val targetOtherUserId = pendingPrivateOtherUserId
                        pendingPrivateChatId = null
                        pendingPrivateOtherUserId = null

                        if (targetChatId != null) {
                            navController.navigate("chat/$targetChatId/${targetOtherUserId ?: ""}") {
                                popUpTo(Destinations.HOME)
                            }
                        } else {
                            navController.navigate(Destinations.PRIVATE_CHATS) {
                                popUpTo(Destinations.HOME)
                            }
                        }
                    }
                )
            }

            composable(Destinations.PRIVATE_CHATS) {
                com.example.ui.chat.PrivateChatsScreen(
                    currentUserId = authState.user?.uid ?: "",
                    chatViewModel = chatViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToChat = { chatId, otherUserId ->
                        navController.navigate("chat/$chatId/$otherUserId")
                    }
                )
            }

            composable(Destinations.TERMS) {
                com.example.ui.home.TermsAndConditionsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
        
        AnimatedVisibility(
            visible = isAppLocked && splashFinished,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
        ) {
            AppLockScreen(
                viewModel = authViewModel,
                onUnlockSuccess = { 
                    isAppLocked = false 
                    hasUnlockedThisSession = true
                }
            )
        }
    }
}
