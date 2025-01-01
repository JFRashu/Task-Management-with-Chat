package com.jfrashu.taskchat.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.SplashActivity
import com.jfrashu.taskchat.chatactivities.ChatActivity
import com.jfrashu.taskchat.groupactivities.GroupInfoActivity
import com.jfrashu.taskchat.taskactivities.TaskInfoActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val db = FirebaseFirestore.getInstance()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Store the token in Firestore for the current user
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.uid)
                .update("fcmToken", token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"] ?: "general"

        if (type == "task_chat") {
            // Use custom sound for chat notifications
            showNotification(
                remoteMessage.data["title"] ?: "New Message",
                remoteMessage.data["message"] ?: "",
                createChatIntent(
                    remoteMessage.data["groupId"],
                    remoteMessage.data["taskId"]
                )
            )
        } else {
            // Handle other notification types based on your existing implementation
            val title = remoteMessage.data["title"] ?: "New Notification"
            val message = remoteMessage.data["message"] ?: ""
            val groupId = remoteMessage.data["groupId"]
            val taskId = remoteMessage.data["taskId"]

            // Create appropriate intent based on notification type
            val intent = when(type) {
                "task" -> Intent(this, TaskInfoActivity::class.java).apply {
                    putExtra("groupId", groupId)
                    putExtra("taskId", taskId)
                }
                "group" -> Intent(this, GroupInfoActivity::class.java).apply {
                    putExtra("groupId", groupId)
                }
                else -> Intent(this, SplashActivity::class.java)
            }

            showNotification(title, message, intent)
        }
    }

    private fun createChatIntent(groupId: String?, taskId: String?): Intent {
        return Intent(this, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("groupId", groupId)
            putExtra("taskId", taskId)
        }
    }

    private fun showNotification(title: String, message: String, intent: Intent) {
        val channelId = "task_chat_notifications"
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TaskChat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}