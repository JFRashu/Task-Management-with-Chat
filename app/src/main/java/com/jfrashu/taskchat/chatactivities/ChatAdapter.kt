package com.jfrashu.taskchat.chatactivities

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ItemChatBinding
import com.jfrashu.taskchat.dataclasses.Chat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: ((Chat) -> Unit)? = null
) : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding, onMessageLongClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = getItem(position)
        val showSenderName = position == 0 ||
                getItem(position - 1).senderId != chat.senderId
        holder.bind(chat, currentUserId, showSenderName)
    }

    class ChatViewHolder(
        private val binding: ItemChatBinding,
        private val onMessageLongClick: ((Chat) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(chat: Chat, currentUserId: String, showSenderName: Boolean) {
            val isSentByMe = chat.senderId == currentUserId

            // Configure message containers visibility
            binding.apply {
                leftMessageContainer.visibility = if (isSentByMe) View.GONE else View.VISIBLE
                rightMessageContainer.visibility = if (isSentByMe) View.VISIBLE else View.GONE

                // Show/hide sender name for grouped messages
                senderName.visibility = if (!isSentByMe && showSenderName) View.VISIBLE else View.GONE
            }

            // Set up long click listener only if message is not deleted
            if (!chat.isDeleted) {
                binding.root.setOnLongClickListener {
                    onMessageLongClick?.invoke(chat)
                    true
                }
            } else {
                binding.root.setOnLongClickListener(null)
            }

            if (isSentByMe) {
                setupReceiverMessage(chat)
            } else {
                setupSenderMessage(chat)
            }
        }

        private fun setupSenderMessage(chat: Chat) {
            binding.apply {
                // Apply deleted message style if needed
                if (chat.isDeleted) {
                    senderMessageText.text = "This message was deleted"
                    senderMessageText.setTextColor(Color.GRAY)
                    leftMessageContainer.alpha = 0.6f
                    leftMessageContainer.setBackgroundResource(R.drawable.sender_deleted_message_background)
                } else {
                    senderMessageText.setTextColor(ContextCompat.getColor(itemView.context,
                        R.color.md_theme_onBackground_mediumContrast))
                    leftMessageContainer.alpha = 1.0f
                    leftMessageContainer.setBackgroundResource(R.drawable.sender_message_background)

                    when (chat.type) {
                        "text" -> {
                            senderMessageText.text = chat.content
                            senderMessageText.visibility = View.VISIBLE
                        }
                        "image" -> {
                            senderMessageText.text = "[Image]"
                            // TODO: Implement image loading using Glide or Coil
                        }
                        "file" -> {
                            senderMessageText.text = "[File Attachment]"
                            // TODO: Implement file attachment handling
                        }
                    }
                }
                senderTimestamp.text = dateFormatter.format(Date(chat.timestamp))
            }
        }

        private fun setupReceiverMessage(chat: Chat) {
            binding.apply {
                // Apply deleted message style if needed
                if (chat.isDeleted) {
                    receiverMessageText.text = "This message was deleted"
                    receiverMessageText.setTextColor(Color.GRAY)
                    rightMessageContainer.alpha = 0.6f
                    rightMessageContainer.setBackgroundResource(R.drawable.receiver_deleted_message_background)
                } else {
                    receiverMessageText.setTextColor(ContextCompat.getColor(itemView.context,
                        R.color.md_theme_onBackground_mediumContrast))
                    rightMessageContainer.alpha = 1.0f
                    rightMessageContainer.setBackgroundResource(R.drawable.receiver_message_background)

                    when (chat.type) {
                        "text" -> {
                            receiverMessageText.text = chat.content
                            receiverMessageText.visibility = View.VISIBLE
                        }
                        "image" -> {
                            receiverMessageText.text = "[Image]"
                            // TODO: Implement image loading using Glide or Coil
                        }
                        "file" -> {
                            receiverMessageText.text = "[File Attachment]"
                            // TODO: Implement file attachment handling
                        }
                    }
                }
                receiverTimestamp.text = dateFormatter.format(Date(chat.timestamp))
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