package com.wonderfood.app.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest

object GoogleDriveAuthorization {
    fun accessTokenFromResultIntent(context: Context, data: Intent?): String =
        throw IllegalStateException(unavailableMessage)

    fun requestAccess(
        activity: Activity,
        accountEmail: String,
        onResolution: (IntentSenderRequest) -> Unit,
        onAccessToken: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        onFailure(IllegalStateException(unavailableMessage))
    }

    private const val unavailableMessage =
        "Google Drive backup is not included in the FOSS build. Use encrypted local export instead."
}
