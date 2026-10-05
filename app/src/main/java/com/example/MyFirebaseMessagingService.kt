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
import kotlinx.coroutines.tasks.await
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
        
        val appPreferences = com.example.data.local.AppPreferences(applicationContext)
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid 
            ?: try { kotlinx.coroutines.runBlocking { appPreferences.getLoggedInUid() } } catch (e: Exception) { null }

        // Check if chat is muted for current user
        val isMuted = if (chatId != null) {
            try {
                kotlinx.coroutines.runBlocking {
                    val localMuted = appPreferences.isChatMuted(chatId, currentUid)
                    if (localMuted) {
                        true
                    } else if (currentUid != null) {
                        val userDoc = FirebaseFirestore.getInstance()
                            .collection("users").document(currentUid)
                            .get()
                            .await()
                        val chatSettingsMap = (userDoc.get("chatSettings") as? Map<*, *>)?.get(chatId) as? Map<*, *>
                        val isMutedField = chatSettingsMap?.get("isMuted") as? Boolean ?: false
                        val muteUntil = (chatSettingsMap?.get("muteUntil") as? Number)?.toLong() ?: 0L
                        if (isMutedField) {
                            if (muteUntil == -1L || muteUntil == 0L || System.currentTimeMillis() < muteUntil) {
                                appPreferences.setChatMuted(chatId, true, muteUntil, currentUid)
                                true
                            } else false
                        } else {
                            try {
                                val doc = FirebaseFirestore.getInstance()
                                    .collection("users").document(currentUid)
                                    .collection("chatSettings").document(chatId)
                                    .get()
                                    .await()
                                val subIsMuted = doc.getBoolean("isMuted") ?: false
                                val subMuteUntil = doc.getLong("muteUntil") ?: 0L
                                if (subIsMuted && (subMuteUntil == -1L || subMuteUntil == 0L || System.currentTimeMillis() < subMuteUntil)) {
                                    appPreferences.setChatMuted(chatId, true, subMuteUntil, currentUid)
                                    true
                                } else false
                            } catch (e: Exception) {
                                false
                            }
                        }
                    } else false
                }
            } catch (e: Exception) {
                false
            }
        } else false

        val showMessageContent = try {
            kotlinx.coroutines.runBlocking {
                appPreferences.getShowMessageContent(currentUid)
            }
        } catch (e: Exception) {
            true
        }

        val isViewingActiveChat = ActiveChatTracker.isAppInForeground && ActiveChatTracker.activeChatId == chatId
        val shouldNotify = !isMuted && !isViewingActiveChat

        Log.d("NOTIFICATION_DECISION", "chatId = ${chatId ?: "none"}\nmuted = $isMuted\nshowMessageContent = $showMessageContent")
        Log.d("NOTIFICATION_DECISION", "shouldNotify = $shouldNotify")

        if (!shouldNotify) {
            if (isMuted) {
                Log.d("FCM_AUDIT", "[ANDROID] Chat $chatId is muted for current user. Suppressing notification.")
            } else if (isViewingActiveChat) {
                Log.d("FCM_AUDIT", "[ANDROID] App in foreground and user is actively viewing chat $chatId. Suppressing notification.")
            }
            return
        }

        if (showMessageContent) {
            try {
                showNotification(title, body, chatId, senderId)
            } catch (e: Exception) {
                Log.e("FCM_AUDIT", "[ANDROID] Exception while showing notification: ${e.message}", e)
            }
        } else {
            try {
                showPrivacyNotification(chatId, senderId)
            } catch (e: Exception) {
                Log.e("FCM_AUDIT", "[ANDROID] Exception while showing privacy notification: ${e.message}", e)
            }
        }
    }

    private fun showPrivacyNotification(chatId: String?, senderId: String?) {
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

        val appPreferences = com.example.data.local.AppPreferences(applicationContext)
        val hasActivePrivacyNotification = try {
            notificationManager.activeNotifications.any { it.id == PRIVACY_NOTIFICATION_ID }
        } catch (e: Exception) {
            false
        }

        val count = kotlinx.coroutines.runBlocking {
            if (!hasActivePrivacyNotification) {
                appPreferences.resetNotificationCount()
            }
            appPreferences.incrementNotificationCount(chatId)
        }

        val title = "Vynex Messenger"
        val body = if (count <= 1) "1 new message" else "$count new messages"

        val targetChatId = kotlinx.coroutines.runBlocking { appPreferences.getLastNotificationChatId() } ?: chatId

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (targetChatId != null) {
                putExtra("chatId", targetChatId)
            }
            if (senderId != null) {
                putExtra("senderId", senderId)
            }
            putExtra("isPrivacyNotification", true)
        }

        val requestCode = Random.nextInt()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val deleteIntent = Intent(this, NotificationDismissReceiver::class.java)
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            PRIVACY_NOTIFICATION_ID,
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setNumber(count)

        try {
            Log.d("NOTIFICATION_BUILD", "title = $title\nbody = $body")
            notificationManager.notify(PRIVACY_NOTIFICATION_ID, notificationBuilder.build())
            Log.d("FCM_AUDIT", "[ANDROID] Privacy notification dispatched: $title - $body (count: $count)")
        } catch (e: SecurityException) {
            Log.e("FCM_AUDIT", "[ANDROID] Missing POST_NOTIFICATIONS permission.")
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
            val safeBody = if (message.length > 8) "${message.take(4)}***${message.takeLast(4)} (length ${message.length})" else "***"
            Log.d("NOTIFICATION_BUILD", "title = $title\nbody = $safeBody")
            notificationManager.notify(notificationId, notificationBuilder.build())
            Log.d("FCM_AUDIT", "[ANDROID] Notification dispatched successfully.")
        } catch (e: SecurityException) {
            Log.e("FCM_AUDIT", "[ANDROID] Missing POST_NOTIFICATIONS permission.")
        }
    }

    companion object {
        const val PRIVACY_NOTIFICATION_ID = 9901
    }
}
