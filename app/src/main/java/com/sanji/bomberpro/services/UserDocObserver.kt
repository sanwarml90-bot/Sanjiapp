package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object UserDocObserver {

    fun observeUserDoc(uid: String): Flow<Map<String, Any?>> = callbackFlow {
        val ref = FirebaseFirestore.getInstance()
            .collection(FirebaseConfig.COL_USERS)
            .document(uid)
        val listener = ref.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                trySend(snap.data ?: emptyMap())
            }
        }
        awaitClose { listener.remove() }
    }
}