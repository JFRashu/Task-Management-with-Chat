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

            val isValid = name.isNotEmpty() &&
                    email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                    contact.isNotEmpty() && contact.length >= 10 &&
                    password.isNotEmpty() && password.length >= 6 &&
                    rePassword.isNotEmpty() && password == rePassword

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
                                showToast("Failed to send verification email")
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
                    // Email is verified, create user account
                    saveUserToFirestore(reloadedUser)
                } else {
                    showToast("Email not verified yet")
                    showVerificationDialog(firebaseUser)
                }
            } else {
                showToast("Failed to check verification status")
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
                // Proceed to Welcome Activity
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