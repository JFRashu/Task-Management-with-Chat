package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.jfrashu.taskchat.databinding.ActivityCreateGroupBinding
import com.jfrashu.taskchat.dataclasses.Group
import com.jfrashu.taskchat.dataclasses.GroupInvitation
import com.jfrashu.taskchat.dataclasses.User
import com.jfrashu.taskchat.users.UserAdapter

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private lateinit var userAdapter: UserAdapter
    private var allUsers = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupUserSearch()
        setupCreateButton()
        clearErrors()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter { user ->
            binding.createButton.isEnabled = userAdapter.getSelectedUsers().isNotEmpty()
            updateSelectedUsersCount()
        }

        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CreateGroupActivity)
            adapter = userAdapter
        }

        loadAllUsers()
    }

    private fun updateSelectedUsersCount() {
        val selectedCount = userAdapter.getSelectedUsers().size
        binding.selectedUsersCount.text = "Selected users: $selectedCount"
        binding.selectedUsersCount.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
    }

    private fun setupUserSearch() {
        binding.searchUserInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchText = s?.toString()?.trim()?.lowercase() ?: ""
                filterUsers(searchText)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAllUsers() {
        val currentUserId = auth.currentUser?.uid ?: return
        binding.loadingIndicator.visibility = View.VISIBLE

        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                allUsers = documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)
                }.filter { it.userId != currentUserId }.toMutableList()

                updateUsersList(allUsers)
                binding.loadingIndicator.visibility = View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users: ${it.message}", Toast.LENGTH_SHORT).show()
                binding.loadingIndicator.visibility = View.GONE
            }
    }

    private fun filterUsers(query: String) {
        if (query.isEmpty()) {
            updateUsersList(allUsers)
            return
        }

        val filteredUsers = allUsers.filter { user ->
            user.email.lowercase().contains(query) || user.displayName.lowercase().contains(query)
        }
        updateUsersList(filteredUsers)
    }

    private fun updateUsersList(users: List<User>) {
        userAdapter.submitList(users)
        binding.usersRecyclerView.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
        binding.noUsersText.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupCreateButton() {
        binding.createButton.isEnabled = false
        binding.createButton.setOnClickListener {
            val name = binding.groupNameInput.text.toString().trim()
            val description = binding.descriptionInput.text.toString().trim()

            if (validateInputs(name, description)) {
                createGroup(name, description)
            }
        }
    }

    private fun clearErrors() {
        binding.groupNameLayout.error = null
        binding.descriptionLayout.error = null
    }

    private fun validateInputs(name: String, description: String): Boolean {
        var isValid = true
        clearErrors()

        when {
            name.isEmpty() -> {
                binding.groupNameLayout.error = "Group name is required"
                isValid = false
            }
            name.length < 3 -> {
                binding.groupNameLayout.error = "Group name must be at least 3 characters"
                isValid = false
            }
        }

        when {
            description.isEmpty() -> {
                binding.descriptionLayout.error = "Description is required"
                isValid = false
            }
            description.length < 10 -> {
                binding.descriptionLayout.error = "Description must be at least 10 characters"
                isValid = false
            }
        }

        if (userAdapter.getSelectedUsers().isEmpty()) {
            Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun createGroup(name: String, description: String) {
        val currentUserUid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.createButton.isEnabled = false

        val selectedUsers = userAdapter.getSelectedUsers().toSet()

        val group = Group(
            groupId = db.collection("groups").document().id,
            name = name,
            description = description,
            adminId = currentUserUid,
            members = mutableListOf(), // Empty member list initially
            createdAt = System.currentTimeMillis(),
            lastActivity = System.currentTimeMillis()
        )

        db.collection("groups")
            .document(group.groupId)
            .set(group)
            .addOnSuccessListener {
                createInvitations(group.groupId, selectedUsers)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create group: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.loadingIndicator.visibility = View.GONE
                binding.createButton.isEnabled = true
            }
    }

    private fun createInvitations(groupId: String, selectedUsers: Set<String>) {
        val currentUserUid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val batch = db.batch()

        selectedUsers.forEach { userId ->
            db.collection("groupInvitations")
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("invitedUser", userId)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result.isEmpty) {
                        val invitationRef = db.collection("groupInvitations").document()
                        val invitation = GroupInvitation(
                            invitationId = invitationRef.id,
                            groupId = groupId,
                            invitedBy = currentUserUid,
                            invitedUser = userId,
                            status = "pending",
                            timestamp = System.currentTimeMillis()
                        )
                        batch.set(invitationRef, invitation)
                    }
                }
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Invitations sent successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send some invitations: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                binding.loadingIndicator.visibility = View.GONE
                binding.createButton.isEnabled = true
                finish()
            }
    }
}