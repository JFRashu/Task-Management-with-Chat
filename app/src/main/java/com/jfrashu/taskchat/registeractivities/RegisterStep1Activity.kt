package com.jfrashu.taskchat.registeractivities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.WelcomeActivity
import com.jfrashu.taskchat.databinding.ActivityRegisterStep1Binding
import com.jfrashu.taskchat.dataclasses.User
import com.jfrashu.taskchat.loginacivities.LoginActivity

class RegisterStep1Activity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStep1Binding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var verificationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStep1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupTextWatchers()
        setupClickListeners()
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
            namefield.addTextChangedListener(textWatcher)
            emailField.addTextChangedListener(textWatcher)
            contactField.addTextChangedListener(textWatcher)
            passwordField.addTextChangedListener(textWatcher)
            repasswordField.addTextChangedListener(textWatcher)
        }
    }

    private fun setupClickListeners() {
        binding.signinbtn.setOnClickListener {
            if (validateFields()) {
                registerUser()
            }
        }

        binding.register.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateFields(): Boolean {
        binding.apply {
            val name = namefield.text.toString().trim()
            val email = emailField.text.toString().trim()
            val contact = contactField.text.toString().trim()
            val password = passwordField.text.toString()
            val rePassword = repasswordField.text.toString()

            var isValid = true
            if (name.isEmpty()) {
                namefield.error = "Name is required"
                isValid = false
            } else {
                namefield.error = null
            }

            if (email.isEmpty()) {
                emailField.error = "Email is required"
                isValid = false
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailField.error = "Invalid email format"
                isValid = false
            } else {
                emailField.error = null
            }

            if (contact.isEmpty()) {
                contactField.error = "Contact number is required"
                isValid = false
            } else if (contact.length < 10) {
                contactField.error = "Contact number should be at least 10 digits"
                isValid = false
            } else {
                contactField.error = null
            }

            if (password.isEmpty()) {
                passwordField.error = "Password is required"
                isValid = false
            } else if (password.length < 8) {
                passwordField.error = "Password must be at least 8 characters"
                isValid = false
            } else {
                passwordField.error = null
            }

            if (rePassword.isEmpty()) {
                repasswordField.error = "Please confirm your password"
                isValid = false
            } else if (password != rePassword) {
                repasswordField.error = "Passwords do not match"
                isValid = false
            } else {
                repasswordField.error = null
            }

            signinbtn.isEnabled = isValid && !verificationInProgress
            return isValid
        }
    }

    private fun registerUser() {
        val email = binding.emailField.text.toString().trim()
        val password = binding.passwordField.text.toString()

        verificationInProgress = true
        binding.signinbtn.isEnabled = false
        binding.signinbtn.text = "Sending Verification..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    firebaseUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                showVerificationDialog(firebaseUser)
                            } else {
                                verificationInProgress = false
                                binding.signinbtn.text = "Next"
                                binding.signinbtn.isEnabled = true
                                showToast("Failed to send verification email: ${verificationTask.exception?.message}")
                            }
                        }
                } else {
                    verificationInProgress = false
                    binding.signinbtn.text = "Next"
                    binding.signinbtn.isEnabled = true
                    showToast("Registration failed: ${task.exception?.message}")
                }
            }
    }

    private fun showVerificationDialog(firebaseUser: FirebaseUser) {
        AlertDialog.Builder(this)
            .setTitle("Verify Your Email")
            .setMessage("Please check your email and click the verification link. Once verified, click 'I've Verified' to continue.")
            .setCancelable(false)
            .setPositiveButton("I've Verified") { _, _ ->
                checkVerificationAndProceed(firebaseUser)
            }
            .setNegativeButton("Resend Email") { dialog, _ ->
                firebaseUser.sendEmailVerification()
                    .addOnSuccessListener {
                        showToast("Verification email resent")
                    }
                    .addOnFailureListener {
                        showToast("Failed to resend email: ${it.message}")
                    }
                dialog.dismiss()
                showVerificationDialog(firebaseUser)
            }
            .show()
    }

    private fun checkVerificationAndProceed(firebaseUser: FirebaseUser) {
        firebaseUser.reload().addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                val reloadedUser = auth.currentUser
                if (reloadedUser?.isEmailVerified == true) {
                    saveUserToFirestore(reloadedUser)
                } else {
                    showToast("Email not verified yet")
                    showVerificationDialog(firebaseUser)
                }
            } else {
                showToast("Failed to check verification status: ${reloadTask.exception?.message}")
                showVerificationDialog(firebaseUser)
            }
        }
    }

    private fun saveUserToFirestore(firebaseUser: FirebaseUser) {
        val user = User(
            userId = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = binding.namefield.text.toString().trim(),
            status = "online",
            lastActive = System.currentTimeMillis()
        )

        db.collection("users")
            .document(firebaseUser.uid)
            .set(user)
            .addOnSuccessListener {
                showToast("Account created successfully!")
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                showToast("Failed to save user data: ${e.message}")
                verificationInProgress = false
                binding.signinbtn.text = "Next"
                binding.signinbtn.isEnabled = true
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
