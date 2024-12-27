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
    private var pendingDownloadChat: Chat? = null

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val STORAGE_PERMISSION_CODE = 101
        private const val MESSAGES_PER_PAGE = 50
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        taskId = intent.getStringExtra("taskId") ?: ""
        groupId = intent.getStringExtra("groupId") ?: ""

        if (taskId.isEmpty() || groupId.isEmpty()) {
            Toast.makeText(this, "Invalid task or group", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupBackButton()
        checkTaskAccess()
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

    private fun setupMessageListener() {
        val chatsRef = db.collection("groups")
            .document(groupId)
            .collection("tasks")
            .document(taskId)
            .collection("chats")
            .orderBy("timestamp", Query.Direction.ASCENDING)

        chatsRef.addSnapshotListener(this) { snapshot, error ->
            if (error != null) {
                Log.e("ChatActivity", "Listen failed: ${error.message}", error)
                Toast.makeText(this, "Error loading messages: ${error.message}", Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                val newMessages = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)
                }

                adapter.submitList(newMessages) {
                    if (newMessages.isNotEmpty()) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                        val totalItems = layoutManager.itemCount
                        val isNearBottom = totalItems - lastVisibleItem <= 3

                        if (isNearBottom) {
                            recyclerView.post {
                                recyclerView.smoothScrollToPosition(newMessages.size - 1)
                            }
                        }
                    }
                }
            }
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                if (firstVisibleItem == 0) {
                    loadMoreMessages(chatsRef)
                }
            }
        })
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
            Toast.makeText(this, "Cannot send messages in completed tasks", Toast.LENGTH_SHORT).show()
            return
        }

        val messageText = binding.messageInput.text.toString().trim()
        if (messageText.isEmpty() && attachmentUrl.isEmpty()) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            Toast.makeText(this, "Please sign in to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        val message = Chat(
            messageId = UUID.randomUUID().toString(),
            taskId = taskId,
            senderId = currentUser.uid,
            content = messageText,
            timestamp = System.currentTimeMillis(),
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
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ChatActivity", "Error sending message", e)
                binding.sendButton.isEnabled = true
            }
    }

    private fun checkTaskAccess() {
        db.document("groups/$groupId/tasks/$taskId")
            .get()
            .addOnSuccessListener { taskDoc ->
                if (!taskDoc.exists()) {
                    Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                taskStatus = taskDoc.getString("status") ?: ""

                db.document("groups/$groupId")
                    .get()
                    .addOnSuccessListener { groupDoc ->
                        val members = groupDoc.get("members") as? List<String> ?: emptyList()
                        if (!members.contains(currentUserId)) {
                            Toast.makeText(this, "No access to this chat", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, "Error checking access", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading task", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun disableInput(hint: String) {
        binding.messageInput.isEnabled = false
        binding.sendButton.isEnabled = false
        binding.messageInput.hint = hint
        Toast.makeText(this, hint, Toast.LENGTH_SHORT).show()
    }

    private fun showMessageOptionsDialog(chat: Chat) {
        val isMyMessage = chat.senderId == currentUserId
        val options = ArrayList<String>().apply {
            add("Copy")
            if (isMyMessage && taskStatus != "completed") {
                add("Delete")
            }
            if (chat.type == "file" && chat.attachmentUrl.isNotEmpty()) {
                add("Download")
            }
        }

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Message Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Copy" -> copyMessageToClipboard(chat)
                    "Delete" -> confirmDeleteMessage(chat)
                    "Download" -> downloadAttachment(chat)
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
        Toast.makeText(this, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Please sign in to delete messages", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.senderId != currentUser.uid) {
            Toast.makeText(this, "You cannot delete other users' messages", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete message: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ChatActivity", "Error deleting message", e)
            }
    }

    private fun downloadAttachment(chat: Chat) {
        if (chat.attachmentUrl.isEmpty()) {
            Toast.makeText(this, "No attachment found", Toast.LENGTH_SHORT).show()
            return
        }

        pendingDownloadChat = chat

        if (checkStoragePermission()) {
            startDownload(chat)
        } else {
            requestStoragePermission()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_CODE
        )
    }

    private fun startDownload(chat: Chat) {
        val request = DownloadManager.Request(Uri.parse(chat.attachmentUrl))
            .setTitle("Downloading attachment")
            .setDescription("Downloading")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "TaskChat_${System.currentTimeMillis()}_${chat.attachmentUrl.substringAfterLast("/")}"
            )

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pendingDownloadChat?.let { chat ->
                        startDownload(chat)
                        pendingDownloadChat = null
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Permission denied, cannot download files",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
}