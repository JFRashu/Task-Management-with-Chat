package com.jfrashu.taskchat.users

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jfrashu.taskchat.databinding.ItemUserBinding
import com.jfrashu.taskchat.dataclasses.User

class UserAdapter(
    private val onUserSelected: (User) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(UserDiffCallback()) {

    private val selectedUsers = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
    }

    inner class UserViewHolder(
        private val binding: ItemUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.apply {
                userName.text = user.displayName
                userEmail.text = user.email
                root.isSelected = selectedUsers.contains(user.userId)

                root.setOnClickListener {
                    if (selectedUsers.contains(user.userId)) {
                        selectedUsers.remove(user.userId)
                    } else {
                        selectedUsers.add(user.userId)
                    }
                    notifyItemChanged(adapterPosition)
                    onUserSelected(user)
                }
            }
        }
    }

    fun getSelectedUsers(): Set<String> = selectedUsers.toSet()

    private class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}