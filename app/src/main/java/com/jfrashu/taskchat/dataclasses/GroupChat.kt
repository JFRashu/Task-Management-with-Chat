package com.jfrashu.taskchat.dataclasses

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

@Keep
data class GroupChat(
    @get:PropertyName("messageId") val messageId: String = "",
    @get:PropertyName("groupId") val groupId: String = "",
    @get:PropertyName("senderId") val senderId: String = "",
    @get:PropertyName("content") val content: String = "",
    @get:PropertyName("timestamp") val timestamp: Long = 0L,
    @get:PropertyName("type") val type: String = "text",
    @get:PropertyName("attachmentUrl") val attachmentUrl: String = "",
    @get:PropertyName("isDeleted") val isDeleted: Boolean = false
) {
    constructor() : this("", "", "", "", 0L, "text", "", false)
}
