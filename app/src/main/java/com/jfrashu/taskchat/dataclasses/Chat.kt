package com.jfrashu.taskchat.dataclasses

data class Chat(
    val messageId: String = "",
    val taskId: String = "", // ID of the task this chat belongs to
    val senderId: String = "", // userId of sender
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text", // text, image, file
    val attachmentUrl: String = "" // URL for attachments if any
)

