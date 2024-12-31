package com.jfrashu.taskchat.dataclasses

data class GroupChat(
    val messageId: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val type: String = "text",
    val attachmentUrl: String = "",
    val isDeleted: Boolean = false  // Add this field
)
