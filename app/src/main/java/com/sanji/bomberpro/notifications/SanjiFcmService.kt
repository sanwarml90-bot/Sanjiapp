package com.sanji.bomberpro.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.MainActivity
import com.sanji.bomberpro.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SanjiFcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: ${token.take(20)}…")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "No signed-in user — token not persisted")
            return
        }
        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection(FirebaseConfig.COL_USERS)
                    .document(uid)
                    .update(
                        mapOf(
                            "fcmToken" to token,
                            "fcmTokenUpdatedAt" to System.currentTimeMillis() / 1000L,
                        )
                    ).await()
                Log.d(TAG, "FCM token saved for $uid")
            } catch (e: Exception) {
                Log.w(TAG, "FCM token save failed", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM received from ${message.from}")

        val data = message.data
        val notif = message.notification

        val title = data["title"]
            ?: notif?.title
            ?: "Sanji Bomber Pro"
        val body = data["body"]
            ?: notif?.body
            ?: ""
        val type = data["type"] ?: "default"

        if (body.isBlank()) {
            Log.d(TAG, "Empty FCM body — skipping notification")
            return
        }

        val channelId = channelForType(type)
        ensureChannel(channelId)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("fcm_type", type)
            data["route"]?.let { putExtra("fcm_route", it) }
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            @Suppress("DEPRECATION")
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % 100000).toInt(),
            launchIntent,
            pendingFlags,
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.color = 0xFF00E5FF.toInt()
        }

        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Post notification failed", e)
        }
    }

    private fun channelForType(type: String): String = when (type) {
        "payment" -> FirebaseConfig.CHANNEL_PAYMENT
        "otp" -> FirebaseConfig.CHANNEL_OTP
        "blast" -> FirebaseConfig.CHANNEL_BLAST
        "giveaway" -> FirebaseConfig.CHANNEL_GIVEAWAY
        "admin" -> FirebaseConfig.CHANNEL_ADMIN
        else -> FirebaseConfig.CHANNEL_DEFAULT
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) != null) return
        val importance = when (channelId) {
            FirebaseConfig.CHANNEL_PAYMENT,
            FirebaseConfig.CHANNEL_OTP,
            FirebaseConfig.CHANNEL_ADMIN,
            FirebaseConfig.CHANNEL_GIVEAWAY,
            -> NotificationManager.IMPORTANCE_HIGH
            FirebaseConfig.CHANNEL_BLAST -> NotificationManager.IMPORTANCE_LOW
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        nm.createNotificationChannel(
            NotificationChannel(channelId, channelId, importance)
        )
    }

    companion object {
        private const val TAG = "SanjiFcmService"
    }
}