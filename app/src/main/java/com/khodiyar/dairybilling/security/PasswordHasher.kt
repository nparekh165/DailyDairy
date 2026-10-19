package com.khodiyar.dairybilling.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 verifier for PINs and passwords. Only the derived key and the random
 * per-user salt are stored; the secret itself is never persisted. Comparison is constant-time.
 */
object PasswordHasher {
    const val DEFAULT_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    data class Hashed(val hash: String, val salt: String, val iterations: Int)

    fun hash(secret: String, iterations: Int = DEFAULT_ITERATIONS): Hashed {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(secret, salt, iterations)
        return Hashed(b64(derived), b64(salt), iterations)
    }

    fun verify(secret: String, hash: String, salt: String, iterations: Int): Boolean {
        val expected = try { Base64.getDecoder().decode(hash) } catch (e: IllegalArgumentException) { return false }
        val saltBytes = try { Base64.getDecoder().decode(salt) } catch (e: IllegalArgumentException) { return false }
        val actual = derive(secret, saltBytes, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** PIN/password policy: at least 6 characters, not all the same character, not a simple sequence. */
    fun isAcceptable(secret: String): Boolean {
        if (secret.length < 6) return false
        if (secret.all { it == secret[0] }) return false
        val ascending = "0123456789012345678901234567890"
        val descending = ascending.reversed()
        if (ascending.contains(secret) || descending.contains(secret)) return false
        return true
    }
}
