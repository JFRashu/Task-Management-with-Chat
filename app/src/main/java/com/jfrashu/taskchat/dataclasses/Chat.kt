package com.jfrashu.taskchat.dataclasses

data class Chat(
    val messageId: String = "",
    val taskId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val type: String = "text",
    val attachmentUrl: String = "",
    val isDeleted: Boolean = false  // Add this field
)
