package com.jfrashu.taskchat.groupactivities

import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.dataclasses.Group
import com.jfrashu.taskchat.dataclasses.GroupInvitation
import com.jfrashu.taskchat.dataclasses.User

class GroupMemberManager(private val firestore: FirebaseFirestore) {

    companion object {
        private const val GROUPS_COLLECTION = "groups"
        private const val INVITATIONS_COLLECTION = "group_invitations"
        private const val USERS_COLLECTION = "users"
    }

    // Send invitation to a user
    fun inviteUserToGroup(
        groupId: String,
        adminId: String,
        userToInvite: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Create invitation object
        val invitation = GroupInvitation(
            invitationId = firestore.collection(INVITATIONS_COLLECTION).document().id,
            groupId = groupId,
            invitedBy = adminId,
            invitedUser = userToInvite,
            status = "pending",
            timestamp = System.currentTimeMillis()
        )

        // Save invitation to Firestore
        firestore.collection(INVITATIONS_COLLECTION)
            .document(invitation.invitationId)
            .set(invitation)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    // Handle invitation response (accept/reject)
    fun handleInvitationResponse(
        invitationId: String,
        accept: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.runTransaction { transaction ->
            val invitationRef = firestore.collection(INVITATIONS_COLLECTION).document(invitationId)
            val invitation = transaction.get(invitationRef).toObject(GroupInvitation::class.java)
                ?: throw Exception("Invitation not found")

            if (accept) {
                // Update group members
                val groupRef = firestore.collection(GROUPS_COLLECTION).document(invitation.groupId)
                val group = transaction.get(groupRef).toObject(Group::class.java)
                    ?: throw Exception("Group not found")

                val updatedMembers = group.members + invitation.invitedUser
                transaction.update(groupRef, "members", updatedMembers)
            }

            // Update invitation status
            transaction.update(invitationRef, "status", if (accept) "accepted" else "rejected")
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    // Get pending invitations for a user
    fun getPendingInvitations(
        userId: String,
        onSuccess: (List<GroupInvitation>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(INVITATIONS_COLLECTION)
            .whereEqualTo("invitedUser", userId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snapshot ->
                val invitations = snapshot.documents.mapNotNull {
                    it.toObject(GroupInvitation::class.java)
                }
                onSuccess(invitations)
            }
            .addOnFailureListener { onError(it) }
    }

    // Remove member from group
    fun removeMember(
        groupId: String,
        memberId: String,
        adminId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.runTransaction { transaction ->
            val groupRef = firestore.collection(GROUPS_COLLECTION).document(groupId)
            val group = transaction.get(groupRef).toObject(Group::class.java)
                ?: throw Exception("Group not found")

            // Verify admin permission
            if (group.adminId != adminId) {
                throw Exception("Only admin can remove members")
            }

            val updatedMembers = group.members - memberId
            transaction.update(groupRef, "members", updatedMembers)
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
}