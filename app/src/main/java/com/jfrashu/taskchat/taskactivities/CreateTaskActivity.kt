package com.jfrashu.taskchat.taskactivities

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
import com.jfrashu.taskchat.databinding.ActivityCreateTaskBinding
import com.jfrashu.taskchat.dataclasses.Task

class CreateTaskActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateTaskBinding
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private lateinit var groupId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId") ?: run {
            finish()
            return
        }

        setupToolbar()
        setupCreateButton()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupCreateButton() {
        binding.createTaskButton.setOnClickListener {
            val title = binding.taskTitleInput.text.toString().trim()
            val description = binding.taskDescriptionInput.text.toString().trim()

            if (validateInputs(title, description)) {
                createTask(title, description)
            }
        }
    }

    private fun validateInputs(title: String, description: String): Boolean {
        if (title.isEmpty()) {
            binding.taskTitleLayout.error = "Title is required"
            return false
        }
        if (description.isEmpty()) {
            binding.taskDescriptionLayout.error = "Description is required"
            return false
        }
        return true
    }

    private fun createTask(title: String, description: String) {
        val currentUser = auth.currentUser ?: return

        val task = Task(
            taskId = db.collection("tasks").document().id,
            groupId = groupId,
            title = title,
            description = description,
            createdBy = currentUser.uid,
            status = "pending"
        )

        db.collection("tasks")
            .document(task.taskId)
            .set(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Task created successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}