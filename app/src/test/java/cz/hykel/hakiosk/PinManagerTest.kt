package cz.hykel.hakiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinManagerTest {
    @Test fun hashesAndVerifiesPinWithSalt() {
        val (salt, hash) = PinHasher.create("7391".toCharArray())
        assertTrue(PinHasher.verify("7391".toCharArray(), salt, hash))
        assertFalse(PinHasher.verify("7392".toCharArray(), salt, hash))
    }

    @Test fun attemptGuardLocksAndUnlocksAfterDelay() {
        var now = 1_000L
        val guard = PinAttemptGuard({ now }, maxAttempts = 3, lockoutMillis = 5_000L)
        repeat(3) { guard.record(false) }
        assertTrue(guard.remainingLockoutMillis() > 0L)
        now += 5_000L
        assertTrue(guard.remainingLockoutMillis() == 0L)
        guard.record(true)
        assertTrue(guard.remainingLockoutMillis() == 0L)
    }
}
