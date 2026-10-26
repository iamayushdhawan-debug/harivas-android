package com.harivas.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Centralised notification helper for HARIVAS.
 *
 * Channels:
 *  - CHANNEL_ORDERS        : normal priority  – order status updates
 *  - CHANNEL_RIDER_ALERT   : high priority + full-screen – incoming rider jobs
 *  - CHANNEL_LOCATION      : low priority     – persistent location foreground
 */
object HvNotifications {

    // ── Channel IDs ───────────────────────────────────────────────────────────
    const val CHANNEL_ORDERS       = "hv_orders"
    const val CHANNEL_RIDER_ALERT  = "hv_rider_alert"
    const val CHANNEL_LOCATION     = "hv_location"

    // ── Notification IDs ──────────────────────────────────────────────────────
    const val NOTIFICATION_ID_LOCATION   = 1001
    private const val NOTIFICATION_ID_RIDER  = 1002
    private const val NOTIFICATION_ID_ORDER  = 1003

    // ── Channel creation ──────────────────────────────────────────────────────

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Order updates – normal importance
        NotificationChannel(
            CHANNEL_ORDERS,
            context.getString(R.string.channel_orders_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_orders_desc)
            nm.createNotificationChannel(this)
        }

        // Rider alert – high importance + full-screen intent support
        NotificationChannel(
            CHANNEL_RIDER_ALERT,
            context.getString(R.string.channel_rider_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_rider_desc)
            enableVibration(true)
            enableLights(true)
            setBypassDnd(true)
            nm.createNotificationChannel(this)
        }

        // Location – low importance (background, no sound)
        NotificationChannel(
            CHANNEL_LOCATION,
            context.getString(R.string.channel_location_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_location_desc)
            setShowBadge(false)
            nm.createNotificationChannel(this)
        }
    }

    // ── Rider alert (FullScreenIntent) ────────────────────────────────────────

    fun showRiderAlert(context: Context, title: String, body: String, orderId: String) {
        val orderUrl = "${BuildConfig.SITE_URL}/harivas-rider/?order=$orderId"
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_ORDER_URL, orderUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RIDER_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_RIDER, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS permission not granted
            }
        }
    }

    // ── Order update notification (standard) ──────────────────────────────────

    fun showOrderNotification(context: Context, title: String, body: String, orderId: String) {
        val orderUrl = "${BuildConfig.SITE_URL}/harivas/?order=$orderId"
        val contentIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode() + 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_ORDER_URL, orderUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_ORDER, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS permission not granted
            }
        }
    }

    // ── Location foreground notification ──────────────────────────────────────

    fun buildLocationNotification(context: Context): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, HvLocationService::class.java).apply {
                action = HvLocationService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.location_notification_title))
            .setContentText(context.getString(R.string.location_notification_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.stop_tracking),
                stopIntent
            )
            .build()
    }
}
