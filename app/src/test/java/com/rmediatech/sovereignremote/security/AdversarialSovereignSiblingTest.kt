package com.rmediatech.sovereignremote.security

import com.rmediatech.sovereign.security.SovereignSibling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Universal Sovereign Sibling Adversarial Test Battery (SOVEREIGN-SIBLING-01) in Argus
 */
class AdversarialSovereignSiblingTest {

    @Test
    fun testSiblingProofLifecycleAndVerification() {
        val nowMs = 1700000000000L
        val challenge = SovereignSibling.issueChallenge(nowMs, 555L)
        val proof = SovereignSibling.generateProof("argus", challenge.nonce, nowMs)
        assertEquals("argus", proof.callerName)
        assertEquals(64, proof.signature.length)

        val result = SovereignSibling.verifyProof(proof, nowMs + 100L)
        assertTrue(result is SovereignSibling.SiblingResult.Success)
    }

    @Test
    fun testStaleTimestampReplayRejection() {
        val nowMs = 1700000000000L
        val staleProof = SovereignSibling.generateProof("argus", "nonce-1", nowMs - 60001L)
        val result = SovereignSibling.verifyProof(staleProof, nowMs)
        assertTrue(result is SovereignSibling.SiblingResult.Failure)
    }

    @Test
    fun testTamperedSignatureRejection() {
        val nowMs = 1700000000000L
        val proof = SovereignSibling.generateProof("argus", "nonce-2", nowMs)
        val tampered = proof.copy(signature = "ff".repeat(32))
        val result = SovereignSibling.verifyProof(tampered, nowMs)
        assertTrue(result is SovereignSibling.SiblingResult.Failure)
    }

    @Test
    fun testHeaderGenerationWireParity() {
        val nowMs = 1700000000000L
        val proof = SovereignSibling.generateProof("argus", "wire-nonce", nowMs)
        val headers = SovereignSibling.buildHeaders(proof)
        assertEquals("argus", headers[SovereignSibling.HEADER_SIBLING_NAME])
        assertEquals("wire-nonce", headers[SovereignSibling.HEADER_SIBLING_NONCE])
    }
}
