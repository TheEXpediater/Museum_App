package com.example.museumapp.model3d

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class Sha256Test {
    @Test
    fun matchesFipsKnownVectorForAbc() {
        // Standard FIPS 180-2 SHA-256 example vector for the input "abc".
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".toByteArray(StandardCharsets.UTF_8))
        )
    }

    @Test
    fun matchesJdkMessageDigestForArbitraryInput() {
        val bytes = "The quick brown fox jumps over the lazy dog".toByteArray(StandardCharsets.UTF_8)
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

        assertEquals(expected, Sha256.of(bytes))
    }

    @Test
    fun fileVariantMatchesByteArrayVariantForSameContent() {
        val bytes = "streamed content used to verify the file-based SHA-256 path".toByteArray(StandardCharsets.UTF_8)
        val file = File.createTempFile("sha256-test", ".bin")
        try {
            file.writeBytes(bytes)
            assertEquals(Sha256.of(bytes), Sha256.of(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun producesLowercase64CharacterHexDigest() {
        val digest = Sha256.of("any content".toByteArray(StandardCharsets.UTF_8))
        assertEquals(64, digest.length)
        assertEquals(digest, digest.lowercase())
        assertEquals(true, digest.all { it in "0123456789abcdef" })
    }
}
