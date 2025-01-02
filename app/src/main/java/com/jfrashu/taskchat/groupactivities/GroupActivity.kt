package com.jfrashu.taskchat.groupactivities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Group
import com.jfrashu.taskchat.dataclasses.User
import com.jfrashu.taskchat.groupchatactivities.GroupChatActivity
import com.jfrashu.taskchat.profileactivities.MyProfileInfoActivity
import com.jfrashu.taskchat.taskactivities.TaskActivity

class GroupActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var searchAdapter: GroupAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchRecyclerView: RecyclerView
    private lateinit var searchBar: SearchBar
    private lateinit var searchView: SearchView
    private lateinit var progressIndicator: CircularProgressIndicator
    private var snapshotListener: ListenerRegistration? = null
    private var allGroups: List<Group> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)

        db = FirebaseFirestore.getInstance()
        setupUI()
        setupRecyclerView()
        setupSearch()
        fetchUserGroups()
    }

    private fun setupUI() {
        searchBar = findViewById(R.id.search_bar)
        searchView = findViewById(R.id.searchView)
        progressIndicator = findViewById(R.id.progressIndicator)

        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        menuButton.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.let { user ->
                fetchAndNavigateToProfile(user.uid)
            }
        }

        val createGroupBtn = findViewById<FloatingActionButton>(R.id.createGroupbtn)
        createGroupBtn.setOnClickListener {
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, CreateGroupActivity::class.java))
            }
        }

        val seeRequestsBtn = findViewById<ImageButton>(R.id.groupRequestsButton)
        seeRequestsBtn.setOnClickListener {
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, GroupInvitationActivity::class.java))
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.groupRecyclerView)
        groupAdapter = GroupAdapter(emptyList()) { group ->
            navigateToGroup(group)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupActivity)
            adapter = groupAdapter
        }

        searchRecyclerView = findViewById(R.id.searchRecyclerView)
        searchAdapter = GroupAdapter(emptyList()) { group ->
            navigateToGroup(group)
            searchView.hide()
        }
        searchRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupActivity)
            adapter = searchAdapter
        }
    }

    private fun navigateToGroup(group: Group) {
        if (!isFinishing && !isDestroyed) {
            val intent = Intent(this, TaskActivity::class.java).apply {
                putExtra("groupId", group.groupId)
                putExtra("groupName", group.name)
                putExtra("groupDescription", group.description)
                putExtra("groupLastActivity", group.lastActivity)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                putExtra("isAdmin", currentUserId == group.adminId)
            }
            startActivity(intent)
        }
    }

    private fun setupSearch() {
        searchView.setupWithSearchBar(searchBar)

        searchView.editText.setOnEditorActionListener { textView, _, _ ->
            val query = textView.text.toString()
            filterGroups(query)
            false
        }

        searchView.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterGroups(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchView.addTransitionListener { searchView, previousState, newState ->
            when (newState) {
                SearchView.TransitionState.SHOWING -> {
                    searchRecyclerView.isVisible = true
                }
                SearchView.TransitionState.HIDDEN -> {
                    searchRecyclerView.isVisible = false
                    groupAdapter.updateGroups(allGroups)
                }
                else -> {}
            }
        }
    }

    private fun filterGroups(query: String) {
        val filteredGroups = if (query.isEmpty()) {
            allGroups
        } else {
            allGroups.filter { group ->
                group.name.contains(query, ignoreCase = true)
            }
        }

        searchAdapter.updateGroups(filteredGroups)
        searchRecyclerView.isVisible = true

        if (filteredGroups.isEmpty() && query.isNotEmpty()) {
            showToast("No Groups Found")
        }
    }

    private fun fetchUserGroups() {
        progressIndicator.isVisible = true
        snapshotListener?.remove()

        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            snapshotListener = db.collection("groups")
                .whereArrayContains("members", user.uid)
                .orderBy("lastActivity", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (!isFinishing && !isDestroyed) {
                        progressIndicator.isVisible = false

                        if (e != null) {
                            Log.e("GroupFetch", "Error fetching groups", e)
                            showToast("Error fetching groups: ${e.message}")
                            return@addSnapshotListener
                        }

                        try {
                            val processedGroups = mutableListOf<Group>()

                            snapshot?.documents?.forEach { doc ->
                                try {
                                    // First check the raw isDeleted value
                                    val rawIsDeleted = doc.getBoolean("isDeleted") ?: false

                                    if (!rawIsDeleted) {
                                        val group = doc.toObject<Group>()
                                        if (group != null) {
                                            processedGroups.add(group)
                                            Log.d("GroupFetch", "Added group: ${group.groupId}, name: ${group.name}, isDeleted: ${group.isDeleted}")
                                        }
                                    } else {
                                        Log.d("GroupFetch", "Skipped deleted group: ${doc.id}")
                                    }
                                } catch (docEx: Exception) {
                                    Log.e("GroupFetch", "Error processing document: ${doc.id}", docEx)
                                }
                            }

                            allGroups = processedGroups
                            Log.d("GroupFetch", "Final groups count: ${allGroups.size}")

                            groupAdapter.updateGroups(allGroups)

                            if (searchView.isShowing) {
                                val query = searchView.editText.text.toString()
                                filterGroups(query)
                            }

                        } catch (ex: Exception) {
                            Log.e("GroupFetch", "Error processing groups", ex)
                            showToast("Error processing groups: ${ex.message}")
                        }
                    }
                }
        } ?: run {
            Log.e("GroupFetch", "No user logged in")
            progressIndicator.isVisible = false
            showToast("Please log in to view groups")
        }
    }

    // Helper function to show toast messages


//    // Helper function to filter groups based on search query
//    private fun filterGroups(query: String) {
//        val filteredGroups = if (query.isEmpty()) {
//            allGroups
//        } else {
//            allGroups.filter { group ->
//                group.name.contains(query, ignoreCase = true) ||
//                        group.description.contains(query, ignoreCase = true)
//            }
//        }
//        groupAdapter.updateGroups(filteredGroups)
//    }


    private fun fetchAndNavigateToProfile(userId: String) {
        if (!isFinishing && !isDestroyed) {
            db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (!isFinishing && !isDestroyed) {
                        document?.toObject(User::class.java)?.let { user ->
                            startActivity(Intent(this, MyProfileInfoActivity::class.java).apply {
                                putExtra("userId", user.userId)
                                putExtra("email", user.email)
                                putExtra("displayName", user.displayName)
                                putExtra("status", user.status)
                                putExtra("lastActive", user.lastActive)
                            })
                        }
                    }
                }
                .addOnFailureListener { e ->
                    showToast("Error fetching profile: ${e.message}")
                }
        }
    }

    private fun createInitialGroup(userId: String) {
        if (!isFinishing && !isDestroyed) {
            db.collection("groups").limit(1).get()
                .addOnSuccessListener { snapshot ->
                    if (!isFinishing && !isDestroyed && snapshot.isEmpty) {
                        val initialGroup = Group(
                            groupId = db.collection("groups").document().id,
                            name = "My First Group",
                            description = "Welcome to your first group!",
                            adminId = userId,
                            members = listOf(userId),
                            isDeleted = false
                        )

                        db.collection("groups")
                            .document(initialGroup.groupId)
                            .set(initialGroup)
                            .addOnFailureListener { e ->
                                showToast("Error creating initial group: ${e.message}")
                            }
                    }
                }
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && !isDestroyed) {
            setupRecyclerView()
            fetchUserGroups()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }
}