package com.jfrashu.taskchat.taskactivities

import android.os.Bundle
import android.text.format.DateUtils
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Task

class TaskInfoActivity : AppCompatActivity() {
    private lateinit var taskTitleText: TextView
    private lateinit var statusChip: Chip
    private lateinit var statusToggleGroup: MaterialButtonToggleGroup
    private lateinit var descriptionText: TextView
    private lateinit var lastMessageText: TextView
    private lateinit var createdByText: TextView
    private lateinit var createdAtText: TextView
    private lateinit var lastActivityText: TextView
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_info)

        initializeViews()
        setupToolbar()
        setupStatusToggle()

        // Get task data from intent
        val taskId = intent.getStringExtra("taskId") ?: return
        loadTaskData(taskId)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        taskTitleText = findViewById(R.id.taskTitleText)
        statusChip = findViewById(R.id.statusChip)
        statusToggleGroup = findViewById(R.id.statusToggleGroup)
        descriptionText = findViewById(R.id.descriptionText)
        lastMessageText = findViewById(R.id.lastMessageText)
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

    private fun loadTaskData(taskId: String) {
        // Example using Firebase
        FirebaseFirestore.getInstance()
            .collection("tasks")
            .document(taskId)
            .get()
            .addOnSuccessListener { document ->
                document.toObject(Task::class.java)?.let { task ->
                    updateUI(task)
                }
            }
    }

    private fun updateUI(task: Task) {
        taskTitleText.text = task.title
        descriptionText.text = task.description
        lastMessageText.text = "Last message: ${task.lastMessage}"
        createdByText.text = "Created by: ${task.createdBy}"
        createdAtText.text = "Created: ${DateUtils.getRelativeTimeSpanString(task.createdAt)}"
        lastActivityText.text = "Last activity: ${DateUtils.getRelativeTimeSpanString(task.lastActivity)}"

        // Update status chip and toggle
        statusChip.text = task.status.replace("_", " ").capitalize()
        when (task.status) {
            "pending" -> statusToggleGroup.check(R.id.pendingButton)
            "in_progress" -> statusToggleGroup.check(R.id.inProgressButton)
            "completed" -> statusToggleGroup.check(R.id.completedButton)
        }
    }

    private fun updateTaskStatus(newStatus: String) {
        val taskId = intent.getStringExtra("taskId") ?: return

        FirebaseFirestore.getInstance()
            .collection("tasks")
            .document(taskId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "lastActivity" to System.currentTimeMillis()
                )
            )

        // Update status chip
        statusChip.text = newStatus.replace("_", " ").capitalize()
    }
}