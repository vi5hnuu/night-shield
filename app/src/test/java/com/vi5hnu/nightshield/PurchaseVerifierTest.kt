package com.vi5hnu.nightshield

import com.vi5hnu.nightshield.billing.PurchaseVerifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PurchaseVerifierTest {

    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
    private val json = """{"packageName":"com.vi5hnu.nightshield","productId":"night_shield_pro","purchaseState":0}"""

    private fun sign(data: String): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("SHA1withRSA").run {
            initSign(pair.private); update(data.toByteArray()); sign()
        }
    )

    @Test fun validSignature_passes() {
        assertTrue(PurchaseVerifier.verifySignature(json, sign(json), pair.public))
    }

    @Test fun tamperedPayload_fails() {
        val sig = sign(json)
        assertFalse(PurchaseVerifier.verifySignature(json.replace("0}", "1}"), sig, pair.public))
    }

    @Test fun otherKey_fails() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        assertFalse(PurchaseVerifier.verifySignature(json, sign(json), other.public))
    }

    @Test fun blankOrGarbageSignature_fails() {
        assertFalse(PurchaseVerifier.verifySignature(json, "", pair.public))
        assertFalse(PurchaseVerifier.verifySignature(json, "not-base64!!", pair.public))
    }
}
