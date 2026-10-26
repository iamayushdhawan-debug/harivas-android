package com.harivas.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * JavaScript bridge injected as `window.HvNative`.
 *
 * All public methods are annotated with @JavascriptInterface and
 * run on a background thread – UI operations must be dispatched
 * to the main thread via Activity.runOnUiThread {}.
 */
class HvJsBridge(private val activity: MainActivity) {

    // ── App detection ─────────────────────────────────────────────────────────

    @JavascriptInterface
    fun isAndroidApp(): Boolean = true

    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    // ── Location tracking ────────────────────────────────────────────────────

    @JavascriptInterface
    fun startLocationTracking(orderId: String) {
        activity.runOnUiThread {
            val intent = Intent(activity, HvLocationService::class.java).apply {
                action = HvLocationService.ACTION_START
                putExtra(HvLocationService.EXTRA_ORDER_ID, orderId)
            }
            ContextCompat.startForegroundService(activity, intent)
        }
    }

    @JavascriptInterface
    fun stopLocationTracking() {
        activity.runOnUiThread {
            val intent = Intent(activity, HvLocationService::class.java).apply {
                action = HvLocationService.ACTION_STOP
            }
            activity.startService(intent)
        }
    }

    // ── Notification permission ───────────────────────────────────────────────

    @JavascriptInterface
    fun requestNotificationPermission() {
        activity.runOnUiThread {
            activity.requestNotificationPermissionIfNeeded()
        }
    }

    // ── Dialler ───────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun openDialer(phone: String) {
        activity.runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(activity, "Unable to open dialler", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Maps ─────────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun openMaps(lat: Double, lng: Double, label: String) {
        activity.runOnUiThread {
            try {
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (_: Exception) {
                // Fallback: open Google Maps URL in browser
                val webUri = Uri.parse(
                    "https://maps.google.com/maps?q=$lat,$lng(${Uri.encode(label)})"
                )
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun shareText(text: String) {
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(Intent.createChooser(intent, "Share via").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // ── Vibrate ───────────────────────────────────────────────────────────────
    // `pattern` is a comma-separated list of millisecond durations
    // e.g. "0,200,100,200" means: wait 0ms, vibrate 200ms, wait 100ms, vibrate 200ms

    @JavascriptInterface
    fun vibrate(pattern: String) {
        activity.runOnUiThread {
            try {
                @Suppress("DEPRECATION")
                val vibrator = activity.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                        as Vibrator
                val parts = pattern.split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()
                if (parts.isEmpty()) return@runOnUiThread
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(parts, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(parts, -1)
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // ── Full-screen alert notification (e.g., new order for rider) ───────────
    // Called from JS when a critical event occurs.

    @JavascriptInterface
    fun showFullScreenAlert(title: String, body: String, orderId: String) {
        activity.runOnUiThread {
            HvNotifications.showRiderAlert(activity, title, body, orderId)
        }
    }
}
