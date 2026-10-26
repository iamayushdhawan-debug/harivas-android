package com.harivas.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Custom WebViewClient for HARIVAS app.
 *
 * - Keeps navigation inside the HARIVAS site.
 * - Intercepts external schemes (tel:, mailto:, geo:, etc.) and payment
 *   redirects (Razorpay) to system intents.
 * - Detects offline state and notifies the host Activity.
 */
class HvWebViewClient(
    private val context: Context,
    private val onPageStarted: () -> Unit,
    private val onOffline: () -> Unit,
    private val onOnline: () -> Unit
) : WebViewClient() {

    private val siteHost: String by lazy {
        Uri.parse(BuildConfig.SITE_URL).host ?: ""
    }

    // Known external payment / deep-link prefixes that must leave the WebView
    private val externalPrefixes = listOf(
        "razorpay://",
        "phonepe://",
        "paytm://",
        "upi://",
        "gpay://",
        "intent://"
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme ?: ""
        val host = request.url.host ?: ""

        return when {
            // Keep in-app if same host
            host == siteHost -> false

            // Phone dialler
            scheme == "tel" -> {
                launchIntent(Intent(Intent.ACTION_DIAL, request.url))
                true
            }

            // Email
            scheme == "mailto" -> {
                launchIntent(Intent(Intent.ACTION_SENDTO, request.url))
                true
            }

            // Geo / Maps
            scheme == "geo" || scheme == "maps" -> {
                launchIntent(Intent(Intent.ACTION_VIEW, request.url))
                true
            }

            // Payment gateways & special scheme intents
            externalPrefixes.any { url.startsWith(it) } -> {
                try {
                    launchIntent(Intent.parseUri(url, Intent.URI_INTENT_SCHEME))
                } catch (_: Exception) {
                    launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                true
            }

            // Market / Play Store links
            scheme == "market" -> {
                launchIntent(Intent(Intent.ACTION_VIEW, request.url))
                true
            }

            // Truly external web URL – open in browser
            scheme == "https" || scheme == "http" -> {
                launchIntent(Intent(Intent.ACTION_VIEW, request.url))
                true
            }

            else -> false
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted()
        if (!isNetworkAvailable()) {
            onOffline()
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Inject shim so JS can detect native environment
        (context as? MainActivity)?.injectNativeShim()
        // Sync cookies after navigation
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String,
        failingUrl: String
    ) {
        if (!isNetworkAvailable()) {
            onOffline()
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: android.webkit.WebResourceResponse
    ) {
        // Ignore sub-resource errors; only surface main-frame errors
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun launchIntent(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // No handler – silently ignore
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
