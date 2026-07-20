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
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    private val intentData = MutableStateFlow<Pair<String?, String?>>(Pair(null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
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
                VynexApp(appContainer, intentDataState.first, intentDataState.second)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        ActiveChatTracker.isAppInForeground = true
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
        if (chatId != null && senderId != null) {
            intentData.value = Pair(chatId, senderId)
            intent.removeExtra("chatId")
            intent.removeExtra("senderId")
        }
    }
}
