package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.local.AppPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import android.content.Context
import com.example.data.model.Result

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class RegisterVerificationTest2 {
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
    }

    @Test
    fun testRegisterAndVerifyFirestore() = runBlocking {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val appPrefs = AppPreferences(ApplicationProvider.getApplicationContext())
        val repo = AuthRepositoryImpl(auth, firestore, appPrefs)
        
        val username = "testverify_${System.currentTimeMillis()}"
        val email = "$username@vynex.local"
        val pwd = "Password123!"
        val pin = "1234"
        val dob = "22 Jun 2003"
        
        println("STARTING REGISTRATION FOR $username")
        val result = repo.register(username, dob, pwd, pin)
        
        if (result is Result.Success) {
            println("REGISTRATION SUCCESS: UID=${result.data.uid}")
        } else if (result is Result.Error) {
            println("REGISTRATION ERROR: ${result.message}")
            throw Exception(result.message)
        }
    }
}
