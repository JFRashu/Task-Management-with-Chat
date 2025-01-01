package com.jfrashu.taskchat.dataclasses

data class NotificationData(
    val recipientId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "",
    val groupId: String? = null,
    val taskId: String? = null,
    val timestamp: Long = 0,
    val read: Boolean = false
)
