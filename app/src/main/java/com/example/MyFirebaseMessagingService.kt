package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_AUDIT", "[ANDROID] onNewToken triggered. New token generated.")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FCM_AUDIT", "[ANDROID] Token successfully uploaded to Firestore.")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM_AUDIT", "[ANDROID] Token upload to Firestore failed.", e)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM_AUDIT", "[ANDROID] onMessageReceived triggered.")
        
        val title = message.notification?.title ?: message.data["title"] ?: "New Message"
        val body = message.notification?.body ?: message.data["body"] ?: "You have a new message."
        val chatId = message.data["chatId"]
        val senderId = message.data["senderId"]
        val messageId = message.data["messageId"]
        
        // Mark as DELIVERED
        if (chatId != null && messageId != null) {
            try {
                FirebaseFirestore.getInstance().collection("chats").document(chatId)
                    .collection("messages").document(messageId)
                    .update("status", "DELIVERED")
            } catch (e: Exception) {
                Log.e("FCM_AUDIT", "Failed to mark as delivered: ${e.message}")
            }
        }
        
        if (ActiveChatTracker.isAppInForeground && ActiveChatTracker.activeChatId == chatId) {
            Log.d("FCM_AUDIT", "[ANDROID] App in foreground and user is actively viewing chat $chatId. Suppressing notification.")
            return
        }
        
        try {
            showNotification(title, body, chatId, senderId)
        } catch (e: Exception) {
            Log.e("FCM_AUDIT", "[ANDROID] Exception while showing notification: ${e.message}", e)
        }
    }

    private fun showNotification(title: String, message: String, chatId: String?, senderId: String?) {
        val channelId = "vynex_messages_high_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (chatId != null) {
                putExtra("chatId", chatId)
            }
            if (senderId != null) {
                putExtra("senderId", senderId)
            }
        }

        val requestCode = Random.nextInt()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, Vibrate
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pendingIntent)

        val notificationId = chatId?.hashCode() ?: Random.nextInt()
        
        try {
            notificationManager.notify(notificationId, notificationBuilder.build())
            Log.d("FCM_AUDIT", "[ANDROID] Notification dispatched successfully.")
        } catch (e: SecurityException) {
            Log.e("FCM_AUDIT", "[ANDROID] Missing POST_NOTIFICATIONS permission.")
        }
    }
}
