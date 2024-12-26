package com.jfrashu.taskchat.groupactivities

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Group
import com.jfrashu.taskchat.dataclasses.User
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class GroupInfoActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var groupNameInput: TextInputEditText
    private lateinit var groupDescriptionInput: TextInputEditText
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var addMemberButton: MaterialButton
    private lateinit var saveGroupFab: ExtendedFloatingActionButton
    private lateinit var createdAtText: TextView
    private lateinit var lastActivityText: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var membersAdapter: GroupMembersAdapter

    private var groupId: String? = null
    private var isAdmin: Boolean = false
    private var currentGroup: Group? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_info)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()

        // Get group ID from intent
        groupId = intent.getStringExtra("groupId")
        isAdmin = intent.getBooleanExtra("isAdmin", false)

        if (groupId == null) {
            Toast.makeText(this, "Invalid group information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupMembersRecyclerView()
        fetchGroupInformation()
        setupButtonListeners()
    }

    private fun initializeViews() {
        groupNameInput = findViewById(R.id.groupNameInput)
        groupDescriptionInput = findViewById(R.id.groupDescriptionInput)
        membersRecyclerView = findViewById(R.id.membersRecyclerView)
        addMemberButton = findViewById(R.id.addMemberButton)
        saveGroupFab = findViewById(R.id.saveGroupFab)
        createdAtText = findViewById(R.id.createdAtText)
        lastActivityText = findViewById(R.id.lastActivityText)
        toolbar = findViewById(R.id.toolbar)

        // Set input fields enabled/disabled based on admin status
        groupNameInput.isEnabled = isAdmin
        groupDescriptionInput.isEnabled = isAdmin
        addMemberButton.isEnabled = isAdmin
        saveGroupFab.isEnabled = isAdmin

        if (!isAdmin) {
            saveGroupFab.hide()
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupMembersRecyclerView() {
        membersAdapter = GroupMembersAdapter(emptyList(), isAdmin) { userId ->
            // Handle member click - maybe show profile or remove member
            if (isAdmin) {
                // Implement remove member functionality
                removeMember(userId)
            }
        }
        membersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupInfoActivity)
            adapter = membersAdapter
        }
    }

    private fun fetchGroupInformation() {
        groupId?.let { gId ->
            db.collection("groups").document(gId)
                .get()
                .addOnSuccessListener { document ->
                    document.toObject(Group::class.java)?.let { group ->
                        currentGroup = group
                        updateUI(group)
                        fetchMembers(group.members)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error fetching group info: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUI(group: Group) {
        groupNameInput.setText(group.name)
        groupDescriptionInput.setText(group.description)

        // Format dates
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        createdAtText.text = "Created: ${dateFormat.format(Date(group.createdAt))}"

        // Format last activity as relative time
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            group.lastActivity,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        lastActivityText.text = "Last activity: $relativeTime"
    }

    private fun fetchMembers(memberIds: List<String>) {
        val membersList = mutableListOf<User>()
        var completedQueries = 0

        memberIds.forEach { userId ->
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    document.toObject(User::class.java)?.let { user ->
                        membersList.add(user)
                    }
                    completedQueries++

                    if (completedQueries == memberIds.size) {
                        membersAdapter.updateMembers(membersList)
                    }
                }
                .addOnFailureListener { e ->
                    completedQueries++
                    Toast.makeText(this, "Error fetching member: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupButtonListeners() {
//        addMemberButton.setOnClickListener {
//            val intent = Intent(this, AddMemberActivity::class.java).apply {
//                putExtra("groupId", groupId)
//            }
//            startActivity(intent)
//        }

        saveGroupFab.setOnClickListener {
            saveGroupChanges()
        }
    }

    private fun saveGroupChanges() {
        val newName = groupNameInput.text.toString().trim()
        val newDescription = groupDescriptionInput.text.toString().trim()

        if (newName.length < 3) {
            groupNameInput.error = "Group name must be at least 3 characters"
            return
        }

        if (newDescription.length < 10) {
            groupDescriptionInput.error = "Description must be at least 10 characters"
            return
        }

        currentGroup?.let { group ->
            val updatedGroup = group.copy(
                name = newName,
                description = newDescription,
                lastActivity = System.currentTimeMillis()
            )

            groupId?.let { gId ->
                db.collection("groups").document(gId)
                    .set(updatedGroup)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Group updated successfully",
                            Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error updating group: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun removeMember(userId: String) {
        currentGroup?.let { group ->
            if (group.members.size <= 1) {
                Toast.makeText(this, "Cannot remove the last member",
                    Toast.LENGTH_SHORT).show()
                return
            }

            if (userId == group.adminId) {
                Toast.makeText(this, "Cannot remove the group admin",
                    Toast.LENGTH_SHORT).show()
                return
            }

            val updatedMembers = group.members.filter { it != userId }

            groupId?.let { gId ->
                db.collection("groups").document(gId)
                    .update("members", updatedMembers)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Member removed successfully",
                            Toast.LENGTH_SHORT).show()
                        fetchGroupInformation() // Refresh the UI
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error removing member: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}