package com.jfrashu.taskchat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jfrashu.taskchat.databinding.ActivityWelcomeBinding
import com.jfrashu.taskchat.loginacivities.LoginActivity
import com.jfrashu.taskchat.registeractivities.RegisterStep1Activity

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set onClickListeners for the buttons
        binding.Loginbtn.setOnClickListener {
            // Redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        binding.registerbtn.setOnClickListener {
            // Redirect to RegisterActivity
            val intent = Intent(this, RegisterStep1Activity::class.java)
            startActivity(intent)
        }
    }
}
