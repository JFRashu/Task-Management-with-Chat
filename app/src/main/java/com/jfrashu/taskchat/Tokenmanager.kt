package com.jfrashu.taskchat

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.jfrashu.taskchat.dataclasses.Token
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val messaging = FirebaseMessaging.getInstance()

    suspend fun initializeToken() {
        try {
            val token = withContext(Dispatchers.IO) {
                messaging.token.await()
            }
            saveTokenToFirestore(token)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing token", e)
        }
    }

    suspend fun saveTokenToFirestore(newToken: String) {
        try {
            val currentUser = auth.currentUser ?: return
            val tokenData = Token(
                userId = currentUser.uid,
                token = newToken,
                lastUpdate = System.currentTimeMillis()
            )

            withContext(Dispatchers.IO) {
                db.collection(TOKENS_COLLECTION)
                    .document(currentUser.uid)
                    .set(tokenData)
                    .await()

                Log.d(TAG, "Token saved successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving token", e)
        }
    }

    suspend fun deleteToken() {
        try {
            val currentUser = auth.currentUser ?: return

            withContext(Dispatchers.IO) {
                db.collection(TOKENS_COLLECTION)
                    .document(currentUser.uid)
                    .delete()
                    .await()

                // Also delete the token from Firebase Messaging
                messaging.deleteToken().await()
                Log.d(TAG, "Token deleted successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting token", e)
        }
    }

    suspend fun getTokenForUser(userId: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                val document = db.collection(TOKENS_COLLECTION)
                    .document(userId)
                    .get()
                    .await()

                document?.getString("token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting token for user: $userId", e)
            null
        }
    }

    suspend fun getTokensForUsers(userIds: List<String>): Map<String, String> {
        return try {
            withContext(Dispatchers.IO) {
                val documents = db.collection(TOKENS_COLLECTION)
                    .whereIn("userId", userIds)
                    .get()
                    .await()

                documents.documents.associate { doc ->
                    doc.getString("userId")!! to (doc.getString("token") ?: "")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting tokens for users", e)
            emptyMap()
        }
    }

    companion object {
        private const val TAG = "TokenManager"
        private const val TOKENS_COLLECTION = "tokens"
    }
}