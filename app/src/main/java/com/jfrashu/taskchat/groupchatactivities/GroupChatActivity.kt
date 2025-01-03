// GroupChatActivity.kt
package com.jfrashu.taskchat.groupchatactivities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ActivityChatBinding
import com.jfrashu.taskchat.databinding.ActivityGroupChatBinding
import com.jfrashu.taskchat.dataclasses.Chat
import com.jfrashu.taskchat.dataclasses.GroupChat
import com.jfrashu.taskchat.taskactivities.TaskInfoActivity
import java.util.UUID

class GroupChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupChatBinding
    private lateinit var adapter: GroupChatAdapter
    private var groupId: String = ""
    private var snapshotListener: ListenerRegistration? = null
    private var lastLoadedMessageTimestamp: Long? = null
    private var isLoadingMore = false
    private var allMessagesLoaded = false


    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val MESSAGES_PER_PAGE = 50
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId") ?: ""
        if (groupId.isEmpty()) {
            showToast("Invalid group")
            finish()
            return
        }

        setupUI()
        setupBackButton()
        checkGroupAccess()
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        adapter = GroupChatAdapter(
            currentUserId = currentUserId,
            onMessageLongClick = { chat -> showMessageOptionsDialog(chat) }
        )

        binding.groupChatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupChatActivity).apply {
                stackFromEnd = true
                reverseLayout = false
            }
            adapter = this@GroupChatActivity.adapter
            itemAnimator = null
        }

        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.sendButton.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.sendButton.setOnClickListener { sendMessage() }

        setupScrollListener()
    }

    private fun loadMoreMessages() {
        if (isLoadingMore || allMessagesLoaded || lastLoadedMessageTimestamp == null) {
            return
        }

        isLoadingMore = true

        // Show loading indicator if you have one
        binding.loadingProgressBar?.visibility = View.VISIBLE

        val query = db.collection("groups")
            .document(groupId)
            .collection("groupchat")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .whereEqualTo("isDeleted", false)
            .whereLessThan("timestamp", lastLoadedMessageTimestamp!!)
            .limit(MESSAGES_PER_PAGE.toLong())

        query.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    allMessagesLoaded = true
                    showToast("No more messages to load")
                } else {
                    val oldMessages = snapshot.toObjects(GroupChat::class.java)

                    // Update the timestamp for the next pagination
                    lastLoadedMessageTimestamp = snapshot.documents.last().getLong("timestamp")

                    // Get current list and add new messages at the beginning
                    val currentList = adapter.currentList.toMutableList()
                    currentList.addAll(0, oldMessages)

                    // Calculate the position to maintain scroll
                    val scrollPosition = oldMessages.size

                    // Submit the updated list
                    adapter.submitList(currentList) {
                        // Maintain scroll position after new items are added
                        val layoutManager = binding.groupChatRecyclerView.layoutManager as LinearLayoutManager
                        layoutManager.scrollToPositionWithOffset(scrollPosition, 0)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("GroupChatActivity", "Error loading more messages", e)
                showToast("Failed to load more messages: ${e.message}")
            }
            .addOnCompleteListener {
                isLoadingMore = false
                // Hide loading indicator if you have one
                binding.loadingProgressBar?.visibility = View.GONE
            }
    }

    private fun setupScrollListener() {
        binding.groupChatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!isLoadingMore && !allMessagesLoaded) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                    // Load more when user scrolls near the top
                    if (firstVisibleItem <= 5) {
                        loadMoreMessages()
                    }
                }
            }
        })
    }

    private fun setupMessageListener() {
        val initialQuery = db.collection("groups")
            .document(groupId)
            .collection("groupchat")
            .whereEqualTo("isDeleted", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MESSAGES_PER_PAGE.toLong())

        initialQuery.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                lastLoadedMessageTimestamp = snapshot.documents.last().getLong("timestamp")
                val messages = snapshot.toObjects(GroupChat::class.java)
                adapter.submitList(messages.reversed())
            }
            setupRealtimeUpdates()
        }.addOnFailureListener { e ->
            Log.e("GroupChatActivity", "Error loading initial messages", e)
            showToast("Error loading messages: ${e.message}")
        }
    }

    private fun setupRealtimeUpdates() {
        val query = db.collection("groups")
            .document(groupId)
            .collection("groupchat")
            .whereEqualTo("isDeleted", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        snapshotListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("GroupChatActivity", "Listen failed", error)
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                val messages = querySnapshot.toObjects(GroupChat::class.java)
                adapter.submitList(messages.reversed()) {
                    binding.groupChatRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun sendMessage(type: String = "text") {
        val messageText = binding.messageInput.text.toString().trim()
        if (messageText.isEmpty()) return

        val messageData = mapOf(
            "messageId" to UUID.randomUUID().toString(),
            "groupId" to groupId,
            "senderId" to currentUserId,
            "content" to messageText,
            "timestamp" to System.currentTimeMillis(),
            "type" to type,
            "attachmentUrl" to "",
            "isDeleted" to false
        )

        binding.sendButton.isEnabled = false

        db.collection("groups")
            .document(groupId)
            .collection("groupchat")
            .document(messageData["messageId"] as String)
            .set(messageData)
            .addOnSuccessListener {
                binding.messageInput.text.clear()
                binding.sendButton.isEnabled = true
            }
            .addOnFailureListener { e ->
                showToast("Failed to send message: ${e.message}")
                binding.sendButton.isEnabled = true
            }
    }

    private fun checkGroupAccess() {
        db.collection("groups").document(groupId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    showToast("Group not found")
                    finish()
                    return@addOnSuccessListener
                }

                val members = document.get("members") as? List<String> ?: emptyList()
                if (!members.contains(currentUserId)) {
                    showToast("You are not a member of this group")
                    finish()
                    return@addOnSuccessListener
                }

                binding.chatNameTextView.text = document.getString("name") ?: "Group Chat"
                setupMessageListener()
            }
            .addOnFailureListener {
                showToast("Error checking access")
                finish()
            }
    }

    private fun showMessageOptionsDialog(chat: GroupChat) {
        if (chat.senderId != currentUserId) return

        val options = arrayOf("Edit Message", "Mark as Deleted","Copy Message")
        AlertDialog.Builder(this)
            .setTitle("Message Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editMessage(chat)
                    1 -> markMessageAsDeleted(chat)
                    2 -> copyMessageToClipboard(chat)
                }
            }
            .show()
    }
    private fun copyMessageToClipboard(chat: GroupChat) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message", chat.content)
        clipboard.setPrimaryClip(clip)
        showToast("Message copied to clipboard")
    }

    private fun editMessage(chat: GroupChat) {
        val editText = android.widget.EditText(this).apply {
            setText(chat.content)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newContent = editText.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    updateMessage(chat.messageId, newContent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMessage(messageId: String, newContent: String) {
        db.collection("groups")
            .document(groupId)
            .collection("groupchat")
            .document(messageId)
            .update("content", newContent)
            .addOnFailureListener { e ->
                showToast("Failed to update message: ${e.message}")
            }
    }

    private fun markMessageAsDeleted(chat: GroupChat) {
        AlertDialog.Builder(this)
            .setTitle("Mark as Deleted")
            .setMessage("Are you sure you want to mark this message as deleted?")
            .setPositiveButton("Yes") { _, _ ->
                db.collection("groups")
                    .document(groupId)
                    .collection("groupchat")
                    .document(chat.messageId)
                    .set(mapOf(
                        "messageId" to chat.messageId,
                        "groupId" to chat.groupId,
                        "senderId" to chat.senderId,
                        "content" to "",
                        "timestamp" to chat.timestamp,
                        "type" to chat.type,
                        "attachmentUrl" to "",
                        "isDeleted" to true
                    ))
                    .addOnFailureListener { e ->
                        showToast("Failed to mark message as deleted: ${e.message}")
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }
}