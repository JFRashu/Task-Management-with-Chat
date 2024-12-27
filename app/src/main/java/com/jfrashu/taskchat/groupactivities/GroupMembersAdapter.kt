package com.jfrashu.taskchat.groupactivities

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ItemGroupMemberBinding
import com.jfrashu.taskchat.dataclasses.User

class GroupMembersAdapter(
    private var members: List<User>,
    private val currentUserIsAdmin: Boolean,
    private val groupAdminId: String,
    private val onMemberRemove: (String) -> Unit
) : RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder>() {

    inner class MemberViewHolder(private val binding: ItemGroupMemberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.apply {
                memberNameText.text = user.displayName
                memberEmailText.text = user.email

                // Check if user is group admin using groupAdminId
                val isGroupAdmin = user.userId == groupAdminId

                // Configure status chip
                statusText.apply {
                    if (isGroupAdmin) {
                        text = "Group Admin"
                        setChipBackgroundColorResource(R.color.md_theme_primaryContainer)
                        setTextColor(ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer))
                        chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer))
                        setChipIconResource(R.drawable.baseline_admin_panel_settings_24)
                    } else {
                        text = "Member"
                        setChipBackgroundColorResource(android.R.color.transparent)
                        setTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant))
                        chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant))
                        setChipIconResource(R.drawable.baseline_person_24)
                    }
                    isVisible = true
                }

                // Show remove button only if current user is admin and target user is not group admin
                removeMemberButton.apply {
                    isVisible = currentUserIsAdmin && !isGroupAdmin
                    setOnClickListener {
                        if (currentUserIsAdmin && !isGroupAdmin) {
                            onMemberRemove(user.userId)
                        }
                    }
                }

                // Configure avatar based on admin status
                memberAvatar.apply {
                    if (isGroupAdmin) {
                        setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_primary))
                        setColorFilter(ContextCompat.getColor(context, R.color.md_theme_onPrimary))
                    } else {
                        setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_secondaryContainer))
                        setColorFilter(ContextCompat.getColor(context, R.color.md_theme_onSecondaryContainer))
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemGroupMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        // Sort members so admin appears first
        val sortedMembers = members.sortedBy { it.userId != groupAdminId }
        holder.bind(sortedMembers[position])
    }

    override fun getItemCount() = members.size

    fun updateMembers(newMembers: List<User>) {
        members = newMembers
        notifyDataSetChanged()
    }
}