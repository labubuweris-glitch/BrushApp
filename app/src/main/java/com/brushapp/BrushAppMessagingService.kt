package com.brushapp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest

class BrushAppMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val tokenId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("fcm_tokens").document(tokenId)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
    }
}
