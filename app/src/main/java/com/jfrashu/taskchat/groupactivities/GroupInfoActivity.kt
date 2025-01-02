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
import androidx.core.view.isVisible
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import androidx.appcompat.app.AlertDialog

class GroupInfoActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var groupNameInput: TextInputEditText
    private lateinit var groupDescriptionInput: TextInputEditText
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var addMemberButton: MaterialButton
    private lateinit var saveGroupFab: ExtendedFloatingActionButton
    private lateinit var createdAtText: TextView
    private lateinit var lastActivityText: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var membersAdapter: GroupMembersAdapter

    private lateinit var deleteGroupButton: MaterialButton

    private var groupId: String? = null
    private var currentUserId: String = ""
    private var currentGroup: Group? = null

    private var groupListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_info)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: run {
            showToast("User not authenticated")
            finish()
            return
        }

        // Get group ID from intent
        groupId = intent.getStringExtra("groupId") ?: run {
            showToast("Invalid group information")
            finish()
            return
        }

        initializeViews()
        fetchGroupInformation()
    }

    private fun fetchGroupInformation() {
        if (isFinishing || isDestroyed) return

        // Remove any existing listener
        groupListener?.remove()

        groupId?.let { gId ->
            groupListener = db.collection("groups").document(gId)
                .addSnapshotListener { document, error ->
                    if (isFinishing || isDestroyed) return@addSnapshotListener

                    if (error != null) {
                        showToast("Error fetching group info: ${error.message}")
                        return@addSnapshotListener
                    }

                    document?.toObject(Group::class.java)?.let { group ->
                        currentGroup = group
                        updateUI(group)
                        setupMembersRecyclerView(group)
                        fetchMembers(group.members)
                        updateAdminControls(group.adminId == currentUserId)
                    }
                }
        }
    }

    private fun fetchMembers(memberIds: List<String>) {
        if (isFinishing || isDestroyed) return
        if (memberIds.isEmpty()) {
            membersAdapter.updateMembers(emptyList())
            return
        }

        db.collection("users")
            .whereIn("userId", memberIds)
            .get()
            .addOnSuccessListener { documents ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val members = documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)
                }
                membersAdapter.updateMembers(members)
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                showToast("Error fetching members: ${e.message}")
            }
    }

    private fun saveGroupChanges() {
        if (isFinishing || isDestroyed) return

        val newName = groupNameInput.text.toString().trim()
        val newDescription = groupDescriptionInput.text.toString().trim()

        // Validate inputs
        if (newName.length < 3) {
            groupNameInput.error = "Group name must be at least 3 characters"
            return
        }

        if (newDescription.length < 10) {
            groupDescriptionInput.error = "Description must be at least 10 characters"
            return
        }

        currentGroup?.let { group ->
            if (group.adminId != currentUserId) {
                showToast("Only admin can modify group details")
                return
            }

            val updatedGroup = group.copy(
                name = newName,
                description = newDescription,
                lastActivity = System.currentTimeMillis()
            )

            groupId?.let { gId ->
                db.collection("groups").document(gId)
                    .set(updatedGroup)
                    .addOnSuccessListener {
                        if (isFinishing || isDestroyed) return@addOnSuccessListener
                        showToast("Group updated successfully")
                    }
                    .addOnFailureListener { e ->
                        if (isFinishing || isDestroyed) return@addOnFailureListener
                        showToast("Error updating group: ${e.message}")
                    }
            }
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        groupListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && !isDestroyed) {
            fetchGroupInformation()
        }
    }


    private fun initializeViews() {
        // Initialize views
        groupNameInput = findViewById(R.id.groupNameInput)
        groupDescriptionInput = findViewById(R.id.groupDescriptionInput)
        membersRecyclerView = findViewById(R.id.membersRecyclerView)
        addMemberButton = findViewById(R.id.addMemberButton)
        saveGroupFab = findViewById(R.id.saveGroupFab)
        createdAtText = findViewById(R.id.createdAtText)
        lastActivityText = findViewById(R.id.lastActivityText)
        toolbar = findViewById(R.id.toolbar)
        // Add this with other view initializations
        deleteGroupButton = findViewById(R.id.deleteGroupButton)

        // Initially hide the delete button until we verify admin status
        deleteGroupButton.isVisible = false

        // Setup delete button click listener
        deleteGroupButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Setup toolbar
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Initially disable all admin controls until we verify admin status
        groupNameInput.isEnabled = false
        groupDescriptionInput.isEnabled = false
        addMemberButton.isEnabled = false
        saveGroupFab.isVisible = false

        // Setup button listeners
        addMemberButton.setOnClickListener {
            startAddMemberActivity()
        }

        saveGroupFab.setOnClickListener {
            saveGroupChanges()
        }
    }
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete this group?")
            .setPositiveButton("Yes") { _, _ ->
                markGroupAsDeleted()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun markGroupAsDeleted() {
        currentGroup?.let { group ->
            if (group.adminId != currentUserId) {
                showToast("Only admin can delete the group")
                return
            }

            groupId?.let { gId ->
                db.collection("groups").document(gId)
                    .update(
                        mapOf(
                            "isDeleted" to true,
                            "lastActivity" to System.currentTimeMillis()
                        )
                    )
                    .addOnSuccessListener {
                        showToast("Group deleted successfully")

                        // Create new intent for GroupActivity
                        val intent = Intent(this, GroupActivity::class.java)
                        // Clear all activities above it in the stack
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                        finish() // Finish current activity
                    }
                    .addOnFailureListener { e ->
                        showToast("Error deleting group: ${e.message}")
                    }
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

    private fun setupMembersRecyclerView(group: Group) {
        val isAdmin = group.adminId == currentUserId

        membersAdapter = GroupMembersAdapter(
            members = emptyList(),
            currentUserIsAdmin = isAdmin,
            groupAdminId = group.adminId
        ) { userId ->
            // This is the member removal callback
            removeMember(userId)
        }

        membersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupInfoActivity)
            adapter = membersAdapter
        }
    }

    private fun updateAdminControls(isAdmin: Boolean) {
        groupNameInput.isEnabled = isAdmin
        groupDescriptionInput.isEnabled = isAdmin
        addMemberButton.isEnabled = isAdmin
        saveGroupFab.isVisible = isAdmin
        deleteGroupButton.isVisible = isAdmin
    }


    private fun startAddMemberActivity() {
        groupId?.let { gId ->
            val intent = Intent(this, AddMemberActivity::class.java).apply {
                putExtra("groupId", gId)
            }
            startActivity(intent)
        }
    }

    private fun removeMember(userId: String) {
        currentGroup?.let { group ->
            // Validate removal
            if (group.adminId != currentUserId) {
                Toast.makeText(this, "Only admin can remove members",
                    Toast.LENGTH_SHORT).show()
                return
            }

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
                    .update(
                        mapOf(
                            "members" to updatedMembers,
                            "lastActivity" to System.currentTimeMillis()
                        )
                    )
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