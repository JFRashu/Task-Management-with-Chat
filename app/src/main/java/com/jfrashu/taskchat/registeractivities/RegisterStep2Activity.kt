package com.jfrashu.taskchat.registeractivities

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
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
import com.jfrashu.taskchat.MainActivity
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ActivityRegisterStep2Binding
import java.util.concurrent.TimeUnit

// RegisterStep2Activity.kt
class RegisterStep2Activity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStep2Binding
    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private var resendEnabled = false
    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStep2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        verificationId = intent.getStringExtra("verificationId")

        setupClickListeners()
        startResendTimer()
    }

    private fun setupClickListeners() {
        binding.verifyButton.setOnClickListener {
            val otp = binding.otpField.text.toString().trim()
            if (otp.length == 6) {
                verifyPhoneNumberWithCode(otp)
            } else {
                binding.otpField.error = "Please enter valid OTP"
            }
        }

        binding.resendOtp.setOnClickListener {
            if (resendEnabled) {
                resendVerificationCode()
                startResendTimer()
            }
        }
    }

    private fun startResendTimer() {
        resendEnabled = false
        binding.resendOtp.setTextColor(Color.GRAY)

        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.resendOtp.text = "Resend OTP in ${millisUntilFinished / 1000} seconds"
            }

            override fun onFinish() {
                binding.resendOtp.text = "Resend OTP"
                binding.resendOtp.setTextColor(getColor(R.color.md_theme_onPrimaryFixedVariant))
                resendEnabled = true
            }
        }.start()
    }

    private fun verifyPhoneNumberWithCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Verifying OTP...")
        progressDialog.show()

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressDialog.dismiss()
                if (task.isSuccessful) {
                    // Get user details from intent
                    val name = intent.getStringExtra("name")
                    val email = intent.getStringExtra("email")
                    val contact = intent.getStringExtra("contact")

                    // Save user data and proceed
                    saveUserData(name, email, contact)
                } else {
                    Toast.makeText(this,
                        "Verification failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserData(name: String?, email: String?, contact: String?) {
        val user = auth.currentUser
        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "contact" to contact
        )

        user?.let {
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(it.uid)
                .setValue(userData)
                .addOnSuccessListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this,
                        "Failed to save user data: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun resendVerificationCode() {
        val phoneNumber = "+88${intent.getStringExtra("contact")}"
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@RegisterStep2Activity,
                        "Verification failed: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(
                    newVerificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    verificationId = newVerificationId
                    Toast.makeText(this@RegisterStep2Activity,
                        "OTP sent successfully",
                        Toast.LENGTH_SHORT).show()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}