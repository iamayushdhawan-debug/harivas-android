package com.harivas.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.harivas.app.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ── View Binding ─────────────────────────────────────────────────────────
    private lateinit var binding: ActivityMainBinding

    // ── File chooser state ───────────────────────────────────────────────────
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // ── Activity result launchers ─────────────────────────────────────────────
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                when {
                    data?.clipData != null -> {
                        val count = data.clipData!!.itemCount
                        Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                    }
                    data?.data != null -> arrayOf(data.data!!)
                    cameraImageUri != null -> arrayOf(cameraImageUri!!)
                    else -> null
                }
            } else {
                null
            }
            fileChooserCallback?.onReceiveValue(uris)
            fileChooserCallback = null
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled silently */ }

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* handled in HvJsBridge callback */ }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupSwipeRefresh()
        requestNotificationPermissionIfNeeded()

        // Handle deep-link intent that launched the activity
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // ── Deep-link / Intent handling ──────────────────────────────────────────

    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null && data.toString().startsWith(BuildConfig.SITE_URL)) {
            binding.webView.loadUrl(data.toString())
        } else {
            // Order alert intent from HvFirebaseService / HvJsBridge
            val orderUrl = intent.getStringExtra(EXTRA_ORDER_URL)
            if (!orderUrl.isNullOrEmpty()) {
                binding.webView.loadUrl(orderUrl)
            }
        }
    }

    // ── WebView setup ────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView

        // Enable WebView debugging in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            // User-agent: keep default but append identifier for server-side detection
            userAgentString = (userAgentString ?: "") + " HarivasAndroid/1.0"
        }

        // Cookies – accept third-party so sessions work across all portals
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Attach clients
        webView.webViewClient = HvWebViewClient(
            context = this,
            onPageStarted = { binding.swipeRefresh.isRefreshing = false },
            onOffline = { showOfflineView(true) },
            onOnline = { showOfflineView(false) }
        )

        webView.webChromeClient = object : WebChromeClient() {
            // Auto-grant geolocation permission (Android OS already asked the user)
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }

            // File chooser for <input type="file"> (API 21+)
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // Cancel any pending callback
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val chooserIntent = buildFileChooserIntent(fileChooserParams)
                fileChooserLauncher.launch(chooserIntent)
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress == 100) binding.swipeRefresh.isRefreshing = false
            }
        }

        // JavaScript bridge
        val bridge = HvJsBridge(this)
        webView.addJavascriptInterface(bridge, "HvNative")

        // Load default portal
        webView.loadUrl("${BuildConfig.SITE_URL}/harivas/")
    }

    // ── File chooser intent builder ──────────────────────────────────────────

    private fun buildFileChooserIntent(params: WebChromeClient.FileChooserParams): Intent {
        val contentSelectionIntent = params.createIntent()

        // Create camera capture intent
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createTempImageFile()
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)

        val intentArray = arrayOf(cameraIntent)
        return Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            putExtra(Intent.EXTRA_TITLE, getString(R.string.choose_file))
            putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
        }
    }

    private fun createTempImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("HARIVAS_${timeStamp}_", ".jpg", storageDir)
    }

    // ── SwipeRefreshLayout ───────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeResources(R.color.primary, R.color.accent)
            setOnRefreshListener {
                if (binding.webView.scrollY == 0) {
                    binding.webView.reload()
                } else {
                    isRefreshing = false
                }
            }
        }
    }

    // ── Offline / Online view ────────────────────────────────────────────────

    private fun showOfflineView(show: Boolean) {
        binding.offlineView.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.webView.visibility = if (show) android.view.View.GONE else android.view.View.VISIBLE
        if (show) {
            binding.swipeRefresh.isRefreshing = false
            binding.retryButton.setOnClickListener {
                showOfflineView(false)
                binding.webView.reload()
            }
        }
    }

    // ── Notification permission (Android 13+) ────────────────────────────────

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── Location permission helper (called from HvJsBridge) ─────────────────

    fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // ── Back navigation (WebView back-stack) ─────────────────────────────────

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ── Inject HvNative shim after every page load ───────────────────────────
    // Called by HvWebViewClient so the JS bridge is always available
    fun injectNativeShim() {
        binding.webView.evaluateJavascript(
            "javascript:(function(){ window.HvNative = window.HvNative || { isAndroidApp: function(){ return true; } }; })()",
            null
        )
    }

    companion object {
        const val EXTRA_ORDER_URL = "extra_order_url"
    }
}
