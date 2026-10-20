package com.sanji.bomberpro.data

data class FirebaseNode(
    val id: String,
    val url: String,
    val label: String,           // "Firebase 1" masked for users
    val enabled: Boolean = true,
)