package com.sanji.bomberpro.services

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val name: String = "",
    val phones: List<String> = emptyList(),
)

class ContactsService(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Loads all contacts with at least one phone number.
     * Sorted by display name. Deduplicates phone numbers.
     */
    suspend fun loadContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val out = mutableListOf<DeviceContact>()
        try {
            val resolver: ContentResolver = context.contentResolver

            // map of contactId -> (name, list of phones)
            val contactNames = mutableMapOf<String, String>()
            val contactPhones = mutableMapOf<String, MutableSet<String>>()

            // 1. get names
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ),
                "${ContactsContract.Contacts.HAS_PHONE_NUMBER} > 0",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (cursor.moveToNext()) {
                    val cid = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    contactNames[cid] = name
                }
            }

            // 2. get phone numbers
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val cidIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val cid = cursor.getString(cidIdx) ?: continue
                    val rawNum = cursor.getString(numIdx) ?: continue
                    val clean = normalizeNumber(rawNum)
                    if (clean.length < 7) continue
                    contactPhones.getOrPut(cid) { mutableSetOf() }.add(clean)
                }
            }

            // 3. combine
            for ((cid, name) in contactNames) {
                val phones = contactPhones[cid]?.toList() ?: continue
                if (phones.isEmpty()) continue
                out.add(DeviceContact(name = name, phones = phones.sorted()))
            }

            out.sortBy { it.name.lowercase() }
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        out
    }

    /**
     * Normalize a phone number:
     *  - strip spaces, dashes, parens
     *  - preserve leading +
     *  - default: if starts with 0 and length 10-11, strip leading 0
     *  - if 10 digits no +, assume +91 if looks Indian? no — leave as-is. caller can prepend.
     */
    private fun normalizeNumber(raw: String): String {
        var s = raw.trim().replace(" ", "").replace("-", "")
            .replace("(", "").replace(")", "").replace(".", "")
        if (s.startsWith("+")) {
            // strip everything except leading + and digits
            s = "+" + s.substring(1).filter { it.isDigit() }
        } else {
            s = s.filter { it.isDigit() }
            if (s.length == 11 && s.startsWith("0")) s = s.substring(1)
        }
        return s
    }
}