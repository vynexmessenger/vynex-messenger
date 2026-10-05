package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.VynexApp
import com.example.ui.theme.MyApplicationTheme
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.content.Context
import android.app.NotificationManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    private val intentData = MutableStateFlow<Pair<String?, String?>>(Pair(null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        handleIntent(intent)
        
        val appContainer = (application as VynexApplication).container
        
        setContent {
            val intentDataState by intentData.collectAsState()
            MyApplicationTheme {
                VynexApp(
                    container = appContainer,
                    initialChatId = intentDataState.first,
                    initialOtherUserId = intentDataState.second,
                    onIntentHandled = {
                        intentData.value = Pair(null, null)
                    }
                )
            }
        }
    }


    override fun onResume() {
        super.onResume()
        ActiveChatTracker.isAppInForeground = true
        clearPrivacyNotifications()
    }

    override fun onPause() {
        super.onPause()
        ActiveChatTracker.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val chatId = intent.getStringExtra("chatId")
        val senderId = intent.getStringExtra("senderId")
        if (!chatId.isNullOrBlank()) {
            intentData.value = Pair(chatId, senderId ?: "")
            intent.removeExtra("chatId")
            intent.removeExtra("senderId")
        }
        val isPrivacy = intent.getBooleanExtra("isPrivacyNotification", false)
        if (isPrivacy) {
            intent.removeExtra("isPrivacyNotification")
        }
        clearPrivacyNotifications()
    }

    private fun clearPrivacyNotifications() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.example.data.local.AppPreferences(applicationContext).resetNotificationCount()
            } catch (e: Exception) {
                // ignore
            }
        }
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(MyFirebaseMessagingService.PRIVACY_NOTIFICATION_ID)
        } catch (e: Exception) {
            // ignore
        }
    }
}
