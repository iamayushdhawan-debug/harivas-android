package com.harivas.app

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Foreground service that streams rider location to the HARIVAS REST API
 * every 5 seconds using FusedLocationProviderClient.
 *
 * Filters:
 *  - Accuracy worse than 50 m  → ignored
 *  - Fix older than 30 s        → ignored
 */
class HvLocationService : Service() {

    companion object {
        const val ACTION_START = "com.harivas.app.ACTION_LOCATION_START"
        const val ACTION_STOP  = "com.harivas.app.ACTION_LOCATION_STOP"
        const val EXTRA_ORDER_ID = "extra_order_id"

        private const val TAG               = "HvLocationService"
        private const val MAX_ACCURACY_M    = 50f          // ignore if worse than 50 m
        private const val MAX_AGE_MS        = 30_000L      // ignore if older than 30 s
        private const val LOCATION_INTERVAL = 5_000L       // 5 s

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var orderId: String = ""

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location: Location = result.lastLocation ?: return
            if (!isLocationFresh(location)) {
                Log.d(TAG, "Stale/inaccurate fix ignored: acc=${location.accuracy}m")
                return
            }
            postLocationToServer(location)
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
                startForegroundWithNotification()
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val notification: Notification = HvNotifications.buildLocationNotification(this)
        // ServiceCompat handles the API-34 mandatory type parameter automatically
        ServiceCompat.startForeground(
            this,
            HvNotifications.NOTIFICATION_ID_LOCATION,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    // ── Location updates ──────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL / 2)
            .setWaitForAccurateLocation(false)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Quality filter ────────────────────────────────────────────────────────

    private fun isLocationFresh(location: Location): Boolean {
        val ageMs = SystemClock.elapsedRealtimeNanos() / 1_000_000L -
                location.elapsedRealtimeNanos / 1_000_000L
        return location.accuracy <= MAX_ACCURACY_M && ageMs <= MAX_AGE_MS
    }

    // ── HTTP post ─────────────────────────────────────────────────────────────

    private fun postLocationToServer(location: Location) {
        serviceScope.launch {
            try {
                val body = JSONObject().apply {
                    put("lat",      location.latitude)
                    put("lng",      location.longitude)
                    put("accuracy", location.accuracy)
                    if (orderId.isNotEmpty()) put("order_id", orderId)
                }.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BuildConfig.SITE_URL}/wp-json/harivas/v1/rider/location")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Location post failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Location post error", e)
            }
        }
    }
}
