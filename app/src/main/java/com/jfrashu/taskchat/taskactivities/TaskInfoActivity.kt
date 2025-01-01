package com.jfrashu.taskchat.taskactivities

import android.os.Bundle
import android.text.format.DateUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Task
import com.jfrashu.taskchat.dataclasses.User

class TaskInfoActivity : AppCompatActivity() {
    private lateinit var taskTitleText: TextView
    private lateinit var statusChip: Chip
    private lateinit var statusToggleGroup: MaterialButtonToggleGroup
    private lateinit var descriptionText: TextView
//    private lateinit var lastMessageText: TextView
    private lateinit var createdByText: TextView
    private lateinit var createdAtText: TextView
    private lateinit var lastActivityText: TextView
    private lateinit var toolbar: MaterialToolbar

    private var groupId: String? = null
    private var taskId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_info)

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
//        lastMessageText = findViewById(R.id.lastMessageText)
        createdByText = findViewById(R.id.createdByText)
        createdAtText = findViewById(R.id.createdAtText)
        lastActivityText = findViewById(R.id.lastActivityText)
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

    private fun updateUI(task: Task) {
        taskTitleText.text = task.title
        descriptionText.text = task.description
//        lastMessageText.text = "Last message: ${if (task.lastMessage.isNotEmpty()) task.lastMessage else "No messages yet"}"
        createdByText.text = "Created by: ${task.createdBy}" // This will be updated when user info is fetched
        createdAtText.text = "Created: ${DateUtils.getRelativeTimeSpanString(task.createdAt)}"
        lastActivityText.text = "Last activity: ${
            task.lastActivity?.let { timestamp ->
                DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } ?: "-"
        }"

        // Update status chip and toggle
        statusChip.text = task.status.replace("_", " ").replaceFirstChar { it.uppercase() }
        when (task.status) {
            "pending" -> statusToggleGroup.check(R.id.pendingButton)
            "in_progress" -> statusToggleGroup.check(R.id.inProgressButton)
            "completed" -> statusToggleGroup.check(R.id.completedButton)
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