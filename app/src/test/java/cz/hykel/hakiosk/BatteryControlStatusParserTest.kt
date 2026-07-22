package cz.hykel.hakiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryControlStatusParserTest {
    @Test fun parsesFreshValidStatus() {
        val status = BatteryControlStatusParser.parse(
            "version=1\nupdated_epoch_seconds=1000\nslate_mode=1\n",
            nowEpochSeconds = 1_060L,
        )

        assertEquals(1, status?.slateMode)
    }

    @Test fun rejectsStaleStatus() {
        val status = BatteryControlStatusParser.parse(
            "version=1\nupdated_epoch_seconds=1000\nslate_mode=0\n",
            nowEpochSeconds = 1_181L,
        )

        assertNull(status)
    }

    @Test fun rejectsInvalidOrFutureStatus() {
        assertNull(BatteryControlStatusParser.parse("version=1\nupdated_epoch_seconds=1000\nslate_mode=2\n", 1_000L))
        assertNull(BatteryControlStatusParser.parse("version=1\nupdated_epoch_seconds=1001\nslate_mode=0\n", 1_000L))
        assertNull(BatteryControlStatusParser.parse("version=2\nupdated_epoch_seconds=1000\nslate_mode=0\n", 1_000L))
    }
}
