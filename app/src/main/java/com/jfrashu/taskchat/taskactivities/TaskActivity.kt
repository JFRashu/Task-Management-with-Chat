package com.jfrashu.taskchat.taskactivities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private var snapshotListener: ListenerRegistration? = null

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
            showToast("Invalid group or user information")
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
            navigateToSpecificTask(task.taskId)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@TaskActivity)
            adapter = taskAdapter
        }
    }

    private fun navigateToSpecificTask(taskId: String) {
        if (!isFinishing && !isDestroyed) {
            groupId?.let { gId ->
                db.collection("groups").document(gId)
                    .collection("tasks")
                    .document(taskId)
                    .get()
                    .addOnSuccessListener { documentSnapshot ->
                        if (!isFinishing && !isDestroyed) {
                            documentSnapshot.toObject(Task::class.java)?.let { task ->
                                val intent = Intent(this, ChatActivity::class.java).apply {
                                    putExtra("groupId", gId)
                                    putExtra("taskId", taskId)
                                }
                                startActivity(intent)
                            } ?: run {
                                showToast("Task not found")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        showToast("Error accessing task: ${e.message}")
                    }
            }
        }
    }

    private fun fetchTasks() {
        // Remove previous listener if it exists
        snapshotListener?.remove()

        groupId?.let { gId ->
            snapshotListener = db.collection("groups").document(gId)
                .collection("tasks")
                .orderBy("lastActivity", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (!isFinishing && !isDestroyed) {
                        if (e != null) {
                            showToast("Error fetching tasks: ${e.message}")
                            return@addSnapshotListener
                        }

                        val tasks = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject(Task::class.java)?.copy(taskId = doc.id)
                        } ?: emptyList()

                        taskAdapter.updateTasks(tasks)
                        Log.d("TaskActivity", "Fetched ${tasks.size} tasks")
                    }
                }
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && !isDestroyed) {
            fetchTasks()
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (!isFinishing && !isDestroyed) {
            setupUI()
            setupRecyclerView()
            fetchTasks()
        }
    }
}