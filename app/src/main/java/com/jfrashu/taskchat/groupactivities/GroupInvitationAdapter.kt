package com.jfrashu.taskchat.groupactivities

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jfrashu.taskchat.databinding.ItemGroupInvitationBinding
import com.jfrashu.taskchat.dataclasses.GroupInvitation
import com.jfrashu.taskchat.dataclasses.GroupInvitationWithDetails

class GroupInvitationAdapter(
    private val onAccept: (GroupInvitation) -> Unit,
    private val onReject: (GroupInvitation) -> Unit
) : ListAdapter<GroupInvitationWithDetails, GroupInvitationAdapter.ViewHolder>(InvitationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupInvitationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemGroupInvitationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GroupInvitationWithDetails) {
            binding.apply {
                groupName.text = item.groupName
                invitedBy.text = "Invited by: ${item.inviterName}"

                acceptButton.setOnClickListener {
                    onAccept(item.invitation)
                }
                rejectButton.setOnClickListener {
                    onReject(item.invitation)
                }
            }
        }
    }

    private class InvitationDiffCallback : DiffUtil.ItemCallback<GroupInvitationWithDetails>() {
        override fun areItemsTheSame(oldItem: GroupInvitationWithDetails, newItem: GroupInvitationWithDetails): Boolean {
            return oldItem.invitation.invitationId == newItem.invitation.invitationId
        }

        override fun areContentsTheSame(oldItem: GroupInvitationWithDetails, newItem: GroupInvitationWithDetails): Boolean {
            return oldItem == newItem
        }
    }
}
