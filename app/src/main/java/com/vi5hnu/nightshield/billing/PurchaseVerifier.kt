package com.vi5hnu.nightshield.billing

import android.util.Log
import com.vi5hnu.nightshield.BuildConfig
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * On-device check that a purchase really came from Google Play for this app.
 *
 * Play signs every purchase's `originalJson` with the app's private key; the matching public key
 * (Play Console → Monetization setup → Licensing) reaches us as `BuildConfig.PLAY_LICENSE_KEY`
 * from local.properties. A forged purchase injected by a fake-billing app has no valid signature
 * and is rejected.
 *
 * Limits, stated plainly: Google's recommended defence is server-side verification through the
 * Play Developer API, which this backend-less app can't do. A modified APK can patch this check
 * out. It raises the bar against the common one-tap cracking tools; it isn't a guarantee.
 *
 * Release builds can't be produced without the key (see app/build.gradle.kts). Debug builds
 * without it skip verification so local development still works, and log that they did.
 */
object PurchaseVerifier {

    private const val TAG = "PurchaseVerifier"
    private const val KEY_ALGORITHM = "RSA"

    /** Play's documented signature scheme for in-app purchase data. */
    private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

    private val publicKey: PublicKey? by lazy { decodeKey(BuildConfig.PLAY_LICENSE_KEY) }

    /**
     * True when [originalJson] is signed by Play for [expectedPackage].
     * A bad signature, a malformed payload or another app's package all fail closed.
     */
    fun isGenuine(originalJson: String, signature: String, expectedPackage: String): Boolean {
        val key = publicKey
        if (key == null) {
            // Only reachable in debug: the release build refuses to compile without a key.
            Log.w(TAG, "PLAY_LICENSE_KEY not set; purchase signature NOT verified (debug build).")
            return BuildConfig.DEBUG
        }
        if (!verifySignature(originalJson, signature, key)) {
            Log.w(TAG, "Rejected a purchase with an invalid signature.")
            return false
        }
        val pkg = runCatching { JSONObject(originalJson).optString("packageName") }.getOrNull()
        if (pkg != expectedPackage) {
            Log.w(TAG, "Rejected a purchase signed for another package.")
            return false
        }
        return true
    }

    /** Pure signature check, kept separate so it can be unit-tested with a generated key pair. */
    internal fun verifySignature(data: String, signatureBase64: String, key: PublicKey): Boolean {
        if (signatureBase64.isBlank()) return false
        return runCatching {
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(key)
                update(data.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signatureBase64))
            }
        }.getOrDefault(false)
    }

    internal fun decodeKey(base64: String): PublicKey? {
        if (base64.isBlank()) return null
        return runCatching {
            KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64)))
        }.onFailure { Log.e(TAG, "PLAY_LICENSE_KEY is not a valid Base64 RSA public key.", it) }
            .getOrNull()
    }
}
