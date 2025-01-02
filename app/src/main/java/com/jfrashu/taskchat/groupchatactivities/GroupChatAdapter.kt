package com.jfrashu.taskchat.groupchatactivities

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ItemChatBinding
import com.jfrashu.taskchat.dataclasses.GroupChat
import com.jfrashu.taskchat.dataclasses.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupChatAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: ((GroupChat) -> Unit)? = null
) : ListAdapter<GroupChat, GroupChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

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
        private val onMessageLongClick: ((GroupChat) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(chat: GroupChat, currentUserId: String, showSenderName: Boolean, userName: String?) {
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

            // Set up long click listener only if message is not deleted and is sent by current user
            if (!chat.isDeleted && isSentByMe) {
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
        private fun formatTimestamp(timestamp: Long): String {
            val date = Date(timestamp)
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("d MMM yy", Locale.getDefault())

            val timePart = timeFormat.format(date)
            val datePart = dateFormat.format(date)

            // Extract day and add the correct suffix
            val day = datePart.split(" ")[0].toInt()
            val suffix = when {
                day in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }

            val formattedDate = datePart.replaceFirst("$day", "$day$suffix")
            return "$timePart $formattedDate"
        }

        private fun setupSenderMessage(chat: GroupChat) {
            binding.apply {
                if (chat.isDeleted) {
                    senderMessageText.apply {
                        text = "❌ Message Deleted"
                        setTextColor(Color.parseColor("#6C6C6C"))
                        setTypeface(null, Typeface.ITALIC)
                        visibility = View.VISIBLE
                    }
                    leftMessageContainer.apply {
                        setBackgroundColor(Color.parseColor("#E6E6E6"))
                        alpha = 0.8f
                    }
                } else {
                    applySenderMessageStyle(chat)
                }
                senderTimestamp.text = formatTimestamp(chat.timestamp)
            }
        }

        private fun setupReceiverMessage(chat: GroupChat) {
            binding.apply {
                if (chat.isDeleted) {
                    receiverMessageText.apply {
                        text = "❌ Message Deleted"
                        setTextColor(Color.parseColor("#6C6C6C"))
                        setTypeface(null, Typeface.ITALIC)
                        visibility = View.VISIBLE
                    }
                    rightMessageContainer.apply {
                        setBackgroundColor(Color.parseColor("#E6E6E6"))
                        alpha = 0.8f
                    }
                } else {
                    applyReceiverMessageStyle(chat)
                }
                receiverTimestamp.text = formatTimestamp(chat.timestamp)
            }
        }

        private fun ItemChatBinding.applySenderMessageStyle(chat: GroupChat) {
            senderMessageText.apply {
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_onBackground))
                setTypeface(null, Typeface.NORMAL)
                visibility = View.VISIBLE
            }
            leftMessageContainer.apply {
                setBackgroundResource(R.drawable.sender_message_background)
                alpha = 1.0f
            }

            when (chat.type) {
                "text" -> {
                    senderMessageText.text = chat.content
                }
                "image" -> {
                    senderMessageText.text = "📷 Image"
                }
                "file" -> {
                    senderMessageText.text = "📎 File Attachment"
                }
            }
        }

        private fun ItemChatBinding.applyReceiverMessageStyle(chat: GroupChat) {
            receiverMessageText.apply {
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_onBackground))
                setTypeface(null, Typeface.NORMAL)
                visibility = View.VISIBLE
            }
            rightMessageContainer.apply {
                setBackgroundResource(R.drawable.receiver_message_background)
                alpha = 1.0f
            }

            when (chat.type) {
                "text" -> {
                    receiverMessageText.text = chat.content
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

    class ChatDiffCallback : DiffUtil.ItemCallback<GroupChat>() {
        override fun areItemsTheSame(oldItem: GroupChat, newItem: GroupChat): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(oldItem: GroupChat, newItem: GroupChat): Boolean {
            return oldItem == newItem
        }
    }
}