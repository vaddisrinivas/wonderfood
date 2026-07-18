package com.wonderfood.app.sync

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

object GoogleDriveAuthorization {
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    fun request(accountEmail: String = ""): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        if (accountEmail.isNotBlank()) {
            builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        }
        return builder.build()
    }

    fun accessToken(result: AuthorizationResult): String =
        result.accessToken?.takeIf { it.isNotBlank() }
            ?: error("Google Drive did not return an access token. Try connecting again.")

    fun accessTokenFromResultIntent(context: Context, data: Intent?): String {
        val result = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
        return accessToken(result)
    }

    fun requestAccess(
        activity: Activity,
        accountEmail: String,
        onResolution: (IntentSenderRequest) -> Unit,
        onAccessToken: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        Identity.getAuthorizationClient(activity)
            .authorize(request(accountEmail))
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent ?: run {
                        onFailure(IllegalStateException("Google Drive permission prompt was unavailable."))
                        return@addOnSuccessListener
                    }
                    onResolution(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                    runCatching { accessToken(authResult) }
                        .onSuccess(onAccessToken)
                        .onFailure(onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
}
