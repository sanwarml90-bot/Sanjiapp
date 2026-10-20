package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.BuildConfig
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UpdateInfo(
    val latestVersion: String,
    val minVersion: String,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean,
    val currentVersion: String,
) {
    fun isForceUpdate(): Boolean = compareVersion(currentVersion, minVersion) < 0 || forceUpdate
    fun isNewerAvailable(): Boolean = compareVersion(currentVersion, latestVersion) < 0
}

class ForceUpdateService {

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val doc = FirebaseFirestore.getInstance()
                .collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()

            if (!doc.exists()) return@withContext null

            UpdateInfo(
                latestVersion = doc.getString("latestVersion") ?: BuildConfig.VERSION_NAME,
                minVersion = doc.getString("minVersion") ?: "0.0.0",
                apkUrl = doc.getString("apkUrl") ?: "",
                changelog = doc.getString("changelog") ?: "",
                forceUpdate = doc.getBoolean("forceUpdate") ?: false,
                currentVersion = BuildConfig.VERSION_NAME,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}