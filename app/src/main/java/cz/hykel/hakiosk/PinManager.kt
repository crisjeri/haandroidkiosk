package cz.hykel.hakiosk

import android.content.SharedPreferences
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    private const val ITERATIONS = 80_000
    private const val KEY_BITS = 256

    fun create(pin: CharArray, random: SecureRandom = SecureRandom()): Pair<String, String> {
        val salt = ByteArray(16).also(random::nextBytes)
        return Base64.getEncoder().encodeToString(salt) to hash(pin, salt)
    }

    fun verify(pin: CharArray, saltBase64: String, expectedBase64: String): Boolean {
        return try {
            val salt = Base64.getDecoder().decode(saltBase64)
            val actual = Base64.getDecoder().decode(hash(pin, salt))
            val expected = Base64.getDecoder().decode(expectedBase64)
            MessageDigest.isEqual(actual, expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun hash(pin: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            Base64.getEncoder().encodeToString(bytes)
        } finally {
            spec.clearPassword()
            pin.fill('\u0000')
        }
    }
}

class PinAttemptGuard(
    private val now: () -> Long,
    private val maxAttempts: Int = 5,
    private val lockoutMillis: Long = 30_000L,
) {
    private var failures = 0
    private var lockedUntil = 0L

    fun remainingLockoutMillis(): Long = (lockedUntil - now()).coerceAtLeast(0L)

    fun record(success: Boolean) {
        if (success) {
            failures = 0
            lockedUntil = 0L
            return
        }
        failures++
        if (failures >= maxAttempts) {
            failures = 0
            lockedUntil = now() + lockoutMillis
        }
    }
}

class PinManager(private val preferences: SharedPreferences) {
    enum class Verification { SUCCESS, FAILURE, LOCKED }

    fun hasPin(): Boolean = preferences.contains(KEY_HASH) && preferences.contains(KEY_SALT)

    fun setPin(pin: String) {
        require(pin.length >= 4)
        val (salt, hash) = PinHasher.create(pin.toCharArray())
        preferences.edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash).apply()
    }

    fun verify(pin: String): Verification {
        val now = System.currentTimeMillis()
        val lockedUntil = preferences.getLong(KEY_LOCKED_UNTIL, 0L)
        if (lockedUntil > now) return Verification.LOCKED
        val salt = preferences.getString(KEY_SALT, null) ?: return Verification.FAILURE
        val hash = preferences.getString(KEY_HASH, null) ?: return Verification.FAILURE
        val valid = PinHasher.verify(pin.toCharArray(), salt, hash)
        if (valid) {
            preferences.edit().putInt(KEY_FAILURES, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
        } else {
            val failures = preferences.getInt(KEY_FAILURES, 0) + 1
            if (failures >= MAX_ATTEMPTS) {
                preferences.edit().putInt(KEY_FAILURES, 0).putLong(KEY_LOCKED_UNTIL, now + LOCKOUT_MILLIS).apply()
            } else {
                preferences.edit().putInt(KEY_FAILURES, failures).apply()
            }
        }
        return if (valid) Verification.SUCCESS else Verification.FAILURE
    }

    fun lockoutSeconds(): Long =
        (preferences.getLong(KEY_LOCKED_UNTIL, 0L) - System.currentTimeMillis() + 999L).coerceAtLeast(0L) / 1_000L

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_FAILURES = "pin_failures"
        const val KEY_LOCKED_UNTIL = "pin_locked_until"
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
    }
}
