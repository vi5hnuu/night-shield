package com.vi5hnu.nightshield

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/**
 * The app's legal pages, hosted on the shared Laxmi Solutions legal site (the `legal` repo,
 * content/night-shield/). Keep these URLs in sync with that repo and with the Play Console's
 * privacy policy field.
 */
object LegalLinks {

    private const val BASE_URL = "https://legal.laxmi.solutions/night-shield"

    const val PRIVACY_POLICY_URL = "$BASE_URL/privacy-policy"
    const val TERMS_OF_SERVICE_URL = "$BASE_URL/terms-of-service"

    fun openPrivacyPolicy(context: Context) = open(context, PRIVACY_POLICY_URL)

    fun openTermsOfService(context: Context) = open(context, TERMS_OF_SERVICE_URL)

    /** Opens [url] in the user's browser; explains instead of crashing when none is installed. */
    private fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found to open $url", Toast.LENGTH_LONG).show()
        }
    }
}
