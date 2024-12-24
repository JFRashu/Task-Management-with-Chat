package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ActivityCreateGroupBinding
import com.jfrashu.taskchat.dataclasses.Group

class CreateGroupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateGroupBinding
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCreateButton()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupCreateButton() {
        binding.createButton.setOnClickListener {
            val name = binding.groupNameInput.text.toString().trim()
            val description = binding.descriptionInput.text.toString().trim()

            if (validateInputs(name, description)) {
                createGroup(name, description)
            }
        }
    }

    private fun validateInputs(name: String, description: String): Boolean {
        if (name.isEmpty()) {
            binding.groupNameLayout.error = "Group name is required"
            return false
        }
        if (description.isEmpty()) {
            binding.descriptionLayout.error = "Description is required"
            return false
        }
        return true
    }

    private fun createGroup(name: String, description: String) {
        val currentUser = auth.currentUser ?: return

        val group = Group(
            groupId = db.collection("groups").document().id,
            name = name,
            description = description,
            adminId = currentUser.uid,
            members = listOf(currentUser.uid)
        )

        db.collection("groups")
            .document(group.groupId)
            .set(group)
            .addOnSuccessListener {
                Toast.makeText(this, "Group created successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create group: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}