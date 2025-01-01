package com.jfrashu.taskchat.loginacivities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.jfrashu.taskchat.databinding.ActivityLoginBinding
import com.jfrashu.taskchat.registeractivities.RegisterStep1Activity
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.jfrashu.taskchat.groupactivities.GroupActivity
import com.jfrashu.taskchat.network.NetworkUtils
import com.google.firebase.messaging.FirebaseMessaging

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
        saveFCMToken()
    }

    private fun saveFCMToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                userId?.let { uid ->
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d("NOTIFICATIONS", "FCM Token saved for user: $uid")
                        }
                        .addOnFailureListener { e ->
                            Log.e("NOTIFICATIONS", "Error saving FCM token: ${e.message}")
                        }
                }
            }
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
            // Add click listener for forgot password
            forgotPassword.setOnClickListener {
                showForgotPasswordDialog()
            }
        }
    }
    private fun showForgotPasswordDialog() {
        val emailInput = binding.emailField.text.toString().trim()

        if (emailInput.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("Please enter your email address in the email field")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("Send password reset email to: $emailInput?")
            .setPositiveButton("Send") { _, _ ->
                sendPasswordResetEmail(emailInput)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("No internet connection available")
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showToast("Password reset email sent to $email")
                } else {
                    when (task.exception) {
                        is FirebaseAuthInvalidUserException -> {
                            showToast("No user found with this email address")
                        }
                        else -> {
                            showToast("Failed to send reset email: ${task.exception?.message}")
                        }
                    }
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
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("No internet connection available")
            binding.signinbtn.isEnabled = true
            return
        }

        binding.signinbtn.isEnabled = false // Disable button during authentication

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    handleSuccessfulSignIn()
                } else {
                    handleSignInError(task.exception)
                }
                binding.signinbtn.isEnabled = true
            }
    }

    private fun updateUserStatus(userId: String, status: String) {
        db.collection("users").document(userId)
            .update(mapOf(
                "status" to status,
                "lastActive" to System.currentTimeMillis()
            ))
    }
    private fun handleSignInError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthInvalidUserException -> {
                showToast("Check email and password properly")

            }
            is FirebaseAuthInvalidCredentialsException -> {
                showToast("Check email and password properly")

            }
            else -> {
                showToast("Authentication failed: ${exception?.message}")
            }
        }
    }
    private fun handleSuccessfulSignIn() {
        val user = auth.currentUser
        if (user != null) {
            if (user.isEmailVerified) {
                saveCredentialsIfRequested(binding.emailField.text.toString().trim(),
                    binding.passwordField.text.toString())
                updateUserStatus(user.uid, "online")
                showToast("You Have Successfully Logged In")
                startActivity(Intent(this, GroupActivity::class.java))
                finish()
            } else {
                showEmailVerificationDialog(user.email ?: "")
                auth.signOut()
            }
        }
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
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("No internet connection available")
            return
        }

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