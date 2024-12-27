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
    private lateinit var availableUsersAdapter: UserAdapter
    private lateinit var selectedUsersAdapter: UserAdapter
    private var allUsers = mutableListOf<User>()
    private var selectedUsers = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerViews()
        setupUserSearch()
        setupCreateButton()
        clearErrors()
    }

    private fun setupRecyclerViews() {
        // Setup Available Users RecyclerView
        availableUsersAdapter = UserAdapter { user ->
            // When an available user is selected
            selectedUsers.add(user.userId)
            updateUserLists()
            updateCreateButtonState()
        }

        // Setup Selected Users RecyclerView
        selectedUsersAdapter = UserAdapter { user ->
            // When a selected user is unselected
            selectedUsers.remove(user.userId)
            updateUserLists()
            updateCreateButtonState()
        }

        binding.availableUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CreateGroupActivity)
            adapter = availableUsersAdapter
        }

        binding.selectedUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CreateGroupActivity)
            adapter = selectedUsersAdapter
        }

        loadAllUsers()
    }

    private fun updateUserLists() {
        // Update Selected Users List
        val selectedUsersList = allUsers.filter { user ->
            selectedUsers.contains(user.userId)
        }
        selectedUsersAdapter.submitList(selectedUsersList)

        // Update UI visibility for selected users
        binding.selectedUsersRecyclerView.visibility =
            if (selectedUsersList.isEmpty()) View.GONE else View.VISIBLE
        binding.noSelectedUsersText.visibility =
            if (selectedUsersList.isEmpty()) View.VISIBLE else View.GONE

        // Update Available Users List (considering search query)
        val searchQuery = binding.searchUserInput.text.toString().trim().lowercase()
        filterUsers(searchQuery)
    }

    private fun filterUsers(query: String) {
        val searchQuery = query.trim().lowercase()

        val filteredUsers = if (searchQuery.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                // Check if all characters in the search query appear in sequence
                // in either the email or display name
                searchQuery.toCharArray().joinToString(".*", "(.*)", ".*").toRegex()
                    .let { regex ->
                        user.email.lowercase().matches(regex) ||
                                user.displayName.lowercase().matches(regex)
                    }
            }
        }.filter { user -> !selectedUsers.contains(user.userId) }

        // Update Available Users
        availableUsersAdapter.submitList(filteredUsers)

        // Update UI visibility
        binding.availableUsersRecyclerView.visibility =
            if (filteredUsers.isEmpty()) View.GONE else View.VISIBLE
        binding.noAvailableUsersText.visibility =
            if (filteredUsers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateCreateButtonState() {
        binding.createButton.isEnabled = selectedUsers.isNotEmpty()
        updateSelectedUsersCount()
    }



    private fun updateSelectedUsersCount() {
        val count = selectedUsers.size
        binding.selectedUsersCount.apply {
            text = "Selected users: $count"
            visibility = if (count > 0) View.VISIBLE else View.GONE
        }
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

                updateUserLists()
                binding.loadingIndicator.visibility = View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to load users: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
                binding.loadingIndicator.visibility = View.GONE
            }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selectedUsers", ArrayList(selectedUsers))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        selectedUsers = savedInstanceState.getStringArrayList("selectedUsers")
            ?.toMutableSet() ?: mutableSetOf()
        updateUserLists()
        updateCreateButtonState()
    }


    private fun setupUserSearch() {
        binding.searchUserInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchText = s?.toString() ?: ""
                filterUsers(searchText)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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

        // Changed from userAdapter.getSelectedUsers() to selectedUsers
        if (selectedUsers.isEmpty()) {
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

        // Changed from userAdapter.getSelectedUsers() to selectedUsers
        val selectedUsersList = selectedUsers.toSet()

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
                createInvitations(group.groupId, selectedUsersList)
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