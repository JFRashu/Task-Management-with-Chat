package com.jfrashu.taskchat.dataclasses

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

@Keep
data class Group(
    @PropertyName("groupId") val groupId: String = "",
    @PropertyName("name") val name: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("adminId") val adminId: String = "",
    @PropertyName("members") val members: List<String> = listOf(),
    @PropertyName("tasks") val tasks: List<String> = listOf(),
    @PropertyName("createdAt") val createdAt: Long = 0,
    @PropertyName("lastActivity") val lastActivity: Long = 0,
    @PropertyName("isDeleted") val isDeleted: Boolean = false
) {
    // Add a no-arg constructor for Firebase
    constructor() : this("", "", "", "", listOf(), listOf(), 0, 0, false)
}