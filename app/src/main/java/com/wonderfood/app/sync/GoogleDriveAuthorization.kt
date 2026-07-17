package com.wonderfood.app.sync

import android.accounts.Account
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
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

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
}
