package com.jfrashu.taskchat.dataclasses

import com.google.firebase.Timestamp


data class Task(
    val taskId: String = "",
    val groupId: String = "", // ID of the group this task belongs to
    val title: String = "",
    val description: String = "",
    val createdBy: String = "", // admin's userId
    val status: String = "pending", // pending, in_progress, completed
    val lastActivity: Long =  System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessage : String =""

)
