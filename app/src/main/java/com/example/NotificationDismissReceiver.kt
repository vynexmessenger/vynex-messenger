package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppPreferences(context.applicationContext).resetNotificationCount()
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
