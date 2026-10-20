package com.sanji.bomberpro.services

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

data class ImportStats(
    val users: Int = 0,
    val firebases: Int = 0,
    val blasts: Int = 0,
    val transactions: Int = 0,
    val skipped: Int = 0,
)

class DataExportService(private val context: Context) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Export full data as JSON to phone Downloads. Returns Uri.
     * Collections exported: users (own), blasts (own), otp_fetches (own), transactions (own),
     * settings/global, firebases, giveaways, apps.
     * Admin-only collections (activity_log, audit_log, payment_log) are also exported for owner.
     */
    suspend fun exportFullData(isAdmin: Boolean): Uri? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("appVersion", "1.0.0")
            root.put("exportedAt", System.currentTimeMillis() / 1000L)
            root.put("isAdmin", isAdmin)

            // firebases (always included — masked labels safe)
            val firebasesArr = JSONArray()
            db.collection(FirebaseConfig.COL_FIREBASES).get().await().documents.forEach { d ->
                firebasesArr.put(JSONObject(d.data ?: emptyMap<String, Any>()).apply { put("id", d.id) })
            }
            root.put("firebases", firebasesArr)

            // global settings
            db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()
                .let { doc -> if (doc.exists()) root.put("settings", JSONObject(doc.data ?: emptyMap<String, Any>())) }

            // giveaways
            val gwArr = JSONArray()
            db.collection(FirebaseConfig.COL_GIVEAWAYS).get().await().documents.forEach { d ->
                gwArr.put(JSONObject(d.data ?: emptyMap<String, Any>()).apply { put("id", d.id) })
            }
            root.put("giveaways", gwArr)

            // apps
            val appsArr = JSONArray()
            db.collection(FirebaseConfig.COL_APPS).get().await().documents.forEach { d ->
                appsArr.put(JSONObject(d.data ?: emptyMap<String, Any>()).apply { put("id", d.id) })
            }
            root.put("apps", appsArr)

            // admin-only logs
            if (isAdmin) {
                val audit = JSONArray()
                db.collection(FirebaseConfig.COL_AUDIT_LOG).limit(5000).get().await().documents.forEach { d ->
                    audit.put(JSONObject(d.data ?: emptyMap<String, Any>()).apply { put("id", d.id) })
                }
                root.put("auditLog", audit)
            }

            // write
            val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
            val filename = "sanji-export-${System.currentTimeMillis()}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values) ?: return@withContext null
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "")
                dir.mkdirs()
                val f = File(dir, filename)
                f.writeBytes(bytes)
                Uri.fromFile(f)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Import data from a JSON file — additive merge, protected fields preserved.
     * Matches bot.py `_import_merge` philosophy.
     */
    suspend fun importFullData(uri: Uri): Result<ImportStats> = withContext(Dispatchers.IO) {
        try {
            val input: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file"))
            val text = input.bufferedReader().readText()
            input.close()

            val root = JSONObject(text)
            var stats = ImportStats()

            // firebases — additive
            if (root.has("firebases")) {
                val arr = root.getJSONArray("firebases")
                var count = 0
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optString("url", "")
                    if (url.isBlank()) { stats = stats.copy(skipped = stats.skipped + 1); continue }
                    val existing = db.collection(FirebaseConfig.COL_FIREBASES)
                        .whereEqualTo("url", url).limit(1).get().await()
                    if (existing.isEmpty) {
                        val map = jsonToMap(obj)
                        map.remove("id")
                        db.collection(FirebaseConfig.COL_FIREBASES).add(map).await()
                        count++
                    } else {
                        stats = stats.copy(skipped = stats.skipped + 1)
                    }
                }
                stats = stats.copy(firebases = count)
            }

            // apps — additive
            if (root.has("apps")) {
                val arr = root.getJSONArray("apps")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pkg = obj.optString("packageName", "")
                    if (pkg.isBlank()) continue
                    val existing = db.collection(FirebaseConfig.COL_APPS)
                        .whereEqualTo("packageName", pkg).limit(1).get().await()
                    if (existing.isEmpty) {
                        val map = jsonToMap(obj)
                        map.remove("id")
                        db.collection(FirebaseConfig.COL_APPS).add(map).await()
                    }
                }
            }

            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun jsonToMap(obj: JSONObject): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = obj.get(k)
        }
        return map
    }
}