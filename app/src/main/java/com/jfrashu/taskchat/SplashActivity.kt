package com.jfrashu.taskchat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.jfrashu.taskchat.databinding.ActivitySplashBinding
import com.jfrashu.taskchat.groupactivities.GroupActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        checkAndUpdateFCMToken()

        Handler(Looper.getMainLooper()).postDelayed({
            checkUserLoginStatus()
        }, 2000)
    }

    private fun checkUserLoginStatus() {
        val currentUser = auth.currentUser

        val intent = when {
            currentUser != null && currentUser.isEmailVerified -> {
                Intent(this, GroupActivity::class.java)
            }
            else -> {
                Intent(this, WelcomeActivity::class.java)
            }
        }

        startActivity(intent)
        finish()
    }
    private fun checkAndUpdateFCMToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUser.uid)
                        .update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d("NOTIFICATIONS", "FCM Token updated on app start")
                        }
                }
        }
    }
}