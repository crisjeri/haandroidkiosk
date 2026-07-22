package cz.hykel.hakiosk

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test fun growsExponentiallyAndStopsAtMaximum() {
        val policy = RetryPolicy(1_000L, 8_000L)
        assertEquals(1_000L, policy.delayMillis(0))
        assertEquals(2_000L, policy.delayMillis(1))
        assertEquals(4_000L, policy.delayMillis(2))
        assertEquals(8_000L, policy.delayMillis(3))
        assertEquals(8_000L, policy.delayMillis(20))
    }
}
