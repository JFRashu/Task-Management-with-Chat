package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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

private const val TAG = "CreateGroupActivity"

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
        availableUsersAdapter = UserAdapter { user ->
            Log.d(TAG, "User selected: ${user.displayName}")
            selectedUsers.add(user.userId)
            updateUserLists()
            updateCreateButtonState()
        }

        selectedUsersAdapter = UserAdapter { user ->
            Log.d(TAG, "User unselected: ${user.displayName}")
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
        val selectedUsersList = allUsers.filter { user ->
            selectedUsers.contains(user.userId)
        }
        selectedUsersAdapter.submitList(selectedUsersList)

        binding.selectedUsersRecyclerView.visibility =
            if (selectedUsersList.isEmpty()) View.GONE else View.VISIBLE
        binding.noSelectedUsersText.visibility =
            if (selectedUsersList.isEmpty()) View.VISIBLE else View.GONE

        val searchQuery = binding.searchUserInput.text.toString().trim().lowercase()
        filterUsers(searchQuery)
    }

    private fun filterUsers(query: String) {
        val searchQuery = query.trim().lowercase()
        Log.d(TAG, "Filtering users with query: $searchQuery")

        val filteredUsers = if (searchQuery.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                searchQuery.toCharArray().joinToString(".*", "(.*)", ".*").toRegex()
                    .let { regex ->
                        user.email.lowercase().matches(regex) ||
                                user.displayName.lowercase().matches(regex)
                    }
            }
        }.filter { user -> !selectedUsers.contains(user.userId) }

        availableUsersAdapter.submitList(filteredUsers)

        binding.availableUsersRecyclerView.visibility =
            if (filteredUsers.isEmpty()) View.GONE else View.VISIBLE
        binding.noAvailableUsersText.visibility =
            if (filteredUsers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadAllUsers() {
        val currentUserId = auth.currentUser?.uid ?: run {
            Log.e(TAG, "No current user found")
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.loadingIndicator.visibility = View.VISIBLE
        Log.d(TAG, "Loading users from Firestore")

        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Successfully loaded ${documents.size()} users")
                allUsers = documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)
                }.filter { it.userId != currentUserId }.toMutableList()

                updateUserLists()
                binding.loadingIndicator.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading users", exception)
                Toast.makeText(
                    this,
                    "Failed to load users: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
                binding.loadingIndicator.visibility = View.GONE
            }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
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

        if (selectedUsers.isEmpty()) {
            Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun createGroup(name: String, description: String) {
        val currentUserUid = auth.currentUser?.uid ?: run {
            Log.e(TAG, "No current user found when creating group")
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Starting group creation process")
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.createButton.isEnabled = false

        val groupId = db.collection("groups").document().id
        val currentTime = System.currentTimeMillis()

        val groupData = mapOf(
            "groupId" to groupId,
            "name" to name,
            "description" to description,
            "adminId" to currentUserUid,
            "members" to listOf(currentUserUid),
            "tasks" to listOf<String>(),
            "createdAt" to currentTime,
            "lastActivity" to currentTime,
            "isDeleted" to false
        )

        Log.d(TAG, "Creating group with ID: $groupId")
        db.collection("groups").document(groupId)
            .set(groupData)
            .addOnSuccessListener {
                Log.d(TAG, "Group created successfully")
                createInvitationsSequentially(groupId, selectedUsers.toSet())
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error creating group", exception)
                Toast.makeText(
                    this,
                    "Failed to create group: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
                binding.loadingIndicator.visibility = View.GONE
                binding.createButton.isEnabled = true
            }
    }

    private fun createInvitationsSequentially(groupId: String, selectedUsers: Set<String>) {
        Log.d(TAG, "Starting sequential invitation creation for ${selectedUsers.size} users")
        val currentUserUid = auth.currentUser?.uid ?: run {
            Log.e(TAG, "No current user found when creating invitations")
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            return
        }

        var successCount = 0
        var failureCount = 0

        fun createNextInvitation(users: Iterator<String>) {
            if (!users.hasNext()) {
                // All invitations processed
                Log.d(TAG, "Finished creating invitations. Success: $successCount, Failed: $failureCount")
                val message = when {
                    failureCount == 0 -> "Group created and all invitations sent"
                    successCount == 0 -> "Group created but failed to send invitations"
                    else -> "Group created. Sent $successCount invitations, $failureCount failed"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                binding.loadingIndicator.visibility = View.GONE
                finish()
                return
            }

            val userId = users.next()
            val invitationRef = db.collection("groupInvitations").document()
            val invitation = GroupInvitation(
                invitationId = invitationRef.id,
                groupId = groupId,
                invitedBy = currentUserUid,
                invitedUser = userId,
                status = "pending",
                timestamp = System.currentTimeMillis()
            )

            invitationRef.set(invitation)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully created invitation for user: $userId")
                    successCount++
                    createNextInvitation(users)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to create invitation for user: $userId", exception)
                    failureCount++
                    createNextInvitation(users)
                }
        }

        // Start the sequential creation process
        createNextInvitation(selectedUsers.iterator())
    }

    private fun clearErrors() {
        binding.groupNameLayout.error = null
        binding.descriptionLayout.error = null
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
}