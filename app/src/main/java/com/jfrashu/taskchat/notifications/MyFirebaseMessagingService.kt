package com.jfrashu.taskchat.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jfrashu.taskchat.R
import com.jfrashu.taskchat.SplashActivity
import com.jfrashu.taskchat.TaskChatApplication
import com.jfrashu.taskchat.chatactivities.ChatActivity
import com.jfrashu.taskchat.groupactivities.GroupInfoActivity
import com.jfrashu.taskchat.taskactivities.TaskInfoActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val db = FirebaseFirestore.getInstance()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "New token: $token")

        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM_TOKEN", "Token updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM_TOKEN", "Failed to update token: ${e.message}")
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received: ${remoteMessage.data}")

        val type = remoteMessage.data["type"] ?: "general"
        val title = remoteMessage.data["title"] ?: "New Notification"
        val message = remoteMessage.data["message"] ?: ""
        val groupId = remoteMessage.data["groupId"]
        val taskId = remoteMessage.data["taskId"]

        val intent = createIntentBasedOnType(type, groupId, taskId)
        showNotification(title, message, intent)
    }

    private fun createIntentBasedOnType(type: String, groupId: String?, taskId: String?): Intent {
        return when(type) {
            "task_chat" -> Intent(this, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("groupId", groupId)
                putExtra("taskId", taskId)
            }
            "task" -> Intent(this, TaskInfoActivity::class.java).apply {
                putExtra("groupId", groupId)
                putExtra("taskId", taskId)
            }
            "group" -> Intent(this, GroupInfoActivity::class.java).apply {
                putExtra("groupId", groupId)
            }
            else -> Intent(this, SplashActivity::class.java)
        }
    }

    private fun showNotification(title: String, message: String, intent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, TaskChatApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}