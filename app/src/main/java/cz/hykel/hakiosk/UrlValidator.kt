package cz.hykel.hakiosk

import java.net.URI

object UrlValidator {
    data class Result(val normalizedUrl: String?, val error: String?) {
        val isValid: Boolean get() = normalizedUrl != null
    }

    fun validate(value: String, allowHttp: Boolean): Result {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return Result(null, "URL nesmí být prázdná")
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return Result(null, "URL nemá platný formát")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && !(allowHttp && scheme == "http")) {
            return Result(null, if (allowHttp) "Povoleno je pouze HTTP nebo HTTPS" else "Povoleno je pouze HTTPS")
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null) {
            return Result(null, "URL musí obsahovat platného hostitele bez přihlašovacích údajů")
        }
        return Result(uri.normalize().toASCIIString(), null)
    }
}
