package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.local.AppPreferences
import com.example.data.model.Result
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.junit.Assert.*
import android.content.Context

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AuthFlowTest {

    private lateinit var authRepository: AuthRepositoryImpl
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.vynexmessenger.prod")
                .setApiKey("fake-api-key")
                .setProjectId("vynex-mess-app")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        val prefs = AppPreferences(context)
        authRepository = AuthRepositoryImpl(auth, firestore, prefs)
    }

    @Test
    fun testAuthenticationFlow() = runBlocking {
        val testUsername = "@testuser_${System.currentTimeMillis()}"
        val testPassword = "Password123!"
        val testDob = "22 Jun 2003"
        val testPin = "1234"

        println("Starting Test for username: $testUsername")

        // 1. Register
        val registerResult = authRepository.register(testUsername, testDob, testPassword, testPin)
        println("Register Result: $registerResult")
        assertTrue("Registration should succeed", registerResult is Result.Success)
        
        val uid = (registerResult as Result.Success).data.uid
        println("Registered UID: $uid")

        // 2. Verify Auth (Can't directly check Auth via SDK without Admin, but login success proves it)
        
        // 3. Verify Users Document
        val userDoc = firestore.collection("users").document(uid).get().kotlinAwait()
        println("Users document exists: ${userDoc.exists()}")
        assertTrue("Users document should exist", userDoc.exists())
        assertEquals("Username should match", testUsername.lowercase(), userDoc.getString("username"))

        // 4. Verify Usernames Document
        val usernameDoc = firestore.collection("usernames").document(testUsername.removePrefix("@").lowercase()).get().kotlinAwait()
        println("Usernames document exists: ${usernameDoc.exists()}")
        assertTrue("Usernames document should exist", usernameDoc.exists())
        assertEquals("UID should match", uid, usernameDoc.getString("uid"))

        // 5. Logout
        auth.signOut()
        println("Logged out successfully")

        // 6. Login
        val loginResult = authRepository.login(testUsername, testPassword)
        println("Login Result: $loginResult")
        assertTrue("Login should succeed", loginResult is Result.Success)
    }

    // Helper to use await() inside tests easily if kotlinx-coroutines-play-services is missing or we just want a wrapper
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.kotlinAwait(): T {
        return com.google.android.gms.tasks.Tasks.await(this)
    }
}
