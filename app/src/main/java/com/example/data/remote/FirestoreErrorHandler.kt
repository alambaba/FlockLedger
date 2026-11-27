package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class FirestoreOperationType {
    CREATE,
    GET,
    LIST,
    UPDATE,
    DELETE,
    WRITE,
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreSyncError"

    fun logAndRethrow(
        error: Throwable,
        operationType: FirestoreOperationType,
        path: String?,
        auth: FirebaseAuth,
    ): Nothing {
        if (error is CancellationException) throw error
        logStructuredError(error, operationType, path, auth)
        throw error
    }

    fun logStructuredError(
        error: Throwable,
        operationType: FirestoreOperationType,
        path: String?,
        auth: FirebaseAuth,
    ) {
        val currentUser = auth.currentUser
        val providers = JSONArray().apply {
            currentUser?.providerData?.forEach { info ->
                put(
                    JSONObject().apply {
                        put("providerId", info.providerId)
                        put("displayName", info.displayName ?: "")
                        put("email", info.email ?: "")
                        put("photoUrl", info.photoUrl?.toString() ?: "")
                    }
                )
            }
        }
        val payload = JSONObject().apply {
            put("error", error.localizedMessage ?: error.javaClass.simpleName)
            put("operationType", operationType.name.lowercase())
            put("path", path ?: JSONObject.NULL)
            put(
                "authInfo",
                JSONObject().apply {
                    put("userId", currentUser?.uid ?: JSONObject.NULL)
                    put("email", currentUser?.email ?: JSONObject.NULL)
                    put("emailVerified", currentUser?.isEmailVerified ?: false)
                    put("isAnonymous", currentUser?.isAnonymous ?: false)
                    put("tenantId", currentUser?.tenantId ?: JSONObject.NULL)
                    put("providerInfo", providers)
                }
            )
        }
        Log.e(TAG, "Firestore error: $payload", error)
    }
}
