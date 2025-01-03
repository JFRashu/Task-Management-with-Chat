package com.jfrashu.taskchat.taskactivities

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Task
import com.jfrashu.taskchat.dataclasses.User

class TaskInfoActivity : AppCompatActivity() {
    private lateinit var taskTitleText: TextView
    private lateinit var statusChip: Chip
    private lateinit var statusToggleGroup: MaterialButtonToggleGroup
    private lateinit var descriptionText: TextView
    private lateinit var createdByText: TextView
    private lateinit var createdAtText: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var deleteTaskButton: MaterialButton

    private var groupId: String? = null
    private var taskId: String? = null
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_info)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        groupId = intent.getStringExtra("groupId")
        taskId = intent.getStringExtra("taskId")

        if (groupId == null || taskId == null) {
            Toast.makeText(this, "Error: Task information not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupStatusToggle()
        loadTaskData(groupId!!, taskId!!)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        taskTitleText = findViewById(R.id.taskTitleText)
        statusChip = findViewById(R.id.statusChip)
        statusToggleGroup = findViewById(R.id.statusToggleGroup)
        descriptionText = findViewById(R.id.descriptionText)
        createdByText = findViewById(R.id.createdByText)
        createdAtText = findViewById(R.id.createdAtText)
        deleteTaskButton = findViewById(R.id.deleteTaskButton)

        // Initially hide delete button until we verify creator status
        deleteTaskButton.isVisible = false

        // Setup delete button click listener
        deleteTaskButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete this task?")
            .setPositiveButton("Yes") { _, _ ->
                markTaskAsDeleted()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun markTaskAsDeleted() {
        val groupId = this.groupId ?: return
        val taskId = this.taskId ?: return

        FirebaseFirestore.getInstance()
            .collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "lastActivity" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Task deleted successfully", Toast.LENGTH_SHORT).show()

                // Create new intent for TaskActivity
                val intent = Intent(this, TaskActivity::class.java)
                intent.putExtra("groupId", groupId)
                // Clear all activities above it in the stack
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK

                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI(task: Task) {
        taskTitleText.text = task.title
        descriptionText.text = task.description
        createdByText.text = "Created by: ${task.createdBy}" // This will be updated when user info is fetched
        createdAtText.text = "Created: ${DateUtils.getRelativeTimeSpanString(task.createdAt)}"

        // Update status chip and toggle
        statusChip.text = task.status.replace("_", " ").replaceFirstChar { it.uppercase() }
        when (task.status) {
            "pending" -> statusToggleGroup.check(R.id.pendingButton)
            "in_progress" -> statusToggleGroup.check(R.id.inProgressButton)
            "completed" -> statusToggleGroup.check(R.id.completedButton)
        }

        // Show delete button only if current user is the task creator
        deleteTaskButton.isVisible = currentUserId == task.createdBy
    }


    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupStatusToggle() {
        statusToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val newStatus = when (checkedId) {
                    R.id.pendingButton -> "pending"
                    R.id.inProgressButton -> "in_progress"
                    R.id.completedButton -> "completed"
                    else -> return@addOnButtonCheckedListener
                }
                updateTaskStatus(newStatus)
            }
        }
    }

    private fun loadTaskData(groupId: String, taskId: String) {
        FirebaseFirestore.getInstance()
            .collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    document.toObject(Task::class.java)?.let { task ->
                        // First update UI with available task data
                        updateUI(task)
                        // Then fetch creator's information
                        fetchCreatorInfo(task.createdBy)
                    }
                } else {
                    Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading task: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun fetchCreatorInfo(creatorId: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(creatorId)
            .get()
            .addOnSuccessListener { document ->
                document.toObject(User::class.java)?.let { user ->
                    // Update the creator text with the user's display name
                    createdByText.text = "Created by: ${user.displayName}"
                }
            }
            .addOnFailureListener { e ->
                // If we fail to get the user info, leave the creator ID as is
                Toast.makeText(this, "Error loading creator info: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }




    private fun updateTaskStatus(newStatus: String) {
        val groupId = this.groupId ?: return
        val taskId = this.taskId ?: return

        FirebaseFirestore.getInstance()
            .collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "lastActivity" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                statusChip.text = newStatus.replace("_", " ").replaceFirstChar { it.uppercase() }
                Toast.makeText(this, "Status updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
                loadTaskData(groupId, taskId)
            }
    }
}