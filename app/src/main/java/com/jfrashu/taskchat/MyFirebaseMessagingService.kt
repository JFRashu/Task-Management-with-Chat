package com.jfrashu.taskchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMessaging"
    private val CHANNEL_ID = "TaskChat_Channel"
    private val NOTIFICATION_ID = 100

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token: $token")
        // TODO: Send this token to your server
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleNow(remoteMessage.data)
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            it.body?.let { body ->
                sendNotification(body, it.title ?: "TaskChat")
            }
        }
    }

    private fun handleNow(data: Map<String, String>) {
        // Handle data payload here
        // Example: Update local storage, trigger events, etc.
        when (data["type"]) {
            "chat_message" -> handleChatMessage(data)
            "task_update" -> handleTaskUpdate(data)
            "group_invitation" -> handleGroupInvitation(data)
        }
    }

    private fun handleChatMessage(data: Map<String, String>) {
        // Handle chat message notification
        val title = data["sender_name"] ?: "New Message"
        val message = data["message"] ?: "You have a new message"
        sendNotification(message, title)
    }

    private fun handleTaskUpdate(data: Map<String, String>) {
        // Handle task update notification
        val title = "Task Update"
        val message = data["update_message"] ?: "Task status has been updated"
        sendNotification(message, title)
    }

    private fun handleGroupInvitation(data: Map<String, String>) {
        // Handle group invitation notification
        val title = "Group Invitation"
        val message = "${data["inviter_name"]} invited you to join ${data["group_name"]}"
        sendNotification(message, title)
    }

    private fun sendNotification(messageBody: String, title: String) {
        // Create intent for notification tap action
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TaskChat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Receive notifications about tasks, messages and updates"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement your server token update logic here
        // This is where you would send the token to your backend
    }
}