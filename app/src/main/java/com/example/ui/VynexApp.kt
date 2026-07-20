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
}

@Composable
fun VynexApp(container: AppContainer, initialChatId: String? = null, initialOtherUserId: String? = null) {
    val navController = rememberNavController()
    
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(container.authRepository)
    )
    
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(container.chatRepository, container.userRepository)
    )

    val authState by authViewModel.authState.collectAsState()
    
    var isAppLocked by remember { mutableStateOf(false) }
    var splashFinished by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
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
                if (authState.user!!.settings.appLockEnabled) {
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
        if (splashFinished && authState.user != null && initialChatId != null && initialOtherUserId != null) {
            navController.navigate("chat/$initialChatId/$initialOtherUserId")
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
                    onNavigateToChat = { chatId, otherUserId ->
                        navController.navigate("chat/$chatId/$otherUserId")
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
                        navController.navigate("chat/$chatId/$otherUserId")
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
                val currentUserId = authState.user?.uid ?: ""
                
                ChatScreen(
                    viewModel = chatViewModel,
                    chatId = chatId,
                    otherUserId = otherUserId,
                    currentUserId = currentUserId,
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
                    onLogout = {
                        // Handled by LaunchedEffect
                    }
                )
            }
        }
        
        AnimatedVisibility(
            visible = isAppLocked && splashFinished,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AppLockScreen(
                viewModel = authViewModel,
                onUnlockSuccess = { isAppLocked = false }
            )
        }
    }
}
