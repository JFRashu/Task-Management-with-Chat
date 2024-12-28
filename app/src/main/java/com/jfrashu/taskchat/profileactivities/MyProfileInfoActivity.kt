package com.jfrashu.taskchat.profileactivities

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.User
import com.jfrashu.taskchat.WelcomeActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.EmailAuthProvider

class MyProfileInfoActivity : AppCompatActivity() {
    private lateinit var displayNameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var statusChip: Chip
    private lateinit var lastActiveText: TextView
    private lateinit var saveFab: ExtendedFloatingActionButton
    private lateinit var logoutButton: MaterialButton
    private lateinit var auth: FirebaseAuth

    private lateinit var changePasswordButton: MaterialButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_profile_info)

        auth = FirebaseAuth.getInstance()
        initializeViews()
        loadUserData()
        setupSaveButton()
        setupLogoutButton()
        setupPasswordButtons()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }
    }

    private fun initializeViews() {
        displayNameInput = findViewById(R.id.displayNameInput)
        emailInput = findViewById(R.id.emailInput)
        statusChip = findViewById(R.id.statusChip)
        lastActiveText = findViewById(R.id.lastActiveText)
        saveFab = findViewById(R.id.saveFab)
        logoutButton = findViewById(R.id.logoutButton)
        changePasswordButton = findViewById(R.id.changePasswordButton)
    }
    private fun setupPasswordButtons() {
        if (isFinishing || isDestroyed) return

        changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }
    }

    private fun showChangePasswordDialog() {
        if (isFinishing || isDestroyed) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.currentPasswordInput)
        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.newPasswordInput)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change") { dialog, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (validatePasswordInputs(newPassword, confirmPassword)) {
                    changePassword(currentPassword, newPassword)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validatePasswordInputs(newPassword: String, confirmPassword: String): Boolean {
        if (newPassword.length < 6) {
            showToast("Password must be at least 6 characters")
            return false
        }

        if (newPassword != confirmPassword) {
            showToast("Passwords don't match")
            return false
        }

        return true
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        if (isFinishing || isDestroyed) return

        val user = auth.currentUser
        val email = user?.email

        if (user == null || email == null) {
            showToast("User not authenticated")
            return
        }

        // First, reauthenticate the user
        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                // Then change the password
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        if (isFinishing || isDestroyed) return@addOnSuccessListener
                        showToast("Password updated successfully")

                        // Log user out for security
                        updateUserStatus("offline")
                        auth.signOut()
                        clearUserSession()
                        navigateToWelcome()
                    }
                    .addOnFailureListener { e ->
                        if (isFinishing || isDestroyed) return@addOnFailureListener
                        showToast("Failed to update password: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                showToast("Current password is incorrect")
            }
    }

    private fun resetPassword() {
        if (isFinishing || isDestroyed) return

        val email = emailInput.text.toString()

        if (email.isEmpty()) {
            showToast("Please enter your email")
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                showToast("Password reset email sent")
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                showToast("Failed to send reset email: ${e.message}")
            }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }


    private fun setupLogoutButton() {
        logoutButton.text = "Logout" // Fix the button text
        logoutButton.setOnClickListener {
            updateUserStatus("offline")
            auth.signOut()
            clearUserSession()
            navigateToWelcome()
        }
    }

    private fun updateUserStatus(status: String) {
        val userId = auth.currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update(
                mapOf(
                    "status" to status,
                    "lastActive" to System.currentTimeMillis()
                )
            )
    }

    private fun clearUserSession() {
        getSharedPreferences("login_prefs", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun navigateToWelcome() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

//    private fun initializeViews() {
//        displayNameInput = findViewById(R.id.displayNameInput)
//        emailInput = findViewById(R.id.emailInput)
//        statusChip = findViewById(R.id.statusChip)
//        lastActiveText = findViewById(R.id.lastActiveText)
//        saveFab = findViewById(R.id.saveFab)
//    }

    private fun loadUserData() {
        intent.extras?.let { bundle ->
            displayNameInput.setText(bundle.getString("displayName"))
            emailInput.setText(bundle.getString("email"))
            statusChip.text = bundle.getString("status", "online")

            val lastActive = bundle.getLong("lastActive")
            lastActiveText.text = "Last active: ${getTimeAgo(lastActive)}"
        }
    }

    private fun setupSaveButton() {
        saveFab.setOnClickListener {
            val updatedUser = User(
                userId = intent.getStringExtra("userId") ?: "",
                email = emailInput.text.toString(),
                displayName = displayNameInput.text.toString(),
                status = statusChip.text.toString(),
                lastActive = System.currentTimeMillis()
            )

            updateUserProfile(updatedUser)
        }
    }

    private fun updateUserProfile(user: User) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.userId)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating profile: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val difference = System.currentTimeMillis() - timestamp
        return when {
            difference < 1000 * 60 -> "Just now"
            difference < 1000 * 60 * 60 -> "${difference / (1000 * 60)} minutes ago"
            difference < 1000 * 60 * 60 * 24 -> "${difference / (1000 * 60 * 60)} hours ago"
            else -> "${difference / (1000 * 60 * 60 * 24)} days ago"
        }
    }
}