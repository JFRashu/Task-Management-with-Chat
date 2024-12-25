package com.jfrashu.taskchat.dataclasses


data class GroupInvitationWithDetails(
    val invitation: GroupInvitation,
    val groupName: String,
    val inviterName: String
)