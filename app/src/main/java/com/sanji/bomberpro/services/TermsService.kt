package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class TermsResult(
    val version: Int = 1,
    val text: String = "",
    val required: Boolean = true,
)

class TermsService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Load terms content + current version.
     * Source: settings/global doc fields `termsText`, `termsVersion`, `termsRequired`.
     * Falls back to defaults if not configured.
     */
    suspend fun loadTerms(): TermsResult = withContext(Dispatchers.IO) {
        try {
            val doc = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()
            if (doc.exists()) {
                TermsResult(
                    version = (doc.getLong("termsVersion") ?: FirebaseConfig.DEFAULT_TERMS_VERSION.toLong()).toInt(),
                    text = doc.getString("termsText") ?: DEFAULT_TERMS_TEXT,
                    required = doc.getBoolean("termsRequired") ?: true,
                )
            } else {
                TermsResult(
                    version = FirebaseConfig.DEFAULT_TERMS_VERSION,
                    text = DEFAULT_TERMS_TEXT,
                    required = true,
                )
            }
        } catch (_: Exception) {
            TermsResult(
                version = FirebaseConfig.DEFAULT_TERMS_VERSION,
                text = DEFAULT_TERMS_TEXT,
                required = true,
            )
        }
    }

    companion object {
        val DEFAULT_TERMS_TEXT = """
            TERMS & CONDITIONS
            Last updated: 2026

            1. ACCEPTANCE
            By using this app you confirm that you are at least 18 years of age,
            or have the legal capacity to accept these terms in your jurisdiction.

            2. PURPOSE
            This app provides communication tooling. You are solely responsible
            for how you use it, including any content you send, the numbers you
            target, and your compliance with local laws.

            3. RISK
            Use of this app is entirely at your own risk. The operator makes no
            warranties about availability, accuracy, or fitness for any purpose.
            You accept full legal and financial responsibility for your actions.

            4. PROHIBITED USE
            You may not use the app to harass, defraud, impersonate, or send
            unsolicited bulk messages to recipients who have not consented.

            5. DATA
            The app stores your account ID, usage counters, and transactional
            history to operate the service. This data is not sold.

            6. NO WARRANTY
            The service is provided "as is" without warranty of any kind.

            7. CHANGES
            These terms may change. Continued use after a version bump means
            you accept the updated terms.

            By tapping "I AGREE", you confirm you have read, understood,
            and accepted these terms. You accept all responsibility and risk.
        """.trimIndent()
    }
}
