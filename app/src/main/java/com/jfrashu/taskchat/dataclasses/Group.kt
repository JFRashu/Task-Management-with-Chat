package com.jfrashu.taskchat.dataclasses

data class Group(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val adminId: String = "", // Group leader/admin userId
    val members: List<String> = listOf(), // List of member userIds
    val tasks: List<String> = listOf(), // List of task IDs
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivity: Long =System.currentTimeMillis()
)
