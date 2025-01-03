package com.jfrashu.taskchat.chatactivities

import android.Manifest
import androidx.appcompat.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.databinding.ActivityChatBinding
import com.jfrashu.taskchat.dataclasses.Chat
import com.jfrashu.taskchat.taskactivities.TaskInfoActivity
import java.util.UUID

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private var taskId: String = ""
    private var groupId: String = ""
    private var taskStatus: String = ""
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
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        taskId = intent.getStringExtra("taskId") ?: ""
        groupId = intent.getStringExtra("groupId") ?: ""

        if (taskId.isEmpty() || groupId.isEmpty()) {
            showToast("Invalid task or group")
            finish()
            return
        }

        setupUI()
        setupBackButton()
        checkTaskAccess()
    }

    private fun setupUI() {
        recyclerView = binding.chatRecyclerView
        adapter = ChatAdapter(
            currentUserId = currentUserId,
            onMessageLongClick = { chat -> showMessageOptionsDialog(chat) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
                reverseLayout = false
            }
            adapter = this@ChatActivity.adapter
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

    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!isLoadingMore && !allMessagesLoaded) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

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
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .whereEqualTo("isDeleted", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MESSAGES_PER_PAGE.toLong())

        initialQuery.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                lastLoadedMessageTimestamp = snapshot.documents.last().getLong("timestamp")
                val messages = snapshot.toObjects(Chat::class.java)
                adapter.submitList(messages.reversed())
            }
            setupRealtimeUpdates()
        }.addOnFailureListener { e ->
            Log.e("ChatActivity", "Error loading initial messages", e)
            showToast("Error loading messages: ${e.message}")
        }
    }

    private fun setupRealtimeUpdates() {
        val query = db.collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .whereEqualTo("isDeleted", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        snapshotListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ChatActivity", "Listen failed", error)
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                val messages = querySnapshot.toObjects(Chat::class.java)
                adapter.submitList(messages.reversed()) {
                    // Only scroll to bottom if we're already near the bottom
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    val totalItems = layoutManager.itemCount
                    if (lastVisibleItem >= totalItems - 3) {
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    private fun loadMoreMessages() {
        if (isLoadingMore || allMessagesLoaded || lastLoadedMessageTimestamp == null) {
            return
        }

        isLoadingMore = true
        binding.loadingProgressBar?.visibility = View.VISIBLE

        val query = db.collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .whereEqualTo("isDeleted", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .whereLessThan("timestamp", lastLoadedMessageTimestamp!!)
            .limit(MESSAGES_PER_PAGE.toLong())

        query.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    allMessagesLoaded = true
                    showToast("No more messages to load")
                } else {
                    val oldMessages = snapshot.toObjects(Chat::class.java)
                    lastLoadedMessageTimestamp = snapshot.documents.last().getLong("timestamp")

                    val currentList = adapter.currentList.toMutableList()
                    currentList.addAll(0, oldMessages)

                    val scrollPosition = oldMessages.size
                    adapter.submitList(currentList) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        layoutManager.scrollToPositionWithOffset(scrollPosition, 0)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error loading more messages", e)
                showToast("Failed to load more messages: ${e.message}")
            }
            .addOnCompleteListener {
                isLoadingMore = false
                binding.loadingProgressBar?.visibility = View.GONE
            }
    }

    private fun sendMessage(type: String = "text") {
        if (taskStatus == "completed") {
            showToast("Cannot send messages in completed tasks")
            return
        }

        val messageText = binding.messageInput.text.toString().trim()
        if (messageText.isEmpty()) return

        val messageData = mapOf(
            "messageId" to UUID.randomUUID().toString(),
            "taskId" to taskId,
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
            .collection("tasks")
            .document(taskId)
            .collection("chats")
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

    private fun editMessage(chat: Chat) {
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
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .document(messageId)
            .update("content", newContent)
            .addOnFailureListener { e ->
                showToast("Failed to update message: ${e.message}")
            }
    }

    private fun deleteMessage(chat: Chat) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            showToast("Please sign in to delete messages")
            return
        }

        if (chat.senderId != currentUser.uid) {
            showToast("You cannot delete other users' messages")
            return
        }

        db.collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .document(chat.messageId)
            .update(mapOf(
                "content" to chat.content,
                "isDeleted" to true
            ))
            .addOnSuccessListener {
                showToast("Message deleted")
            }
            .addOnFailureListener { e ->
                showToast("Failed to delete message: ${e.message}")
            }
    }

    private fun showMessageOptionsDialog(chat: Chat) {
        val isMyMessage = chat.senderId == currentUserId
        val options = ArrayList<String>().apply {
            add("Copy")
            if (isMyMessage && taskStatus != "completed") {
                add("Delete")
                add("Edit")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Message Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Copy" -> copyMessageToClipboard(chat)
                    "Delete" -> confirmDeleteMessage(chat)
                    "Edit" -> editMessage(chat)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun copyMessageToClipboard(chat: Chat) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message", chat.content)
        clipboard.setPrimaryClip(clip)
        showToast("Message copied to clipboard")
    }

    private fun confirmDeleteMessage(chat: Chat) {
        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ -> deleteMessage(chat) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkTaskAccess() {
        db.document("groups/$groupId/tasks/$taskId")
            .get()
            .addOnSuccessListener { taskDoc ->
                if (!taskDoc.exists()) {
                    showToast("Task not found")
                    finish()
                    return@addOnSuccessListener
                }

                taskStatus = taskDoc.getString("status") ?: ""

                db.document("groups/$groupId")
                    .get()
                    .addOnSuccessListener { groupDoc ->
                        val members = groupDoc.get("members") as? List<String> ?: emptyList()
                        if (!members.contains(currentUserId)) {
                            showToast("No access to this chat")
                            finish()
                            return@addOnSuccessListener
                        }

                        when (taskStatus) {
                            "completed" -> {
                                disableInput("Task completed - Chat is read-only")
                            }
                            "pending" -> {
                                disableInput("Task is pending & cannot chat yet")
                            }
                        }

                        setupMessageListener()
                        binding.chatNameTextView.text = taskDoc.getString("title") ?: "Task Chat"
                    }
                    .addOnFailureListener {
                        showToast("Error checking access")
                        finish()
                    }
            }
            .addOnFailureListener {
                showToast("Error loading task")
                finish()
            }
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            val intent = Intent(this, TaskInfoActivity::class.java).apply {
                putExtra("groupId", groupId)
                putExtra("taskId", taskId)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun disableInput(hint: String) {
        binding.messageInput.isEnabled = false
        binding.sendButton.isEnabled = false
        binding.messageInput.hint = hint
        showToast(hint)
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }
}