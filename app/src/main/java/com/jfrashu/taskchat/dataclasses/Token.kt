package com.jfrashu.taskchat.dataclasses

data class Token(
    val userId: String = "",
    val token: String = "",
    val lastUpdate: Long = 0L
)
