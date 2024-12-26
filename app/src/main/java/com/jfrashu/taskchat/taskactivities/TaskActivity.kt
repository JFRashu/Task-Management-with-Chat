package com.jfrashu.taskchat.taskactivities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.chatactivities.ChatActivity
import com.jfrashu.taskchat.dataclasses.Task
import com.jfrashu.taskchat.groupactivities.GroupInfoActivity

class TaskActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var recyclerView: RecyclerView
    private var groupId: String? = null
    private var groupName: String? = null
    private var isAdmin: Boolean = false
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        // Initialize Firebase and get current user
        db = FirebaseFirestore.getInstance()
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Get group information from intent
        groupId = intent.getStringExtra("groupId")
        groupName = intent.getStringExtra("groupName")
        isAdmin = intent.getBooleanExtra("isAdmin", false)

        if (groupId == null || currentUserId == null) {
            Toast.makeText(this, "Invalid group or user information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupRecyclerView()
        fetchTasks()
    }

    private fun setupUI() {
        // Handle menu button click to navigate to GroupInfoActivity
        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        menuButton.setOnClickListener {
            val intent = Intent(this, GroupInfoActivity::class.java).apply {
                putExtra("groupId", groupId)
                putExtra("groupName", groupName)
                putExtra("isAdmin", isAdmin)
            }
            startActivity(intent)
        }

        // Handle create task button - only visible to admin
        val createTaskBtn = findViewById<FloatingActionButton>(R.id.createTaskButton)
        if (isAdmin) {
            createTaskBtn.show()
            createTaskBtn.setOnClickListener {
                val intent = Intent(this, CreateTaskActivity::class.java).apply {
                    putExtra("groupId", groupId)
                }
                startActivity(intent)
            }
        } else {
            createTaskBtn.hide()
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.taskRecyclerView)
        taskAdapter = TaskAdapter(emptyList()) { task ->
            // Call navigateToSpecificTask when a task is clicked
            navigateToSpecificTask(task.taskId)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@TaskActivity)
            adapter = taskAdapter
        }
    }

    private fun navigateToSpecificTask(taskId: String) {
        groupId?.let { gId ->
            // Verify user has access to this task
            db.collection("groups").document(gId)
                .collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    documentSnapshot.toObject(Task::class.java)?.let { task ->
                        // Navigate to TaskInfoActivity with the task details
                        val intent = Intent(this, ChatActivity::class.java).apply {
                            putExtra("groupId", gId)  // Pass the groupId
                            putExtra("taskId", taskId)  // Pass the taskId
                        }
                        startActivity(intent)
                        startActivity(intent)
                    } ?: run {
                        Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error accessing task: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchTasks() {
        groupId?.let { gId ->
            db.collection("groups").document(gId)
                .collection("tasks")
                .orderBy("lastActivity", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Toast.makeText(this, "Error fetching tasks: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val tasks = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(taskId = doc.id)
                    } ?: emptyList()

                    taskAdapter.updateTasks(tasks)
                    Log.d("TaskActivity", "Fetched ${tasks.size} tasks, groupId: ${gId}") // Log the number of tasks
                    tasks.forEach { task -> // Log each task's details
                        Log.d("TaskActivity", "Task: ${task.title}, ${task.description}, ${task.status}, groupId: ${task.groupId}")
                    }
                }
        }

    }
    override fun onResume() {
        super.onResume()
        // Refresh tasks when returning to this activity
        fetchTasks()
    }
}