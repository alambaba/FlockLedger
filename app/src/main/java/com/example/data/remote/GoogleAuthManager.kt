package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GoogleAuthManager(
    private val appContext: Context,
    val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val credentialManager: CredentialManager = CredentialManager.create(appContext)

    val authUserFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun attemptSilentSignIn(activityContext: Context): Boolean {
        if (auth.currentUser != null) return true
        val serverClientId = resolveDefaultWebClientId(activityContext)
        if (serverClientId.isBlank()) return false

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext,
            )
            handleCredential(result.credential) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Silent sign-in cancelled by user")
            false
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized saved Google credential for silent sign-in")
            false
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Silent Credential Manager check failed", e)
            false
        } catch (e: FirebaseAuthException) {
            Log.w(TAG, "Silent Firebase sign-in failed", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error during silent sign-in", e)
            false
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        val serverClientId = resolveDefaultWebClientId(activityContext)
        if (serverClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In is not configured in app/google-services.json yet. Enable Google Sign-In + add your SHA-1 in Firebase Console and re-download google-services.json."
                )
            )
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext,
            )
            val user = handleCredential(result.credential)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Google account sign-in did not return a valid Firebase user"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Result.failure(GoogleSignInCancelledException())
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account available on this device. Add a Google account in device settings.", e))
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google Credential Manager failed", e)
            Result.failure(e)
        } catch (e: FirebaseAuthException) {
            Log.w(TAG, "Firebase Google credential exchange failed", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected Google sign-in error", e)
            Result.failure(e)
        }
    }

    private suspend fun handleCredential(credential: androidx.credentials.Credential): FirebaseUser? {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                authResult.user
            } catch (e: GoogleIdTokenParsingException) {
                Log.w(TAG, "Invalid Google ID token", e)
                null
            }
        }
        return null
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                auth.signOut()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Error during sign out", e)
            }
        }
    }

    private fun resolveDefaultWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }

    companion object {
        private const val TAG = "GoogleAuthManager"
    }
}

class GoogleSignInCancelledException : Exception("Google sign-in cancelled")
