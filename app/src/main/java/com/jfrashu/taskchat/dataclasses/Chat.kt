package com.jfrashu.taskchat.dataclasses

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

@Keep
data class Chat(
    @PropertyName("messageId") val messageId: String = "",
    @PropertyName("taskId") val taskId: String = "",
    @PropertyName("senderId") val senderId: String = "",
    @PropertyName("content") val content: String = "",
    @PropertyName("timestamp") val timestamp: Long = 0L,
    @PropertyName("type") val type: String = "text",
    @PropertyName("attachmentUrl") val attachmentUrl: String = "",
    @PropertyName("isDeleted") val isDeleted: Boolean = false
) {
    // Add a no-arg constructor for Firebase
    constructor() : this("", "", "", "", 0L, "text", "", false)
}