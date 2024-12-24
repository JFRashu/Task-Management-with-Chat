package com.jfrashu.taskchat.dataclasses

data class User(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val status: String = "online",
    val lastActive: Long = System.currentTimeMillis()
)
