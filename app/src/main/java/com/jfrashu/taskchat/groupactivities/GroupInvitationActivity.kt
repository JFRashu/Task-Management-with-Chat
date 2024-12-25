package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.databinding.ActivityGroupInvitationBinding
import com.jfrashu.taskchat.dataclasses.Group
import com.jfrashu.taskchat.dataclasses.GroupInvitation
import com.jfrashu.taskchat.dataclasses.GroupInvitationWithDetails
import com.jfrashu.taskchat.dataclasses.User

class GroupInvitationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupInvitationBinding
    private lateinit var adapter: GroupInvitationAdapter
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInvitationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        loadInvitations()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = GroupInvitationAdapter(
            onAccept = { invitation -> handleInvitationResponse(invitation, "accepted") },
            onReject = { invitation -> handleInvitationResponse(invitation, "rejected") }
        )

        binding.invitationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupInvitationActivity)
            adapter = this@GroupInvitationActivity.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadInvitations()
        }
    }

    private fun loadInvitations() {
        val currentUser = auth.currentUser ?: return
        showLoading(true)

        db.collection("groupInvitations")
            .whereEqualTo("invitedUser", currentUser.uid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                val invitations = documents.mapNotNull { it.toObject(GroupInvitation::class.java) }
                if (invitations.isEmpty()) {
                    showEmptyView(true)
                } else {
                    fetchInvitationDetails(invitations)
                }
            }
            .addOnFailureListener {
                showError("Failed to load invitations")
                showLoading(false)
            }
    }

    private fun fetchInvitationDetails(invitations: List<GroupInvitation>) {
        val invitationDetails = mutableListOf<GroupInvitationWithDetails>()
        var completedRequests = 0

        invitations.forEach { invitation ->
            // Fetch group details
            db.collection("groups").document(invitation.groupId).get()
                .addOnSuccessListener { groupDoc ->
                    val group = groupDoc.toObject(Group::class.java)

                    // Fetch inviter details
                    db.collection("users").document(invitation.invitedBy).get()
                        .addOnSuccessListener { userDoc ->
                            val inviter = userDoc.toObject(User::class.java)

                            if (group != null && inviter != null) {
                                invitationDetails.add(
                                    GroupInvitationWithDetails(
                                        invitation = invitation,
                                        groupName = group.name,
                                        inviterName = inviter.displayName
                                    )
                                )
                            }

                            completedRequests++
                            if (completedRequests == invitations.size) {
                                adapter.submitList(invitationDetails)
                                showLoading(false)
                                showEmptyView(invitationDetails.isEmpty())
                            }
                        }
                }
        }
    }

    private fun handleInvitationResponse(invitation: GroupInvitation, status: String) {
        val currentUser = auth.currentUser ?: return
        showLoading(true)

        // Update invitation status
        db.collection("groupInvitations")
            .document(invitation.invitationId)
            .update("status", status)
            .addOnSuccessListener {
                if (status == "accepted") {
                    // Add user to group members
                    addUserToGroup(invitation.groupId, currentUser.uid)
                } else {
                    // Reload invitations if rejected
                    loadInvitations()
                }
            }
            .addOnFailureListener {
                showError("Failed to ${status} invitation")
                showLoading(false)
            }
    }

    private fun addUserToGroup(groupId: String, userId: String) {
        db.collection("groups").document(groupId)
            .get()
            .addOnSuccessListener { document ->
                val group = document.toObject(Group::class.java)
                if (group != null) {
                    val updatedMembers = group.members + userId
                    db.collection("groups").document(groupId)
                        .update("members", updatedMembers)
                        .addOnSuccessListener {
                            showSuccess("Successfully joined the group")
                            loadInvitations()
                        }
                        .addOnFailureListener {
                            showError("Failed to join group")
                            showLoading(false)
                        }
                }
            }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun showEmptyView(show: Boolean) {
        binding.emptyView.visibility = if (show) View.VISIBLE else View.GONE
        binding.invitationsRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}