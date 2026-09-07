package com.example.museumapp.model3d

import java.io.File
import java.security.MessageDigest

/** Pure SHA-256 helper, usable on both a byte array and a [File] without loading it fully into memory. */
object Sha256 {
    private const val STREAM_BUFFER_SIZE = 8 * 1024

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            builder.append(HEX_CHARS[(byte.toInt() shr 4) and 0xF])
            builder.append(HEX_CHARS[byte.toInt() and 0xF])
        }
        return builder.toString()
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
