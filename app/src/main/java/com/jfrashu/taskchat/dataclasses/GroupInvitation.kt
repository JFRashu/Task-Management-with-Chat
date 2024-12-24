package com.jfrashu.taskchat.dataclasses

data class GroupInvitation(
    val invitationId: String = "",
    val groupId: String = "",
    val invitedBy: String = "", // must be admin's userId
    val invitedUser: String = "", // userId of invited person
    val status: String = "pending", // pending, accepted, rejected
    val timestamp: Long = System.currentTimeMillis()
)
