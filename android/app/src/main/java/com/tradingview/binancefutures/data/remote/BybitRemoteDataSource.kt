package com.tradingview.binancefutures.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tradingview.binancefutures.domain.model.Candle
import com.tradingview.binancefutures.domain.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Bybit supplies chart history and the live kline stream.
 * Binance is deliberately not used by this class.
 */
class BybitRemoteDataSource(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val wsClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val gson: Gson = Gson()
) {
    private val restBase = "https://api.bybit.com"
    private val wsUrl = "wss://stream.bybit.com/v5/public/linear"

    private fun bybitInterval(timeframe: Timeframe): String = when (timeframe) {
        Timeframe.M1 -> "1"
        Timeframe.M3, Timeframe.M9 -> "3"
        Timeframe.M5 -> "5"
        Timeframe.M15, Timeframe.M45 -> "15"
        Timeframe.M30 -> "30"
        Timeframe.H1 -> "60"
        Timeframe.H2 -> "120"
        Timeframe.H4 -> "240"
        Timeframe.H6 -> "360"
        Timeframe.H8 -> "360"
        Timeframe.H12 -> "720"
        Timeframe.D1, Timeframe.D3 -> "D"
        Timeframe.W1 -> "W"
        Timeframe.MO1 -> "M"
    }

    suspend fun getHistoricalKlines(symbol: String, timeframe: Timeframe, limit: Int = 500): List<Candle> =
        withContext(Dispatchers.IO) {
            val safeSymbol = symbol.uppercase().filter { it.isLetterOrDigit() }
            if (!safeSymbol.endsWith("USDT")) return@withContext emptyList()
            val url = "$restBase/v5/market/kline?category=linear&symbol=$safeSymbol&interval=${bybitInterval(timeframe)}&limit=${limit.coerceIn(1, 1000)}"
            try {
                val request = Request.Builder().url(url).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val root = JsonParser.parseString(body).asJsonObject
                    if (root.get("retCode")?.asInt != 0) return@withContext emptyList()
                    val rows = root.getAsJsonObject("result")?.getAsJsonArray("list") ?: return@withContext emptyList()
                    rows.mapNotNull { row ->
                        if (!row.isJsonArray || row.asJsonArray.size() < 6) return@mapNotNull null
                        val a = row.asJsonArray
                        val candle = Candle(
                            time = a[0].asString.toLongOrNull() ?: return@mapNotNull null,
                            open = a[1].asString.toFloatOrNull() ?: return@mapNotNull null,
                            high = a[2].asString.toFloatOrNull() ?: return@mapNotNull null,
                            low = a[3].asString.toFloatOrNull() ?: return@mapNotNull null,
                            close = a[4].asString.toFloatOrNull() ?: return@mapNotNull null,
                            volume = a[5].asString.toFloatOrNull() ?: 0f
                        )
                        if (candle.open > 0f && candle.high >= maxOf(candle.open, candle.close, candle.low) &&
                            candle.low <= minOf(candle.open, candle.close, candle.high)) candle else null
                    }.sortedBy { it.time }.takeLast(limit.coerceIn(1, 1000))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun observeLiveKline(symbol: String, timeframe: Timeframe): Flow<Candle> = callbackFlow {
        val safeSymbol = symbol.uppercase().filter { it.isLetterOrDigit() }
        if (!safeSymbol.endsWith("USDT")) {
            close(IllegalArgumentException("Bybit linear USDT symbol required: $symbol"))
            return@callbackFlow
        }
        val topic = "kline.${bybitInterval(timeframe)}.$safeSymbol"
        val request = Request.Builder().url(wsUrl).build()
        val socket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(gson.toJson(mapOf("op" to "subscribe", "args" to listOf(topic))))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = gson.fromJson(text, JsonObject::class.java)
                    val data = root.getAsJsonArray("data") ?: return
                    data.forEach { item ->
                        if (!item.isJsonObject) return@forEach
                        val k = item.asJsonObject
                        val candle = Candle(
                            time = k.get("start")?.asLong ?: return@forEach,
                            open = k.get("open")?.asString?.toFloatOrNull() ?: return@forEach,
                            high = k.get("high")?.asString?.toFloatOrNull() ?: return@forEach,
                            low = k.get("low")?.asString?.toFloatOrNull() ?: return@forEach,
                            close = k.get("close")?.asString?.toFloatOrNull() ?: return@forEach,
                            volume = k.get("volume")?.asString?.toFloatOrNull() ?: 0f,
                            isClosed = k.get("confirm")?.asBoolean ?: false
                        )
                        if (candle.open > 0f && candle.high >= maxOf(candle.open, candle.close, candle.low) &&
                            candle.low <= minOf(candle.open, candle.close, candle.high)) trySend(candle)
                    }
                } catch (_: Exception) {
                    // Ignore Bybit heartbeat and malformed frames.
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose { socket.close(1000, "Chart changed") }
    }
}
