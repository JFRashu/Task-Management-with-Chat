package com.jfrashu.taskchat.chatactivities

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.res.ColorStateList
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ItemChatBinding
import com.jfrashu.taskchat.dataclasses.Chat
import com.jfrashu.taskchat.dataclasses.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: ((Chat) -> Unit)? = null
) : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    private val userNameCache = mutableMapOf<String, String>()
    private val db = FirebaseFirestore.getInstance()

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

        if (!isSentByMe(chat.senderId) && showSenderName) {
            fetchUserName(chat.senderId) { userName ->
                holder.bind(chat, currentUserId, showSenderName, userName)
            }
        } else {
            holder.bind(chat, currentUserId, showSenderName, userNameCache[chat.senderId])
        }
    }

    private fun fetchUserName(userId: String, callback: (String?) -> Unit) {
        if (userNameCache.containsKey(userId)) {
            callback(userNameCache[userId])
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                val userName = user?.displayName ?: "Unknown User"
                userNameCache[userId] = userName
                callback(userName)
            }
            .addOnFailureListener {
                callback("Unknown User")
            }
    }

    private fun isSentByMe(senderId: String): Boolean = senderId == currentUserId

    class ChatViewHolder(
        private val binding: ItemChatBinding,
        private val onMessageLongClick: ((Chat) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(chat: Chat, currentUserId: String, showSenderName: Boolean, userName: String?) {
            val isSentByMe = chat.senderId == currentUserId

            binding.apply {
                leftMessageContainer.visibility = if (isSentByMe) View.GONE else View.VISIBLE
                rightMessageContainer.visibility = if (isSentByMe) View.VISIBLE else View.GONE

                // Show/hide sender name for grouped messages
                if (!isSentByMe && showSenderName) {
                    senderName.visibility = View.VISIBLE
                    senderName.text = userName
                } else {
                    senderName.visibility = View.GONE
                }
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
                    senderMessageText.apply {
                        text = "❌ Message Deleted" // Clear text with emoji
                        setTextColor(Color.parseColor("#6C6C6C")) // Direct hex color for gray
                        setTypeface(null, Typeface.ITALIC)
                        visibility = View.VISIBLE
                    }
                    leftMessageContainer.apply {
                        setBackgroundColor(Color.parseColor("#E6E6E6")) // Light gray background
                        alpha = 1.0f // Full visibility
                    }
                } else {
                    // Normal message styling
                    senderMessageText.apply {
                        text = chat.content
//                        setTextColor(Color.parseColor("#000000")) // Black color for normal messages
//                        setTypeface(null, Typeface.NORMAL)
                        visibility = View.VISIBLE
                    }
                    leftMessageContainer.apply {
                        setBackgroundResource(R.drawable.sender_message_background)
                        alpha = 1.0f
                    }
                }
                senderTimestamp.text = dateFormatter.format(Date(chat.timestamp))
            }
        }

        private fun setupReceiverMessage(chat: Chat) {
            binding.apply {
                // Apply deleted message style if needed
                if (chat.isDeleted) {
                    receiverMessageText.apply {
                        text = "❌ Message Deleted" // Clear text with emoji
                        setTextColor(Color.parseColor("#6C6C6C")) // Direct hex color for gray
                        setTypeface(null, Typeface.ITALIC)
                        visibility = View.VISIBLE
                    }
                    rightMessageContainer.apply {
                        setBackgroundColor(Color.parseColor("#E6E6E6")) // Light gray background
                        alpha = 1.0f // Full visibility
                    }
                } else {
                    // Normal message styling
                    receiverMessageText.apply {
                        text = chat.content
//                        setTextColor(Color.parseColor("#000000")) // Black color for normal messages
//                        setTypeface(null, Typeface.NORMAL)
                        visibility = View.VISIBLE
                    }
                    rightMessageContainer.apply {
                        setBackgroundResource(R.drawable.receiver_message_background)
                        alpha = 1.0f
                    }
                }
                receiverTimestamp.text = dateFormatter.format(Date(chat.timestamp))
            }
        }

        private fun ItemChatBinding.applyReceiverDeletedMessageStyle() {
            receiverMessageText.apply {
                text = " Message was deleted"
                setTextColor(Color.parseColor("#808080")) // Gray color
                setTypeface(null, Typeface.ITALIC)
                textSize = 14f // Optional: set smaller text size for deleted messages
            }
            rightMessageContainer.apply {
                alpha = 0.8f
                setBackgroundResource(R.drawable.receiver_deleted_message_background) // Use same background
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0")) // Light gray tint
            }
        }

        private fun ItemChatBinding.applySenderDeletedMessageStyle() {
            senderMessageText.apply {
                text = " Message was deleted"
                setTextColor(Color.parseColor("#808080")) // Gray color
                setTypeface(null, Typeface.ITALIC)
                textSize = 14f // Optional: set smaller text size for deleted messages
            }
            leftMessageContainer.apply {
                alpha = 0.8f
                setBackgroundResource(R.drawable.receiver_deleted_message_background) // Use same background
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0")) // Light gray tint
            }
        }

        private fun ItemChatBinding.applySenderNormalMessageStyle(chat: Chat) {
            senderMessageText.apply {
                setTextColor(ContextCompat.getColor(itemView.context,
                    R.color.md_theme_onBackground_mediumContrast))
                setTypeface(null, Typeface.NORMAL)
            }
            leftMessageContainer.apply {
                alpha = 1.0f
                setBackgroundResource(R.drawable.sender_message_background)
            }

            when (chat.type) {
                "text" -> {
                    senderMessageText.text = chat.content
                    senderMessageText.visibility = View.VISIBLE
                }
                "image" -> {
                    senderMessageText.text = "📷 Image"
                }
                "file" -> {
                    senderMessageText.text = "📎 File Attachment"
                }
            }
        }



        private fun ItemChatBinding.applyReceiverNormalMessageStyle(chat: Chat) {
            receiverMessageText.apply {
                setTextColor(ContextCompat.getColor(itemView.context,
                    R.color.md_theme_onBackground_mediumContrast))
                setTypeface(null, Typeface.NORMAL)
            }
            rightMessageContainer.apply {
                alpha = 1.0f
                setBackgroundResource(R.drawable.receiver_message_background)
            }

            when (chat.type) {
                "text" -> {
                    receiverMessageText.text = chat.content
                    receiverMessageText.visibility = View.VISIBLE
                }
                "image" -> {
                    receiverMessageText.text = "📷 Image"
                }
                "file" -> {
                    receiverMessageText.text = "📎 File Attachment"
                }
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