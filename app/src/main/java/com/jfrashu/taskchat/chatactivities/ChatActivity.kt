package com.jfrashu.taskchat.chatactivities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.chatactivities.ChatAdapter// Make sure this import is correct
import com.jfrashu.taskchat.databinding.ActivityChatBinding
import com.jfrashu.taskchat.dataclasses.Chat
import java.util.UUID

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView // Declare RecyclerView
    private var taskId: String = ""
    private var groupId: String = ""
    private var taskStatus: String = ""

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get IDs from intent
        taskId = intent.getStringExtra("taskId") ?: ""
        groupId = intent.getStringExtra("groupId") ?: ""

        if (taskId.isEmpty() || groupId.isEmpty()) {
            Toast.makeText(this, "Invalid task or group", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        checkTaskAccess()
    }

    private fun setupUI() {
        // Setup RecyclerView
        recyclerView = findViewById(R.id.chatRecyclerView) // Initialize RecyclerView
        adapter = ChatAdapter(currentUserId)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true // Messages appear from the bottom
            }
            adapter = this@ChatActivity.adapter
        }

        // Setup input field
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.sendButton.isEnabled = !s.isNullOrBlank()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup click listeners
        binding.backButton.setOnClickListener { onBackPressed() }
        binding.sendButton.setOnClickListener { sendMessage() }
        binding.attachmentButton.setOnClickListener { showAttachmentDialog() }
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

                        if (taskStatus == "completed") {
                            binding.messageInput.isEnabled = false
                            binding.sendButton.isEnabled = false
                            binding.attachmentButton.isEnabled = false
                            Toast.makeText(this, "Task completed - Chat is read-only", Toast.LENGTH_SHORT).show()
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

    private fun setupMessageListener() {
        db.collection("chats")
            .whereEqualTo("taskId", taskId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ChatActivity", "Listen failed.", e) // Log the error
                    Toast.makeText(this, "Error loading messages", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val newMessages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)
                } ?: emptyList()

                adapter.submitList(newMessages)

                if (newMessages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(newMessages.size - 1)
                }
            }
    }

    private fun sendMessage(type: String = "text", attachmentUrl: String = "") {
        if (taskStatus == "completed") {
            Toast.makeText(this, "Cannot send messages in completed tasks", Toast.LENGTH_SHORT).show()
            return
        }

        val messageText = binding.messageInput.text.toString().trim()
        if (messageText.isEmpty() && attachmentUrl.isEmpty()) return

        val message = Chat(
            messageId = UUID.randomUUID().toString(),
            taskId = taskId,
            senderId = currentUserId,
            content = messageText,
            timestamp = System.currentTimeMillis(),
            type = type,
            attachmentUrl = attachmentUrl
        )

        binding.sendButton.isEnabled = false

        db.collection("chats")
            .document(message.messageId)
            .set(message)
            .addOnSuccessListener {
                binding.messageInput.text.clear()
                binding.sendButton.isEnabled = true
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                binding.sendButton.isEnabled = true
            }
    }

    private fun showAttachmentDialog() {
        Toast.makeText(this, "Attachment feature coming soon", Toast.LENGTH_SHORT).show()
    }
}