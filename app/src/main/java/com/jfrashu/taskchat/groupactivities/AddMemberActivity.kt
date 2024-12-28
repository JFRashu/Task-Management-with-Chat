package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.databinding.ActivityAddMemberBinding
import com.jfrashu.taskchat.dataclasses.Group
import com.jfrashu.taskchat.dataclasses.GroupInvitation
import com.jfrashu.taskchat.dataclasses.User
import com.jfrashu.taskchat.users.UserAdapter

class AddMemberActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddMemberBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var availableUsersAdapter: UserAdapter
    private lateinit var selectedUsersAdapter: UserAdapter
    private var allUsers = mutableListOf<User>()
    private var selectedUsers = mutableSetOf<String>()
    private var currentGroupMembers = mutableSetOf<String>()
    private var groupId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMemberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        groupId = intent.getStringExtra("groupId")

        if (groupId == null) {
            Toast.makeText(this, "Invalid group ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        setupRecyclerViews()
        fetchCurrentMembers()
    }

    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup search functionality
        binding.searchUserInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup add button
        binding.addMembersButton.apply {
            isEnabled = false
            setOnClickListener { sendInvitations() }
        }
    }

    private fun setupRecyclerViews() {
        // Setup adapter for available users
        availableUsersAdapter = UserAdapter { user ->
            selectedUsers.add(user.userId)
            updateUserLists()
            updateAddButtonState()
        }

        // Setup adapter for selected users
        selectedUsersAdapter = UserAdapter { user ->
            selectedUsers.remove(user.userId)
            updateUserLists()
            updateAddButtonState()
        }

        // Setup RecyclerViews
        binding.availableUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AddMemberActivity)
            adapter = availableUsersAdapter
        }

        binding.selectedUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AddMemberActivity)
            adapter = selectedUsersAdapter
        }
    }

    private fun fetchCurrentMembers() {
        binding.loadingIndicator.visibility = View.VISIBLE

        groupId?.let { gId ->
            db.collection("groups").document(gId)
                .get()
                .addOnSuccessListener { document ->
                    document.toObject(Group::class.java)?.let { group ->
                        currentGroupMembers = group.members.toMutableSet()
                        loadAvailableUsers()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to fetch group members", Toast.LENGTH_SHORT).show()
                    binding.loadingIndicator.visibility = View.GONE
                }
        }
    }

    private fun loadAvailableUsers() {
        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                allUsers = documents
                    .mapNotNull { it.toObject(User::class.java) }
                    .filter { !currentGroupMembers.contains(it.userId) }
                    .toMutableList()

                updateUserLists()
                binding.loadingIndicator.visibility = View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load available users", Toast.LENGTH_SHORT).show()
                binding.loadingIndicator.visibility = View.GONE
            }
    }

    private fun updateUserLists() {
        // Update selected users list
        val selectedUsersList = allUsers.filter { user ->
            selectedUsers.contains(user.userId)
        }
        selectedUsersAdapter.submitList(selectedUsersList)

        // Update visibility of selected users section
        binding.apply {
            selectedUsersRecyclerView.visibility =
                if (selectedUsersList.isEmpty()) View.GONE else View.VISIBLE
            noSelectedUsersText.visibility =
                if (selectedUsersList.isEmpty()) View.VISIBLE else View.GONE
        }

        // Filter available users based on current search query
        filterUsers(binding.searchUserInput.text.toString())
    }

    private fun filterUsers(query: String) {
        val searchQuery = query.trim().lowercase()

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

        // Update visibility of available users section
        binding.apply {
            availableUsersRecyclerView.visibility =
                if (filteredUsers.isEmpty()) View.GONE else View.VISIBLE
            noAvailableUsersText.visibility =
                if (filteredUsers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun sendInvitations() {
        if (selectedUsers.isEmpty()) {
            Toast.makeText(this, "Please select users to invite", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.addMembersButton.isEnabled = false

        groupId?.let { gId ->
            val currentUserUid = auth.currentUser?.uid ?: return

            // Create and send invitations
            val batch = db.batch()
            selectedUsers.forEach { userId ->
                val invitationRef = db.collection("groupInvitations").document()
                val invitation = GroupInvitation(
                    invitationId = invitationRef.id,
                    groupId = gId,
                    invitedBy = currentUserUid,
                    invitedUser = userId,
                    status = "pending",
                    timestamp = System.currentTimeMillis()
                )
                batch.set(invitationRef, invitation)
            }

            batch.commit()
                .addOnSuccessListener {
                    Toast.makeText(this, "Invitations sent successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to send invitations: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.loadingIndicator.visibility = View.GONE
                    binding.addMembersButton.isEnabled = true
                }
        }
    }

    private fun updateAddButtonState() {
        binding.addMembersButton.isEnabled = selectedUsers.isNotEmpty()
        updateSelectedUsersCount()
    }

    private fun updateSelectedUsersCount() {
        val count = selectedUsers.size
        binding.selectedUsersCount.apply {
            text = "Selected users: $count"
            visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }
}