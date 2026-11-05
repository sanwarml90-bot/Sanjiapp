package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class MessageTemplate(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
)

/**
 * Message templates — stored in `users/{uid}/templates` subcollection.
 *
 * IMPORTANT: If Firestore rules do not allow writing to subcollections
 * under `users/{uid}`, saves will fail. Rules must include:
 *
 *   match /users/{uid} {
 *     allow read, write: if request.auth.uid == uid;
 *     match /templates/{tid} {
 *       allow read, write: if request.auth.uid == uid;
 *     }
 *   }
 *
 * Or a recursive wildcard:
 *
 *   match /users/{uid}/{document=**} {
 *     allow read, write: if request.auth.uid == uid;
 *   }
 *
 * If saving fails with permission-denied, that's the rules issue.
 *
 * All operations now throw / return errors so UI can display the real
 * cause instead of silently returning empty.
 */
class TemplateService {

    private val db = FirebaseFirestore.getInstance()

    private fun col(uid: String) = db.collection(FirebaseConfig.COL_USERS)
        .document(uid)
        .collection("templates")

    /**
     * Returns list of templates. Throws on error so caller shows message.
     */
    suspend fun listTemplates(uid: String): List<MessageTemplate> = withContext(Dispatchers.IO) {
        val snap = col(uid).orderBy("createdAt", Query.Direction.DESCENDING).get().await()
        snap.documents.map { d ->
            MessageTemplate(
                id = d.id,
                uid = uid,
                name = d.getString("name") ?: "Template",
                text = d.getString("text") ?: "",
                createdAt = d.getLong("createdAt") ?: 0L,
            )
        }
    }

    suspend fun saveTemplate(uid: String, name: String, text: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanName = name.trim().take(40)
                val cleanText = text.trim().take(1000)
                if (cleanName.isBlank()) return@withContext Result.failure(Exception("Name required"))
                if (cleanText.isBlank()) return@withContext Result.failure(Exception("Text required"))

                val ref = col(uid).document()
                ref.set(
                    mapOf(
                        "name" to cleanName,
                        "text" to cleanText,
                        "createdAt" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
                Result.success(ref.id)
            } catch (e: Exception) {
                Log.e(TAG, "saveTemplate failed", e)
                Result.failure(Exception(e.message ?: "Save failed"))
            }
        }

    suspend fun updateTemplate(uid: String, id: String, name: String, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                col(uid).document(id).update(
                    "name", name.trim().take(40),
                    "text", text.trim().take(1000),
                ).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "updateTemplate failed", e)
                Result.failure(Exception(e.message ?: "Update failed"))
            }
        }

    suspend fun deleteTemplate(uid: String, id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            col(uid).document(id).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteTemplate failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "TemplateService"
    }
}