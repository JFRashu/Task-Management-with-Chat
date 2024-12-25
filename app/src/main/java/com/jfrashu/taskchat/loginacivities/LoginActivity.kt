package com.jfrashu.taskchat.loginacivities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.WelcomeActivity
import com.jfrashu.taskchat.databinding.ActivityLoginBinding
import com.jfrashu.taskchat.registeractivities.RegisterStep1Activity
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase components
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("login_prefs", MODE_PRIVATE)

        setupTextWatchers()
        setupClickListeners()
        loadSavedCredentials()
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateFields()
            }
        }

        binding.apply {
            emailField.addTextChangedListener(textWatcher)
            passwordField.addTextChangedListener(textWatcher)
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            signinbtn.setOnClickListener {
                val email = emailField.text.toString().trim()
                val password = passwordField.text.toString()
                signIn(email, password)
            }

            register.setOnClickListener {
                startActivity(Intent(this@LoginActivity, RegisterStep1Activity::class.java))
            }
        }
    }

    private fun validateFields() {
        binding.apply {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()

            val isValid = email.isNotEmpty() &&
                    android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                    password.isNotEmpty() &&
                    password.length >= 6

            signinbtn.isEnabled = isValid
        }
    }

    private fun signIn(email: String, password: String) {
        binding.signinbtn.isEnabled = false // Disable button during authentication

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        if (user.isEmailVerified) {
                            // Save credentials if "Remember me" is checked
                            saveCredentialsIfRequested(email, password)

                            // Update user status to online
                            updateUserStatus(user.uid, "online")
                            showToast("You Have Successfully Logged In")
                            // Navigate to Welcome Activity
                            startActivity(Intent(this, WelcomeActivity::class.java))
                            finish()
                        } else {
                            showEmailVerificationDialog(user.email ?: "")
                            auth.signOut()
                        }
                    }
                } else {
                    showToast("Authentication failed: ${task.exception?.message}")
                    binding.signinbtn.isEnabled = true
                }
            }
    }

    private fun updateUserStatus(userId: String, status: String) {
        db.collection("users").document(userId)
            .update(mapOf(
                "status" to status,
                "lastActive" to System.currentTimeMillis()
            ))
    }

    private fun saveCredentialsIfRequested(email: String, password: String) {
        if (binding.rememberMeCheckbox.isChecked) {
            sharedPreferences.edit().apply {
                putString("saved_email", email)
                putString("saved_password", password)
                putBoolean("remember_me", true)
                apply()
            }
        } else {
            // Clear saved credentials if "Remember me" is unchecked
            clearSavedCredentials()
        }
    }

    private fun loadSavedCredentials() {
        if (sharedPreferences.getBoolean("remember_me", false)) {
            binding.apply {
                emailField.setText(sharedPreferences.getString("saved_email", ""))
                passwordField.setText(sharedPreferences.getString("saved_password", ""))
                rememberMeCheckbox.isChecked = true
            }
            validateFields()
        }
    }

    private fun clearSavedCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    private fun showEmailVerificationDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("Email Verification Required")
            .setMessage("Please verify your email address: $email\n\nDid not receive the verification email?")
            .setPositiveButton("Resend Email") { _, _ ->
                resendVerificationEmail()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showToast("Verification email sent")
                } else {
                    showToast("Failed to send verification email: ${task.exception?.message}")
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}