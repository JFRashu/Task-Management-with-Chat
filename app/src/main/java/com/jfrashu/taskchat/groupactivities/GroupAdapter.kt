package com.jfrashu.taskchat.groupactivities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.dataclasses.Group

class GroupAdapter(
    private var groups: List<Group>,
    private val onGroupClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val groupIcon: ImageView = itemView.findViewById(R.id.groupIcon)
        val groupName: TextView = itemView.findViewById(R.id.groupName)
        val lastActivity: TextView = itemView.findViewById(R.id.lastActivity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.groupName.text = group.name
        holder.lastActivity.text = getTimeAgo(group.lastActivity)
        holder.itemView.setOnClickListener { onGroupClick(group) }
    }

    override fun getItemCount() = groups.size

    fun updateGroups(newGroups: List<Group>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    private fun getTimeAgo(timestamp: Long): String {
        val difference = System.currentTimeMillis() - timestamp
        return when {
            difference < 1000 * 60 -> "Just now"
            difference < 1000 * 60 * 60 -> "${difference / (1000 * 60)} minutes ago"
            difference < 1000 * 60 * 60 * 24 -> "${difference / (1000 * 60 * 60)} hours ago"
            else -> "${difference / (1000 * 60 * 60 * 24)} days ago"
        }
    }
}