package com.jfrashu.taskchat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.jfrashu.taskchat.databinding.ActivitySplashBinding
import com.jfrashu.taskchat.groupactivities.GroupActivity
import com.jfrashu.taskchat.loginacivities.LoginActivity

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

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
}