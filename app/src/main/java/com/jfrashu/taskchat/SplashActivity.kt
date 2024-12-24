package com.jfrashu.taskchat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.jfrashu.taskchat.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Add a delay of 2 seconds (2000 milliseconds)
        Handler(Looper.getMainLooper()).postDelayed({
            // Start the next activity
            val intent = Intent(this, WelcomeActivity::class.java) // Replace 'NextActivity' with your target activity
            startActivity(intent)
            finish() // Close the WelcomeActivity to remove it from the back stack
        }, 2000) // 2000 milliseconds = 2 seconds
    }
}