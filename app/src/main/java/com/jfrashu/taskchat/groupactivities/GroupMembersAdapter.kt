package com.jfrashu.taskchat.groupactivities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.User

class GroupMembersAdapter(
    private var members: List<User>,
    private val isAdmin: Boolean,
    private val onMemberClick: (String) -> Unit
) : RecyclerView.Adapter<GroupMembersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.memberNameText)
        val emailText: TextView = view.findViewById(R.id.memberEmailText)
        val removeButton: ImageButton = view.findViewById(R.id.removeMemberButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        holder.nameText.text = member.displayName
        holder.emailText.text = member.email

        // Only show remove button for admin
        holder.removeButton.visibility = if (isAdmin) View.VISIBLE else View.GONE

        holder.removeButton.setOnClickListener {
            onMemberClick(member.userId)
        }
    }

    override fun getItemCount() = members.size

    fun updateMembers(newMembers: List<User>) {
        members = newMembers
        notifyDataSetChanged()
    }
}