package com.jfrashu.taskchat.dataclasses

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

@Keep
data class Task(
    @PropertyName("taskId") val taskId: String = "",
    @PropertyName("groupId") val groupId: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("createdBy") val createdBy: String = "",
    @PropertyName("status") val status: String = "pending", // pending, in_progress, completed
    @PropertyName("lastActivity") val lastActivity: Long = 0,
    @PropertyName("createdAt") val createdAt: Long = 0,
    @PropertyName("lastMessage") val lastMessage: String = "",
    @PropertyName("isDeleted") val isDeleted: Boolean = false
) {
    // Add a no-arg constructor for Firebase
    constructor() : this("", "", "", "", "", "pending", 0, 0, "", false)
}