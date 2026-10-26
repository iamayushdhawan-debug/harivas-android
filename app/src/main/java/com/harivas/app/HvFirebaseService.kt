package com.harivas.app

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
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
 * Firebase Cloud Messaging service.
 *
 * Message types:
 *  - `type=rider_alert`   → FullScreenIntent notification (wakes device)
 *  - `type=order_update`  → Normal heads-up notification
 *  - Everything else      → Normal notification using title/body
 */
class HvFirebaseService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "HvFirebaseService"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Token refresh ─────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        registerTokenWithServer(token)
    }

    private fun registerTokenWithServer(token: String) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("token", token)
                    put("platform", "android")
                }.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BuildConfig.SITE_URL}/wp-json/harivas/v1/push/register")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Token registered successfully")
                    } else {
                        Log.w(TAG, "Token registration failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token registration error", e)
            }
        }
    }

    // ── Message received ──────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        val data    = message.data
        val type    = data["type"] ?: ""
        val title   = data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val body    = data["body"]  ?: message.notification?.body  ?: ""
        val orderId = data["order_id"] ?: ""

        when (type) {
            "rider_alert" -> {
                // High-priority full-screen notification (wakes device)
                HvNotifications.showRiderAlert(applicationContext, title, body, orderId)
            }
            "order_update" -> {
                // Normal notification that appears in the notification drawer
                HvNotifications.showOrderNotification(applicationContext, title, body, orderId)
            }
            else -> {
                // Generic notification
                HvNotifications.showOrderNotification(applicationContext, title, body, orderId)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
