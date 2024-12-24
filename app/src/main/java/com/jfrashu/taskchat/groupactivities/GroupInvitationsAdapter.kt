package com.jfrashu.taskchat.groupactivities

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.GroupInvitation

class GroupInvitationsAdapter(
    private val invitations: List<GroupInvitation>,
    private val onAccept: (GroupInvitation) -> Unit,
    private val onReject: (GroupInvitation) -> Unit
) : RecyclerView.Adapter<GroupInvitationsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val invitedByText: TextView = view.findViewById(R.id.invitedByText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
        val acceptButton: MaterialButton = view.findViewById(R.id.acceptButton)
        val rejectButton: MaterialButton = view.findViewById(R.id.rejectButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_invitation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val invitation = invitations[position]

        holder.invitedByText.text = "Invited by ${invitation.invitedBy}"
        holder.timestampText.text = DateUtils.getRelativeTimeSpanString(invitation.timestamp)

        holder.acceptButton.setOnClickListener { onAccept(invitation) }
        holder.rejectButton.setOnClickListener { onReject(invitation) }

        if (invitation.status != "pending") {
            holder.acceptButton.isEnabled = false
            holder.rejectButton.isEnabled = false
        }
    }

    override fun getItemCount() = invitations.size
}