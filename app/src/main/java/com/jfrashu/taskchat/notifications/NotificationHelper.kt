package com.jfrashu.taskchat.notifications

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage

class NotificationHelper(private val db: FirebaseFirestore) {
    fun sendNotification(
        recipientUserId: String,
        title: String,
        message: String,
        type: String,
        groupId: String? = null,
        taskId: String? = null
    ) {
        db.collection("users").document(recipientUserId)
            .get()
            .addOnSuccessListener { document ->
                val recipientToken = document.getString("fcmToken")
                Log.d("NOTIFICATIONS", "Recipient token: $recipientToken")

                recipientToken?.let { token ->
                    // Create notification data
                    val notificationData = hashMapOf(
                        "to" to token,
                        "data" to hashMapOf(
                            "title" to title,
                            "message" to message,
                            "type" to type,
                            "groupId" to (groupId ?: ""),
                            "taskId" to (taskId ?: "")
                        )
                    )

                    // Send to FCM
                    FirebaseMessaging.getInstance().send(
                        RemoteMessage.Builder(token)
                        .setData(notificationData["data"] as Map<String, String>)
                        .build())

                    // Store in Firestore
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