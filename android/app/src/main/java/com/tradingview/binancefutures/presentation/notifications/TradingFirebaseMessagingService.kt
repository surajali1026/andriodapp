package com.tradingview.binancefutures.presentation.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tradingview.binancefutures.R
import com.tradingview.binancefutures.presentation.MainActivity
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class TradingFirebaseMessagingService : FirebaseMessagingService() {
    private val io = Executors.newSingleThreadExecutor()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        io.execute { registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title ?: "Trading Alert"
        val body = message.data["body"] ?: message.notification?.body ?: "Your price alert was triggered."
        showNotification(title, body)
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun registerToken(token: String) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("$WORKER_URL/api/device/register").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = """{"token":"$token","platform":"android"}"""
            connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "FCM token registration failed: HTTP ${connection.responseCode}")
            }
        } catch (error: Exception) {
            Log.w(TAG, "FCM token registration error", error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trading price alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Price alerts from Binance Futures"
                enableVibration(true)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification permission not granted", error)
        }
    }

    companion object {
        private const val TAG = "TradingFCM"
        private const val CHANNEL_ID = "trading_price_alerts"
        private const val WORKER_URL = "https://jolly-hall-eb84suraj-trading-alerts.surajali1026.workers.dev"
    }
}
