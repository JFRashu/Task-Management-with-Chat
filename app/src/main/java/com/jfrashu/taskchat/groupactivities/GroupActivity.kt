package com.jfrashu.taskchat.groupactivities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Group
import com.jfrashu.taskchat.dataclasses.User
import com.jfrashu.taskchat.profileactivities.MyProfileInfoActivity
import com.jfrashu.taskchat.taskactivities.TaskActivity

class GroupActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var recyclerView: RecyclerView
    private var snapshotListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)

        db = FirebaseFirestore.getInstance()
        setupUI()
        setupRecyclerView()
        fetchUserGroups()
    }

    private fun setupUI() {
        // Initialize menu button
        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        menuButton.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.let { user ->
                fetchAndNavigateToProfile(user.uid)
            }
        }

        // Create group button
        val createGroupBtn = findViewById<FloatingActionButton>(R.id.createGroupbtn)
        createGroupBtn.setOnClickListener {
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, CreateGroupActivity::class.java))
            }
        }

        // Group requests button
        val seeRequestsBtn = findViewById<ImageButton>(R.id.groupRequestsButton)
        seeRequestsBtn.setOnClickListener {
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, GroupInvitationActivity::class.java))
            }
        }
    }

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

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.groupRecyclerView)
        groupAdapter = GroupAdapter(emptyList()) { group ->
            if (!isFinishing && !isDestroyed) {
                val intent = Intent(this, TaskActivity::class.java).apply {
                    putExtra("groupId", group.groupId)
                    putExtra("groupName", group.name)
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    putExtra("isAdmin", currentUserId == group.adminId)
                }
                startActivity(intent)
            }
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupActivity)
            adapter = this@GroupActivity.groupAdapter
        }
    }

    private fun fetchUserGroups() {
        // Remove previous listener if it exists
        snapshotListener?.remove()

        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            createInitialGroup(user.uid)

            snapshotListener = db.collection("groups")
                .whereArrayContains("members", user.uid)
                .addSnapshotListener { snapshot, e ->
                    if (!isFinishing && !isDestroyed) {
                        if (e != null) {
                            showToast("Error fetching groups: ${e.message}")
                            return@addSnapshotListener
                        }

                        val groups = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject<Group>()
                        } ?: emptyList()

                        groupAdapter.updateGroups(groups)
                    }
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
                            members = listOf(userId)
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

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && !isDestroyed) {
            setupRecyclerView()
            fetchUserGroups()
        }
    }
}