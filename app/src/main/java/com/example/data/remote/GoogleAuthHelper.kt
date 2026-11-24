package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

object GoogleAuthHelper {

    fun currentUserOrNull(): FirebaseUser? =
        runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    fun authStateFlow(auth: FirebaseAuth = FirebaseAuth.getInstance()): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Attempts silent/one-tap sign-in against already authorized Google accounts on the device.
     * Never presents a blocking modal if no previously authorized account exists.
     */
    suspend fun attemptAutoSignIn(
        activityContext: Context,
        auth: FirebaseAuth = FirebaseAuth.getInstance()
    ): GoogleSignInOutcome? {
        auth.currentUser?.let { return GoogleSignInOutcome.Success(it) }
        val credentialManager = runCatching { CredentialManager.create(activityContext) }.getOrNull()
            ?: return null
        val serverClientId = runCatching { activityContext.getString(R.string.default_web_client_id) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val attempt = trySilentOrAuthorizedGoogleId(credentialManager, activityContext, serverClientId)
            ?: return null
        return finishFirebaseCredentialSignIn(attempt, auth)
    }

    /**
     * Explicit user-initiated Google Sign-In flow via GetSignInWithGoogleOption.
     */
    suspend fun signInInteractive(
        activityContext: Context,
        auth: FirebaseAuth = FirebaseAuth.getInstance()
    ): GoogleSignInOutcome {
        val credentialManager = CredentialManager.create(activityContext)
        val serverClientId = activityContext.getString(R.string.default_web_client_id)
        val attempt = tryExplicitSignInWithGoogle(credentialManager, activityContext, serverClientId)
        return finishFirebaseCredentialSignIn(attempt, auth)
    }

    suspend fun signInWithGoogle(
        activityContext: Context,
        auth: FirebaseAuth = FirebaseAuth.getInstance()
    ): GoogleSignInOutcome {
        val credentialManager = CredentialManager.create(activityContext)
        val serverClientId = activityContext.getString(R.string.default_web_client_id)

        // Single-option requests: check authorized Google accounts first, then explicit Google Sign-In picker
        val idTokenResult = trySilentOrAuthorizedGoogleId(credentialManager, activityContext, serverClientId)
            ?: tryExplicitSignInWithGoogle(credentialManager, activityContext, serverClientId)

        return finishFirebaseCredentialSignIn(idTokenResult, auth)
    }

    suspend fun signOut(
        context: Context,
        auth: FirebaseAuth = FirebaseAuth.getInstance(),
        onBeforeFirebaseSignOut: suspend () -> Unit = {}
    ) = withContext(NonCancellable) {
        runCatching { onBeforeFirebaseSignOut() }
        runCatching { auth.signOut() }
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private suspend fun finishFirebaseCredentialSignIn(
        tokenAttempt: TokenAttempt,
        auth: FirebaseAuth
    ): GoogleSignInOutcome {
        return when (tokenAttempt) {
            is TokenAttempt.Token -> {
                try {
                    val firebaseCred = GoogleAuthProvider.getCredential(tokenAttempt.idToken, null)
                    val authResult = auth.signInWithCredential(firebaseCred).await()
                    val user = authResult.user
                    if (user != null) {
                        GoogleSignInOutcome.Success(user)
                    } else {
                        GoogleSignInOutcome.Error("Sign-in succeeded, but user profile was unavailable.")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    GoogleSignInOutcome.Error(e.localizedMessage ?: "Firebase authentication failed.")
                }
            }
            is TokenAttempt.Cancelled -> GoogleSignInOutcome.Cancelled
            is TokenAttempt.Error -> GoogleSignInOutcome.Error(tokenAttempt.message)
        }
    }

    private sealed interface TokenAttempt {
        data class Token(val idToken: String) : TokenAttempt
        data object Cancelled : TokenAttempt
        data class Error(val message: String) : TokenAttempt
    }

    private suspend fun trySilentOrAuthorizedGoogleId(
        credentialManager: CredentialManager,
        activityContext: Context,
        serverClientId: String
    ): TokenAttempt? {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(activityContext, request)
            extractGoogleToken(response.credential)
        } catch (_: NoCredentialException) {
            null
        } catch (e: GetCredentialCancellationException) {
            Log.w("GoogleAuthHelper", "Authorized Google ID flow cancelled or dismissed", e)
            TokenAttempt.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryExplicitSignInWithGoogle(
        credentialManager: CredentialManager,
        activityContext: Context,
        serverClientId: String
    ): TokenAttempt {
        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val response = credentialManager.getCredential(activityContext, request)
            extractGoogleToken(response.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w("GoogleAuthHelper", "Google Sign-In sheet cancelled by user or play services config", e)
            TokenAttempt.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TokenAttempt.Error(e.localizedMessage ?: "Google Sign-In unavailable on this device.")
        }
    }

    private fun extractGoogleToken(credential: androidx.credentials.Credential): TokenAttempt {
        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val tokenCred = GoogleIdTokenCredential.createFrom(credential.data)
            TokenAttempt.Token(tokenCred.idToken)
        } else {
            TokenAttempt.Error("Unsupported credential type received.")
        }
    }
}
