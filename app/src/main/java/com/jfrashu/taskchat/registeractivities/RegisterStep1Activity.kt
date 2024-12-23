package com.jfrashu.taskchat.registeractivities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ActivityRegisterStep1Binding
import com.jfrashu.taskchat.loginacivities.LoginActivity
import java.util.concurrent.TimeUnit

// RegisterStep1Activity.kt
// RegisterStep1Activity.kt
class RegisterStep1Activity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStep1Binding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStep1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        setupValidation()
        setupClickListeners()
        setupPhoneAuthCallbacks()
    }

    private fun setupValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateFields()
            }
        }

        binding.namefield.addTextChangedListener(textWatcher)
        binding.emailField.addTextChangedListener(textWatcher)
        binding.contactField.addTextChangedListener(textWatcher)
        binding.passwordField.addTextChangedListener(textWatcher)
        binding.repasswordField.addTextChangedListener(textWatcher)
    }

    private fun validateFields(): Boolean {
        val name = binding.namefield.text.toString().trim()
        val email = binding.emailField.text.toString().trim()
        val contact = binding.contactField.text.toString().trim()
        val password = binding.passwordField.text.toString()
        val repassword = binding.repasswordField.text.toString()

        var isValid = true

        if (name.isEmpty()) {
            binding.namefield.error = "Name is required"
            isValid = false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailField.error = "Valid email is required"
            isValid = false
        }

        if (contact.length < 10) {
            binding.contactField.error = "Valid contact number is required"
            isValid = false
        }

        if (password.length < 6) {
            binding.passwordField.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (password != repassword) {
            binding.repasswordField.error = "Passwords do not match"
            isValid = false
        }

        binding.signinbtn.isEnabled = isValid
        return isValid
    }

    private fun setupPhoneAuthCallbacks() {
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This will be called if verification is done automatically
                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Toast.makeText(this@RegisterStep1Activity,
                    "Verification failed: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                // Save verification ID and navigate to OTP screen
                val intent = Intent(this@RegisterStep1Activity, RegisterStep2Activity::class.java).apply {
                    putExtra("verificationId", verificationId)
                    putExtra("name", binding.namefield.text.toString().trim())
                    putExtra("email", binding.emailField.text.toString().trim())
                    putExtra("contact", binding.contactField.text.toString().trim())
                    putExtra("password", binding.passwordField.text.toString())
                }
                startActivity(intent)
            }
        }
    }

    private fun setupClickListeners() {
        binding.signinbtn.setOnClickListener {
            if (validateFields()) {
                startPhoneNumberVerification()
            }
        }

        binding.register.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun startPhoneNumberVerification() {
        val phoneNumber = "+91${binding.contactField.text.toString().trim()}" // Add country code
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    saveUserData()
                } else {
                    Toast.makeText(this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserData() {
        val user = auth.currentUser
        val userData = hashMapOf(
            "name" to binding.namefield.text.toString().trim(),
            "email" to binding.emailField.text.toString().trim(),
            "contact" to binding.contactField.text.toString().trim()
        )

        user?.let {
            database.reference.child("users").child(it.uid).setValue(userData)
                .addOnSuccessListener {
                    startActivity(Intent(this, RegisterStep2Activity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this,
                        "Failed to save user data: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
        }
    }
}

// RegisterStep2Activity.kt remains the same as before