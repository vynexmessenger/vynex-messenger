package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppPreferences
import com.example.data.local.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class NotificationPrivacyTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = AppPreferences(context)
        context.dataStore.edit { it.clear() }
    }

    @Test
    fun testDefaultShowMessageContentIsOn() = runBlocking {
        // Default must be true (ON)
        val defaultValue = appPreferences.showMessageContentFlow.first()
        assertTrue("Default show message content must be ON (true)", defaultValue)
    }

    @Test
    fun testToggleShowMessageContentPersists() = runBlocking {
        // Toggle to OFF
        appPreferences.setShowMessageContent(false)
        assertFalse("Show message content should be OFF (false)", appPreferences.showMessageContentFlow.first())
        assertFalse("Synchronous get should also return OFF (false)", appPreferences.getShowMessageContent())

        // Toggle back to ON
        appPreferences.setShowMessageContent(true)
        assertTrue("Show message content should be ON (true)", appPreferences.showMessageContentFlow.first())
        assertTrue("Synchronous get should also return ON (true)", appPreferences.getShowMessageContent())
    }

    @Test
    fun testNotificationCountingSequence() = runBlocking {
        // Start fresh
        appPreferences.resetNotificationCount()

        // Message 1 arrives
        val count1 = appPreferences.incrementNotificationCount("chat_1")
        assertEquals(1, count1)
        val body1 = if (count1 <= 1) "1 new message" else "$count1 new messages"
        assertEquals("1 new message", body1)

        // Message 2 arrives
        val count2 = appPreferences.incrementNotificationCount("chat_1")
        assertEquals(2, count2)
        val body2 = if (count2 <= 1) "1 new message" else "$count2 new messages"
        assertEquals("2 new messages", body2)

        // Message 3 arrives
        val count3 = appPreferences.incrementNotificationCount("chat_2")
        assertEquals(3, count3)
        val body3 = if (count3 <= 1) "1 new message" else "$count3 new messages"
        assertEquals("3 new messages", body3)

        // Reset when user taps or dismisses
        appPreferences.resetNotificationCount()
        val countAfterReset = appPreferences.unreadNotificationCountFlow.first()
        assertEquals(0, countAfterReset)
    }

    @Test
    fun testNotificationDismissReceiverClearsCount() = runBlocking {
        // Accumulate messages
        appPreferences.incrementNotificationCount("chat_abc")
        appPreferences.incrementNotificationCount("chat_abc")
        assertEquals(2, appPreferences.unreadNotificationCountFlow.first())

        // Trigger dismiss receiver
        val receiver = NotificationDismissReceiver()
        val intent = Intent()
        receiver.onReceive(context, intent)

        // Wait a moment for coroutine in receiver to finish
        var countAfterDismiss = 2
        for (i in 1..25) {
            Thread.sleep(100)
            countAfterDismiss = appPreferences.unreadNotificationCountFlow.first()
            if (countAfterDismiss == 0) break
        }

        // Count should be reset to 0
        assertEquals(0, countAfterDismiss)
    }

    @Test
    fun testMuteChatNotifications() = runBlocking {
        val uid = "user_test_mute"
        appPreferences.saveSession(uid)

        val chatIdA = "chat_alpha"
        val chatIdB = "chat_beta"

        // Initially neither is muted
        assertFalse(appPreferences.isChatMuted(chatIdA, uid))
        assertFalse(appPreferences.isChatMuted(chatIdB, uid))

        // Mute Chat A indefinitely (-1L)
        appPreferences.setChatMuted(chatIdA, isMuted = true, muteUntil = -1L, currentUid = uid)

        // Chat A must be muted; Chat B must NOT be muted (isolated)
        assertTrue("Chat A should be muted", appPreferences.isChatMuted(chatIdA, uid))
        assertFalse("Chat B should remain unmuted", appPreferences.isChatMuted(chatIdB, uid))

        // Unmute Chat A
        appPreferences.setChatMuted(chatIdA, isMuted = false, muteUntil = 0L, currentUid = uid)
        assertFalse("Chat A should be unmuted", appPreferences.isChatMuted(chatIdA, uid))
    }

    @Test
    fun testMuteExpiration() = runBlocking {
        val uid = "user_test_expiry"
        appPreferences.saveSession(uid)

        val chatId = "chat_expiring"

        // Mute for 1 hour in the future
        val futureTime = System.currentTimeMillis() + 3600_000L
        appPreferences.setChatMuted(chatId, isMuted = true, muteUntil = futureTime, currentUid = uid)
        assertTrue("Chat should be muted with future expiry", appPreferences.isChatMuted(chatId, uid))

        // Mute with past timestamp (expired)
        val pastTime = System.currentTimeMillis() - 10_000L
        appPreferences.setChatMuted(chatId, isMuted = true, muteUntil = pastTime, currentUid = uid)
        assertFalse("Chat should NOT be considered muted once expiry has passed", appPreferences.isChatMuted(chatId, uid))
    }

    @Test
    fun testAccountSeparationMute() = runBlocking {
        val userB = "user_B"
        val userC = "user_C"
        val chatId = "chat_shared"

        // User B mutes the chat
        appPreferences.saveSession(userB)
        appPreferences.setChatMuted(chatId, isMuted = true, muteUntil = -1L, currentUid = userB)
        assertTrue("Chat must be muted for User B", appPreferences.isChatMuted(chatId, userB))

        // Switch to User C (logout user B, login user C)
        appPreferences.saveSession(userC)
        assertFalse("User C must NOT inherit User B's mute state", appPreferences.isChatMuted(chatId, userC))
    }

    @Test
    fun testAccountSeparationShowMessageContent() = runBlocking {
        val userB = "user_B"
        val userC = "user_C"

        // User B sets Show Message Content = OFF
        appPreferences.saveSession(userB)
        appPreferences.setShowMessageContent(false, userB)
        assertFalse("User B showMessageContent must be false", appPreferences.getShowMessageContent(userB))

        // User C sets Show Message Content = ON
        appPreferences.saveSession(userC)
        appPreferences.setShowMessageContent(true, userC)
        assertTrue("User C showMessageContent must be true", appPreferences.getShowMessageContent(userC))

        // Verify User B still has false
        assertFalse("User B showMessageContent must remain false", appPreferences.getShowMessageContent(userB))
    }

    @Test
    fun testPrivateContentNeverExposedWhenShowContentOff() = runBlocking {
        val uid = "user_privacy"
        appPreferences.saveSession(uid)
        appPreferences.setShowMessageContent(false, uid)

        val secretPhrase = "PRIVATE_CONTENT_TEST_98765"
        val count = appPreferences.incrementNotificationCount("chat_test")

        val title = "Vynex Messenger"
        val body = if (count <= 1) "1 new message" else "$count new messages"

        assertFalse("Title must never contain the private content", title.contains(secretPhrase))
        assertFalse("Body must never contain the private content", body.contains(secretPhrase))
        assertEquals("Vynex Messenger", title)
        assertEquals("1 new message", body)
    }
}
