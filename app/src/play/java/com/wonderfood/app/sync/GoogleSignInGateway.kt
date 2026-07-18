package com.wonderfood.app.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.wonderfood.app.R

class GoogleSignInGateway(private val context: Context) {
    suspend fun signIn(webClientIdOverride: String = ""): GoogleAccountProfile {
        val clientId = configuredWebClientId(webClientIdOverride)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = try {
            CredentialManager.create(context).getCredential(
                context = context,
                request = request,
            )
        } catch (error: NoCredentialException) {
            throw IllegalStateException("No Google account credential was available.", error)
        } catch (error: GetCredentialException) {
            throw IllegalStateException(error.message ?: "Google sign-in was cancelled or unavailable.", error)
        }
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Google sign-in returned an unsupported credential.")
        }
        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (error: GoogleIdTokenParsingException) {
            throw IllegalStateException("Google sign-in response could not be read.", error)
        }
        return GoogleAccountProfile(
            email = googleCredential.id,
            displayName = googleCredential.displayName.orEmpty(),
        )
    }

    private fun configuredWebClientId(webClientIdOverride: String): String {
        val value = webClientIdOverride.trim().ifBlank {
            context.getString(R.string.google_web_client_id).trim()
        }
        require(value.isNotBlank() && !value.startsWith("TODO_")) {
            "Paste your Google Web OAuth client ID in Settings → Backup & restore before using Google backup."
        }
        return value
    }
}
