package com.jfrashu.taskchat.taskactivities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.chatactivities.ChatActivity
import com.jfrashu.taskchat.dataclasses.Task
import com.jfrashu.taskchat.groupactivities.GroupInfoActivity
import com.jfrashu.taskchat.groupchatactivities.GroupChatActivity

class TaskActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var searchAdapter: TaskAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchRecyclerView: RecyclerView
    private lateinit var searchBar: SearchBar
    private lateinit var searchView: SearchView
    private lateinit var noResultsText: TextView
    private var groupId: String? = null
    private var groupName: String? = null
    private var groupDescription: String? = null

    private var isAdmin: Boolean = false
    private var currentUserId: String? = null
    private var snapshotListener: ListenerRegistration? = null
    private var allTasks: List<Task> = emptyList()

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
        groupDescription = intent.getStringExtra("groupDescription")


        if (groupId == null || currentUserId == null) {
            showToast("Invalid group or user information")
            finish()
            return
        }

        setupUI()
        setupRecyclerView()
        setupSearch()
        fetchTasks()
    }

    private fun setupUI() {
        // Initialize views
        searchBar = findViewById(R.id.search_bar)
        searchView = findViewById(R.id.searchView)
        noResultsText = findViewById(R.id.noResultsText)

        val viewGroupName = findViewById<TextView>(R.id.groupTitle)
        val viewGroupDescriptor = findViewById<TextView>(R.id.groupDescription)
        viewGroupName.text = "Group: $groupName"
        viewGroupDescriptor.text =groupDescription



        // Handle menu button click
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

        // Assuming you have a groupId variable
        findViewById<MaterialCardView>(R.id.groupNavigationCard).setOnClickListener {
            val intent = Intent(this, GroupChatActivity::class.java).apply {
                putExtra("groupId", groupId)  // Pass the groupId to the next activity

//                putExtra("groupLastActivity",group.lastActivity)
            }
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        // Main RecyclerView setup
        recyclerView = findViewById(R.id.taskRecyclerView)
        taskAdapter = TaskAdapter(emptyList()) { task ->
            navigateToSpecificTask(task.taskId)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@TaskActivity)
            adapter = taskAdapter
        }

        // Search RecyclerView setup
        searchRecyclerView = findViewById(R.id.searchRecyclerView)
        searchAdapter = TaskAdapter(emptyList()) { task ->
            navigateToSpecificTask(task.taskId)
            searchView.hide()
        }
        searchRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TaskActivity)
            adapter = searchAdapter
        }
    }

    private fun setupSearch() {
        // Connect SearchView with SearchBar
        searchView.setupWithSearchBar(searchBar)

        // Setup search functionality
        searchView.editText.setOnEditorActionListener { textView, _, _ ->
            val query = textView.text.toString()
            filterTasks(query)
            false
        }

        // Add text change listener for real-time search
        searchView.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTasks(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle search view state changes
        searchView.addTransitionListener(object : SearchView.TransitionListener {
            override fun onStateChanged(
                searchView: SearchView,
                previousState: SearchView.TransitionState,
                newState: SearchView.TransitionState
            ) {
                when (newState) {
                    SearchView.TransitionState.SHOWING -> {
                        searchRecyclerView.isVisible = true
                    }
                    SearchView.TransitionState.HIDDEN -> {
                        searchRecyclerView.isVisible = false
                        noResultsText.visibility = View.GONE
                        taskAdapter.updateTasks(allTasks)
                    }
                    else -> {}
                }
            }
        })
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

    private fun filterTasks(query: String) {
        val filteredTasks = if (query.isEmpty()) {
            allTasks
        } else {
            allTasks.filter { task ->
                task.title.contains(query, ignoreCase = true) ||
                        task.description?.contains(query, ignoreCase = true) == true
            }
        }

        searchAdapter.updateTasks(filteredTasks)
        searchRecyclerView.isVisible = true

        // Show/hide no results message
        noResultsText.visibility = if (filteredTasks.isEmpty() && query.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun fetchTasks() {
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

                        allTasks = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject(Task::class.java)?.copy(taskId = doc.id)
                        } ?: emptyList()

                        taskAdapter.updateTasks(allTasks)

                        // Update search results if search is active
                        if (searchView.isShowing) {
                            val query = searchView.editText.text.toString()
                            filterTasks(query)
                        }

                        Log.d("TaskActivity", "Fetched ${allTasks.size} tasks")
                    }
                }
        }
    }

    private fun navigateToSpecificTask(taskId: String) {
        if (!isFinishing && !isDestroyed) {
            groupId?.let { gId ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("groupId", gId)
                    putExtra("taskId", taskId)
                }
                startActivity(intent)
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