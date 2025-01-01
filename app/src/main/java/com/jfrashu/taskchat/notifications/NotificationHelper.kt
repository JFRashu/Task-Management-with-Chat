package com.jfrashu.taskchat.notifications

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class NotificationHelper(private val db: FirebaseFirestore) {
    fun sendNotification(
        recipientUserId: String,
        title: String,
        message: String,
        type: String,
        groupId: String? = null,
        taskId: String? = null
    ) {
        // Get recipient's FCM token
        db.collection("users").document(recipientUserId)
            .get()
            .addOnSuccessListener { document ->
                val recipientToken = document.getString("fcmToken")
                recipientToken?.let { token ->
                    // Create notification data
                    val notificationData = hashMapOf(
                        "title" to title,
                        "message" to message,
                        "type" to type
                    )

                    // Add optional data
                    groupId?.let { notificationData["groupId"] = it }
                    taskId?.let { notificationData["taskId"] = it }

                    // Store notification in Firestore
                    db.collection("notifications")
                        .add(
                            hashMapOf(
                                "recipientId" to recipientUserId,
                                "title" to title,
                                "message" to message,
                                "type" to type,
                                "groupId" to groupId,
                                "taskId" to taskId,
                                "timestamp" to FieldValue.serverTimestamp(),
                                "read" to false
                            )
                        )
                }
            }
    }
}