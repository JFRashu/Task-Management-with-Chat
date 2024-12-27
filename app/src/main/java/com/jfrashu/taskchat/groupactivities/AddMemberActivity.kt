package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
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
    private var selectedUsers = mutableSetOf<String>()
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
        fetchCurrentMembers()
    }

    private fun setupViews() {
        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup RecyclerView for available users
        availableUsersAdapter = UserAdapter { user ->
            if (selectedUsers.contains(user.userId)) {
                selectedUsers.remove(user.userId)
            } else {
                selectedUsers.add(user.userId)
            }
            updateAddButtonState()
        }

        binding.apply {
            availableUsersRecyclerView.layoutManager = LinearLayoutManager(this@AddMemberActivity)
            availableUsersRecyclerView.adapter = availableUsersAdapter

            // Setup search functionality
            searchUserInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterUsers(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // Setup add button
            addMembersButton.setOnClickListener {
                sendInvitations()
            }
        }
    }

    private fun filterUsers(query: String) {
        val searchQuery = query.trim().lowercase()

        val filteredUsers = if (searchQuery.isEmpty()) {
            availableUsersAdapter.currentList
        } else {
            availableUsersAdapter.currentList.filter { user ->
                searchQuery.toCharArray().joinToString(".*", "(.*)", ".*").toRegex()
                    .let { regex ->
                        user.email.lowercase().matches(regex) ||
                                user.displayName.lowercase().matches(regex)
                    }
            }
        }

        availableUsersAdapter.submitList(filteredUsers)
        updateUIVisibility(filteredUsers.isEmpty())
    }

    private fun updateUIVisibility(isEmpty: Boolean) {
        binding.apply {
            availableUsersRecyclerView.isVisible = !isEmpty
            noUsersFoundText.isVisible = isEmpty
        }
    }

    private fun fetchCurrentMembers() {
        binding.loadingIndicator.isVisible = true

        groupId?.let { gId ->
            db.collection("groups").document(gId)
                .get()
                .addOnSuccessListener { document ->
                    document.toObject(Group::class.java)?.let { group ->
                        loadAvailableUsers()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to fetch group members", Toast.LENGTH_SHORT).show()
                    binding.loadingIndicator.isVisible = false
                }
        }
    }

    private fun loadAvailableUsers() {
        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                val availableUsers = documents
                    .mapNotNull { it.toObject(User::class.java) }
                availableUsersAdapter.submitList(availableUsers)
                binding.loadingIndicator.isVisible = false
                updateUIVisibility(availableUsers.isEmpty())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load available users", Toast.LENGTH_SHORT).show()
                binding.loadingIndicator.isVisible = false
            }
    }

    private fun sendInvitations() {
        if (selectedUsers.isEmpty()) {
            Toast.makeText(this, "Please select users to invite", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loadingIndicator.isVisible = true
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
                    binding.loadingIndicator.isVisible = false
                    binding.addMembersButton.isEnabled = true
                }
        }
    }

    private fun updateAddButtonState() {
        binding.addMembersButton.isEnabled = selectedUsers.isNotEmpty()
    }
}
