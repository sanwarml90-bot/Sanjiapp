package com.sanji.bomberpro.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SanjiFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM received from ${message.from}")
        // Session 3+: post local notification for blast complete/payment update
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: ${token.take(20)}…")
        // Session 3+: save token to Firestore users/{uid}.fcmToken
    }

    companion object {
        private const val TAG = "SanjiFcmService"
    }
}