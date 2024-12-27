package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.util.Log
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

private const val TAG = "GroupInvitationActivity"

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
            visibility = View.VISIBLE // Ensure RecyclerView is visible initially
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadInvitations()
        }
    }

    private fun loadInvitations() {
        val currentUser = auth.currentUser ?: run {
            Log.e(TAG, "No authenticated user found")
            showError("Please sign in to view invitations")
            return
        }

        showLoading(true)
        Log.d(TAG, "Loading invitations for user: ${currentUser.uid}")

        db.collection("groupInvitations")
            .whereEqualTo("invitedUser", currentUser.uid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Found ${documents.size()} invitation documents")
                val invitations = documents.mapNotNull { doc ->
                    try {
                        doc.toObject(GroupInvitation::class.java).also { invitation ->
                            Log.d(TAG, "Mapped invitation: ${invitation.invitationId}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapping invitation document: ${doc.id}", e)
                        null
                    }
                }

                if (invitations.isEmpty()) {
                    Log.d(TAG, "No pending invitations found")
                    showEmptyView(true)
                    showLoading(false)
                } else {
                    Log.d(TAG, "Found ${invitations.size} invitations, fetching details")
                    fetchInvitationDetails(invitations)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading invitations", e)
                showError("Failed to load invitations: ${e.localizedMessage}")
                showLoading(false)
            }
    }

    private fun fetchInvitationDetails(invitations: List<GroupInvitation>) {
        val invitationDetails = mutableListOf<GroupInvitationWithDetails>()
        var completedRequests = 0
        var hasError = false

        invitations.forEach { invitation ->
            Log.d(TAG, "Fetching details for invitation: ${invitation.invitationId}")

            // Fetch group details
            db.collection("groups")
                .document(invitation.groupId)
                .get()
                .addOnSuccessListener { groupDoc ->
                    val group = groupDoc.toObject(Group::class.java)
                    if (group == null) {
                        Log.e(TAG, "Group not found for invitation: ${invitation.invitationId}")
                        hasError = true
                        handleCompletedRequest(invitations.size, ++completedRequests, hasError, invitationDetails)
                        return@addOnSuccessListener
                    }

                    // Fetch inviter details
                    db.collection("users")
                        .document(invitation.invitedBy)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val inviter = userDoc.toObject(User::class.java)
                            if (inviter == null) {
                                Log.e(TAG, "Inviter not found for invitation: ${invitation.invitationId}")
                                hasError = true
                                handleCompletedRequest(invitations.size, ++completedRequests, hasError, invitationDetails)
                                return@addOnSuccessListener
                            }

                            val invitationWithDetails = GroupInvitationWithDetails(
                                invitation = invitation,
                                groupName = group.name,
                                inviterName = inviter.displayName
                            )
                            Log.d(TAG, "Created invitation details: ${invitationWithDetails.groupName}")
                            invitationDetails.add(invitationWithDetails)
                            handleCompletedRequest(invitations.size, ++completedRequests, hasError, invitationDetails)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error fetching inviter details", e)
                            hasError = true
                            handleCompletedRequest(invitations.size, ++completedRequests, hasError, invitationDetails)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching group details", e)
                    hasError = true
                    handleCompletedRequest(invitations.size, ++completedRequests, hasError, invitationDetails)
                }
        }
    }

    private fun handleCompletedRequest(
        totalRequests: Int,
        completedRequests: Int,
        hasError: Boolean,
        invitationDetails: List<GroupInvitationWithDetails>
    ) {
        if (completedRequests == totalRequests) {
            Log.d(TAG, "All requests completed. HasError: $hasError, Details size: ${invitationDetails.size}")
            runOnUiThread {
                if (!hasError) {
                    adapter.submitList(invitationDetails)
                    showEmptyView(invitationDetails.isEmpty())
                } else {
                    showError("Some invitation details could not be loaded")
                    showEmptyView(invitationDetails.isEmpty())
                }
                showLoading(false)
            }
        }
    }

    private fun handleInvitationResponse(invitation: GroupInvitation, status: String) {
        val currentUser = auth.currentUser ?: run {
            Log.e(TAG, "No authenticated user found while handling invitation response")
            showError("Please sign in to respond to invitations")
            return
        }

        showLoading(true)
        Log.d(TAG, "Handling invitation response: $status for ${invitation.invitationId}")

        db.collection("groupInvitations")
            .document(invitation.invitationId)
            .update("status", status)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully updated invitation status to: $status")
                if (status == "accepted") {
                    addUserToGroup(invitation.groupId, currentUser.uid)
                } else {
                    loadInvitations()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating invitation status", e)
                showError("Failed to $status invitation: ${e.localizedMessage}")
                showLoading(false)
            }
    }

    private fun addUserToGroup(groupId: String, userId: String) {
        Log.d(TAG, "Adding user $userId to group $groupId")

        val groupRef = db.collection("groups").document(groupId)

        // Use a transaction to safely update the members array
        db.runTransaction { transaction ->
            val snapshot = transaction.get(groupRef)
            val group = snapshot.toObject(Group::class.java)

            if (group != null) {
                // Create a new members list with the user added
                val updatedMembers = if (group.members.contains(userId)) {
                    group.members // User already in group
                } else {
                    group.members + userId // Add user to group
                }

                // Update the document
                transaction.update(groupRef, "members", updatedMembers)
            } else {
                throw Exception("Group not found")
            }
        }.addOnSuccessListener {
            Log.d(TAG, "Successfully added user to group")
            showSuccess("Successfully joined the group")
            loadInvitations()
            showLoading(false)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error updating group members", e)
            showError("Failed to join group: ${e.localizedMessage}")
            showLoading(false)
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