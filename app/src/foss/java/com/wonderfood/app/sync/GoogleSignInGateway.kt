package com.wonderfood.app.sync

import android.content.Context

class GoogleSignInGateway(private val context: Context) {
    suspend fun signIn(webClientIdOverride: String = ""): GoogleAccountProfile =
        throw IllegalStateException(
            "Google sign-in is not included in the FOSS build. Use encrypted local export instead.",
        )
}
