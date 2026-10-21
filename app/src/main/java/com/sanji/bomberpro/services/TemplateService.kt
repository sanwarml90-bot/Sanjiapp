package com.sanji.bomberpro.services

import com.google.firebase.firestore.FieldValue
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

class TemplateService {

    private val db = FirebaseFirestore.getInstance()

    private fun col(uid: String) = db.collection(FirebaseConfig.COL_USERS)
        .document(uid)
        .collection("templates")

    suspend fun listTemplates(uid: String): List<MessageTemplate> = withContext(Dispatchers.IO) {
        try {
            col(uid).orderBy("createdAt", Query.Direction.DESCENDING)
                .get().await().documents.map { d ->
                    MessageTemplate(
                        id = d.id,
                        uid = uid,
                        name = d.getString("name") ?: "Template",
                        text = d.getString("text") ?: "",
                        createdAt = d.getLong("createdAt") ?: 0L,
                    )
                }
        } catch (_: Exception) {
            emptyList()
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
                Result.failure(e)
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
                Result.failure(e)
            }
        }

    suspend fun deleteTemplate(uid: String, id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            col(uid).document(id).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }
}