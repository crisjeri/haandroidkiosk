package cz.hykel.hakiosk

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.Toast

class WebViewController(
    private val webView: WebView,
    private val preferences: PreferencesRepository,
    private val onErrorChanged: (String?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val retryPolicy = RetryPolicy()
    private var retryAttempt = 0
    private var networkAvailable = true
    private var hasLoadedContent = false
    private var displayingErrorPage = false

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            safeBrowsingEnabled = true
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (isAllowed(uri)) {
                    false
                } else {
                    Toast.makeText(view.context, R.string.external_url_blocked, Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!url.startsWith("https://kiosk.invalid/")) displayingErrorPage = false
                onErrorChanged(null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (displayingErrorPage || url.startsWith("https://kiosk.invalid/")) return
                hasLoadedContent = true
                retryAttempt = 0
                onErrorChanged(null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val message = "WebView: ${error.errorCode}"
                onErrorChanged(message)
                if (!hasLoadedContent) showLocalErrorPage()
                scheduleRetry()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                onErrorChanged("Chyba TLS certifikátu")
            }
        }
    }

    fun loadHome() {
        if (preferences.homeUrl.isNotBlank()) webView.loadUrl(preferences.homeUrl)
    }

    fun reload() {
        handler.removeCallbacksAndMessages(null)
        webView.reload()
    }

    fun onNetworkChanged(available: Boolean) {
        val restored = !networkAvailable && available
        networkAvailable = available
        if (restored && preferences.reloadOnNetwork) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ webView.reload() }, 1_500L)
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    private fun isAllowed(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        val validScheme = scheme == "https" || (preferences.allowHttp && scheme == "http")
        val allowedHost = Uri.parse(preferences.homeUrl).host.orEmpty().lowercase()
        return validScheme && uri.host?.lowercase() == allowedHost
    }

    private fun scheduleRetry() {
        if (!networkAvailable) return
        handler.removeCallbacksAndMessages(null)
        val delay = retryPolicy.delayMillis(retryAttempt++)
        handler.postDelayed({ if (networkAvailable) webView.reload() }, delay)
    }

    private fun showLocalErrorPage() {
        displayingErrorPage = true
        val html = """
            <!doctype html><html lang="cs"><meta name="viewport" content="width=device-width">
            <body style="background:#121212;color:#fff;font-family:sans-serif;text-align:center;padding-top:20vh">
            <h1>Home Assistant není dostupný</h1><p>Po obnovení sítě se stránka načte automaticky.</p></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://kiosk.invalid/", html, "text/html", "UTF-8", null)
    }
}
