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

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val STORAGE_PERMISSION_CODE = 101
        private const val MESSAGES_PER_PAGE = 50
    }

    private var lastLoadedMessageTimestamp: Long? = null
    private var isLoadingMore = false
    private var allMessagesLoaded = false

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

    private fun setupMessageListener() {
        if (isFinishing || isDestroyed) return

        // Initial query to get the most recent messages
        val initialQuery = db.collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MESSAGES_PER_PAGE.toLong())

        initialQuery.get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            if (!snapshot.isEmpty) {
                lastLoadedMessageTimestamp = snapshot.documents.last().getLong("timestamp")
            }

            // Set up real-time listener for new messages
            setupRealtimeUpdates()

        }.addOnFailureListener { e ->
            if (isFinishing || isDestroyed) return@addOnFailureListener
            Log.e("ChatActivity", "Error loading initial messages", e)
            showToast("Error loading messages: ${e.message}")
        }
    }

    private fun setupRealtimeUpdates() {
        if (isFinishing || isDestroyed) return

        val realtimeQuery = db.collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .apply {
                lastLoadedMessageTimestamp?.let {
                    startAfter(it)
                }
            }

        snapshotListener = realtimeQuery.addSnapshotListener { snapshot, error ->
            if (isFinishing || isDestroyed) return@addSnapshotListener

            if (error != null) {
                Log.e("ChatActivity", "Listen failed: ${error.message}", error)
                showToast("Error loading messages: ${error.message}")
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                val newMessages = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)
                }

                updateMessageList(newMessages)
            }
        }

        // Setup scroll listener for pagination
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (isLoadingMore || allMessagesLoaded) return

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                if (firstVisibleItem <= 5) {
                    loadMoreMessages()
                }
            }
        })
    }

    private fun loadMoreMessages() {
        if (isLoadingMore || allMessagesLoaded || lastLoadedMessageTimestamp == null) return
        isLoadingMore = true

        val oldMessagesQuery = db.collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .whereLessThan("timestamp", lastLoadedMessageTimestamp!!)
            .limit(MESSAGES_PER_PAGE.toLong())

        oldMessagesQuery.get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            if (snapshot.isEmpty) {
                allMessagesLoaded = true
            } else {
                val oldMessages = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)
                }

                lastLoadedMessageTimestamp = snapshot.documents.last().getLong("timestamp")
                updateMessageList(oldMessages, prepend = true)
            }
            isLoadingMore = false

        }.addOnFailureListener { e ->
            if (isFinishing || isDestroyed) return@addOnFailureListener
            Log.e("ChatActivity", "Error loading more messages", e)
            isLoadingMore = false
        }
    }

    private fun updateMessageList(newMessages: List<Chat>, prepend: Boolean = false) {
        val currentList = adapter.currentList.toMutableList()

        if (prepend) {
            // Remove duplicates and add new messages at the beginning
            val uniqueMessages = newMessages.filterNot { new ->
                currentList.any { existing -> existing.messageId == new.messageId }
            }
            currentList.addAll(0, uniqueMessages)
        } else {
            // Remove duplicates and add new messages at the end
            val uniqueMessages = newMessages.filterNot { new ->
                currentList.any { existing -> existing.messageId == new.messageId }
            }
            currentList.addAll(uniqueMessages)
        }

        // Sort messages by timestamp
        val sortedList = currentList.sortedBy { it.timestamp }

        adapter.submitList(sortedList) {
            if (!prepend && sortedList.isNotEmpty()) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItems = layoutManager.itemCount
                val isNearBottom = totalItems - lastVisibleItem <= 3

                if (isNearBottom) {
                    recyclerView.post {
                        recyclerView.smoothScrollToPosition(sortedList.size - 1)
                    }
                }
            }
        }
    }

    private fun setupUI() {
        recyclerView = findViewById(R.id.chatRecyclerView)
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

        binding.backButton.setOnClickListener { onBackPressed() }
        binding.sendButton.setOnClickListener { sendMessage() }
    }



    private fun loadMoreMessages(query: Query) {
        query.limit(MESSAGES_PER_PAGE.toLong()).get()
            .addOnSuccessListener { documents ->
                val oldMessages = documents.toObjects(Chat::class.java)
                val currentList = adapter.currentList.toMutableList()
                currentList.addAll(0, oldMessages)
                adapter.submitList(currentList)
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error loading more messages", e)
            }
    }

    private fun sendMessage(type: String = "text", attachmentUrl: String = "") {
        if (taskStatus == "completed") {
            showToast("Cannot send messages in completed tasks")
            return
        }

        val messageText = binding.messageInput.text.toString().trim()
        if (messageText.isEmpty() && attachmentUrl.isEmpty()) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            showToast("Please sign in to send messages")
            return
        }

        val message = Chat(
            messageId = UUID.randomUUID().toString(),
            taskId = taskId,
            senderId = currentUser.uid,
            content = messageText,
            timestamp = System.currentTimeMillis(),
            isDeleted = false,
            type = type,
            attachmentUrl = attachmentUrl
        )

        binding.sendButton.isEnabled = false

        db.collection("groups").document(groupId)
            .collection("tasks").document(taskId)
            .collection("chats").document(message.messageId)
            .set(message)
            .addOnSuccessListener {
                binding.messageInput.text.clear()
                binding.sendButton.isEnabled = true
            }
            .addOnFailureListener { e ->
                showToast("Failed to send message: ${e.message}")
                Log.e("ChatActivity", "Error sending message", e)
                binding.sendButton.isEnabled = true
            }
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

    private fun disableInput(hint: String) {
        binding.messageInput.isEnabled = false
        binding.sendButton.isEnabled = false
        binding.messageInput.hint = hint
        showToast(hint)
    }

    private fun showMessageOptionsDialog(chat: Chat) {
        val isMyMessage = chat.senderId == currentUserId
        val options = ArrayList<String>().apply {
            add("Copy")
            if (isMyMessage && taskStatus != "completed") {
                add("Delete")
            }
        }

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Message Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Copy" -> copyMessageToClipboard(chat)
                    "Delete" -> confirmDeleteMessage(chat)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun copyMessageToClipboard(chat: Chat) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message", chat.content)
        clipboard.setPrimaryClip(clip)
        showToast("Message copied to clipboard")
    }

    private fun confirmDeleteMessage(chat: Chat) {
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ -> deleteMessage(chat) }
            .setNegativeButton("Cancel", null)
            .show()
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

        db.collection("groups").document(groupId)
            .collection("tasks").document(taskId)
            .collection("chats").document(chat.messageId)
            .update(mapOf(
                "content" to "",
                "isDeleted" to true
            ))
            .addOnSuccessListener {
                showToast("Message deleted")
            }
            .addOnFailureListener { e ->
                showToast("Failed to delete message: ${e.message}")
                Log.e("ChatActivity", "Error deleting message", e)
            }
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            val intent = Intent(this, TaskInfoActivity::class.java).apply {
                putExtra("groupId", groupId)
                putExtra("taskId", taskId)
            }
            startActivity(intent)
            finish()
        }
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