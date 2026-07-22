package cz.hykel.hakiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test fun acceptsHttpsUrl() {
        val result = UrlValidator.validate("https://ha.local:8123/dashboard", false)
        assertTrue(result.isValid)
        assertEquals("https://ha.local:8123/dashboard", result.normalizedUrl)
    }

    @Test fun rejectsHttpUnlessExplicitlyAllowed() {
        assertFalse(UrlValidator.validate("http://192.168.1.5:8123", false).isValid)
        assertTrue(UrlValidator.validate("http://192.168.1.5:8123", true).isValid)
    }

    @Test fun rejectsCredentialsAndUnsupportedSchemes() {
        assertFalse(UrlValidator.validate("https://user:pass@ha.local", false).isValid)
        assertFalse(UrlValidator.validate("file:///tmp/index.html", true).isValid)
    }
}
