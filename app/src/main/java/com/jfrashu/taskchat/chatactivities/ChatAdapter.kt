package com.jfrashu.taskchat.chatactivities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jfrashu.taskchat.databinding.ItemChatMessageBinding
import com.jfrashu.taskchat.dataclasses.Chat
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val currentUserId: String) :
    ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position), currentUserId)
    }

    class ChatViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(chat: Chat, currentUserId: String) {
            val isSentByMe = chat.senderId == currentUserId

            // Show/hide appropriate containers
            binding.leftMessageContainer.visibility = if (isSentByMe) View.GONE else View.VISIBLE
            binding.rightMessageContainer.visibility = if (isSentByMe) View.VISIBLE else View.GONE

            if (isSentByMe) {
                when (chat.type) {
                    "text" -> {
                        binding.receiverMessageText.text = chat.content
                    }
                    "image" -> {
                        // TODO: Implement image loading
                        binding.receiverMessageText.text = "[Image]"
                    }
                    "file" -> {
                        // TODO: Implement file attachment display
                        binding.receiverMessageText.text = "[File Attachment]"
                    }
                }
                binding.receiverTimestamp.text = dateFormatter.format(Date(chat.timestamp))
            } else {
                when (chat.type) {
                    "text" -> {
                        binding.senderMessageText.text = chat.content
                    }
                    "image" -> {
                        // TODO: Implement image loading
                        binding.senderMessageText.text = "[Image]"
                    }
                    "file" -> {
                        // TODO: Implement file attachment display
                        binding.senderMessageText.text = "[File Attachment]"
                    }
                }
                binding.senderTimestamp.text = dateFormatter.format(Date(chat.timestamp))
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem == newItem
        }
    }
}