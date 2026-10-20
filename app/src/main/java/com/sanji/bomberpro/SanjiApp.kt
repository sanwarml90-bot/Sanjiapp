package com.sanji.bomberpro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.messaging.FirebaseMessaging

class SanjiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        try {
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            firestore.firestoreSettings = settings
        } catch (_: Exception) {
            // Firestore may already be initialized; safe to ignore.
        }

        createNotificationChannels()

        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
        } catch (_: Exception) {
            // No play services — silent
        }

        installCrashHandler()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DEFAULT,
                    "General",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "General notifications" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BLAST,
                    "Blast progress",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "SMS blast live progress"
                    setShowBadge(false)
                }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PAYMENT,
                    "Payment updates",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Payment approved / rejected" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OTP,
                    "OTP alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "New OTP found by auto-detect" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ADMIN,
                    "Admin",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Admin notifications" }
            )
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashRef = FirebaseFirestore.getInstance()
                    .collection(FirebaseConfig.COL_CRASH_LOGS)
                    .document()

                crashRef.set(
                    mapOf(
                        "thread" to thread.name,
                        "message" to (throwable.message ?: "unknown"),
                        "stack" to throwable.stackTraceToString().take(4000),
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "android" to Build.VERSION.SDK_INT,
                        "ts" to System.currentTimeMillis() / 1000L,
                    )
                )
            } catch (_: Throwable) {
                // Never crash the crash handler
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CHANNEL_DEFAULT = "sanji_default_channel"
        const val CHANNEL_BLAST = "sanji_blast_channel"
        const val CHANNEL_PAYMENT = "sanji_payment_channel"
        const val CHANNEL_OTP = "sanji_otp_channel"
        const val CHANNEL_ADMIN = "sanji_admin_channel"
    }
}