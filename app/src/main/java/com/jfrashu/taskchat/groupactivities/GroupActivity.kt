package com.jfrashu.taskchat.groupactivities


import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.User
import com.jfrashu.taskchat.profileactivities.MyProfileInfoActivity

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.toObject
import com.jfrashu.taskchat.dataclasses.Group

class GroupActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)

        db = FirebaseFirestore.getInstance()
        // Initialize menu button
        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        menuButton.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.let { user ->
                fetchAndNavigateToProfile(user.uid)
            }
        }
        setupRecyclerView()
        fetchUserGroups()
    }
    private fun fetchAndNavigateToProfile(userId: String) {
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
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
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching profile: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        recyclerView =findViewById(R.id.groupRecyclerView)
        groupAdapter = GroupAdapter(emptyList()) { group ->
            // Handle group click
            // You can navigate to group details activity here
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupActivity)
            adapter = groupAdapter
        }
    }

    private fun fetchUserGroups() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            // First, create the groups collection if it doesn't exist
            createInitialGroup(user.uid)

            // Then fetch groups where the current user is a member
            db.collection("groups")
                .whereArrayContains("members", user.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Toast.makeText(this, "Error fetching groups: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val groups = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject<Group>()
                    } ?: emptyList()

                    groupAdapter.updateGroups(groups)
                }
        }
    }

    private fun createInitialGroup(userId: String) {
        // Check if groups collection exists
        db.collection("groups").limit(1).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // Create initial group
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
                            Toast.makeText(this, "Error creating initial group: ${e.message}",
                                Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }
}