package com.tradingview.binancefutures.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.tradingview.binancefutures.domain.model.Candle
import com.tradingview.binancefutures.domain.model.Timeframe
import com.tradingview.binancefutures.domain.model.TradingPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class BinanceRemoteDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val gson: Gson = Gson()
) {
    private val baseUrl = "https://fapi.binance.com"
    private val wsBaseUrl = "wss://fstream.binance.com/ws"

    fun getBinanceSupportedInterval(timeframe: Timeframe): String {
        return when (timeframe) {
            Timeframe.M9 -> "5m"
            Timeframe.M45 -> "15m"
            else -> timeframe.interval
        }
    }

    suspend fun getServerTime(): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/fapi/v1/time").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext System.currentTimeMillis()
                val body = response.body?.string() ?: return@withContext System.currentTimeMillis()
                val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                obj.get("serverTime")?.asLong ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    suspend fun getFuturesTickers(): List<TradingPair> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/fapi/v1/ticker/24hr")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext getFallbackTradingPairs()
                val body = response.body?.string() ?: return@withContext getFallbackTradingPairs()
                val jsonElement = com.google.gson.JsonParser.parseString(body)
                if (!jsonElement.isJsonArray) return@withContext getFallbackTradingPairs()

                val array = jsonElement.asJsonArray
                val list = mutableListOf<TradingPair>()
                for (element in array) {
                    if (!element.isJsonObject) continue
                    val obj = element.asJsonObject
                    val symbol = obj.get("symbol")?.asString ?: continue
                    val isUsdt = symbol.endsWith("USDT")
                    val isUsdc = symbol.endsWith("USDC")
                    if (isUsdt || isUsdc) {
                        val quote = if (isUsdt) "USDT" else "USDC"
                        val base = symbol.removeSuffix(quote)
                        val lastPrice = obj.get("lastPrice")?.asString?.toFloatOrNull() ?: 0f
                        val priceChange = obj.get("priceChangePercent")?.asString?.toFloatOrNull() ?: 0f
                        val highPrice = obj.get("highPrice")?.asString?.toFloatOrNull() ?: 0f
                        val lowPrice = obj.get("lowPrice")?.asString?.toFloatOrNull() ?: 0f
                        val volume = obj.get("volume")?.asString?.toFloatOrNull() ?: 0f
                        val quoteVolume = obj.get("quoteVolume")?.asString?.toFloatOrNull() ?: 0f
                        list.add(
                            TradingPair(
                                symbol = symbol,
                                baseAsset = base,
                                quoteAsset = quote,
                                price = lastPrice,
                                priceChangePercent = priceChange,
                                highPrice = highPrice,
                                lowPrice = lowPrice,
                                volume = volume,
                                quoteVolume = quoteVolume
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) list.sortedByDescending { it.quoteVolume } else getFallbackTradingPairs()
            }
        } catch (e: Exception) {
            getFallbackTradingPairs()
        }
    }

    private fun getFallbackTradingPairs(): List<TradingPair> {
        val topSymbols = listOf(
            "BTCUSDT" to 96250f, "ETHUSDT" to 3450f, "SOLUSDT" to 195f, "BNBUSDT" to 630f,
            "DOGEUSDT" to 0.38f, "XRPUSDT" to 1.15f, "ADAUSDT" to 0.75f, "AVAXUSDT" to 34f,
            "SUIUSDT" to 3.2f, "LINKUSDT" to 18f, "PEPEUSDT" to 0.000019f, "SHIBUSDT" to 0.000024f,
            "NEARUSDT" to 6.5f, "KASUSDT" to 0.15f, "RENDERUSDT" to 8.2f, "TAOUSDT" to 480f,
            "FETUSDT" to 1.45f, "WIFUSDT" to 3.1f, "BONKUSDT" to 0.000038f, "OPUSDT" to 1.8f
        )
        return topSymbols.map { (sym, price) ->
            TradingPair(
                symbol = sym,
                baseAsset = sym.removeSuffix("USDT"),
                quoteAsset = "USDT",
                price = price,
                priceChangePercent = 2.45f,
                highPrice = price * 1.04f,
                lowPrice = price * 0.97f,
                volume = 1200000f,
                quoteVolume = 50000000f
            )
        }
    }

    suspend fun getHistoricalKlines(
        symbol: String,
        timeframe: Timeframe,
        priceType: com.tradingview.binancefutures.domain.model.PriceType = com.tradingview.binancefutures.domain.model.PriceType.LAST,
        limit: Int = 500
    ): List<Candle> = withContext(Dispatchers.IO) {
        val endpoint = when (priceType) {
            com.tradingview.binancefutures.domain.model.PriceType.LAST -> "klines?symbol=${symbol.uppercase()}"
            com.tradingview.binancefutures.domain.model.PriceType.MARK -> "markPriceKlines?symbol=${symbol.uppercase()}"
            com.tradingview.binancefutures.domain.model.PriceType.INDEX -> "indexPriceKlines?pair=${symbol.uppercase()}"
        }
        val interval = getBinanceSupportedInterval(timeframe)
        val url = "$baseUrl/fapi/v1/$endpoint&interval=$interval&limit=$limit"
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (priceType == com.tradingview.binancefutures.domain.model.PriceType.INDEX) {
                        val fallbackUrl = "$baseUrl/fapi/v1/indexPriceKlines?symbol=${symbol.uppercase()}&interval=$interval&limit=$limit"
                        client.newCall(Request.Builder().url(fallbackUrl).build()).execute().use { fbResp ->
                            if (fbResp.isSuccessful) {
                                val fbBody = fbResp.body?.string() ?: return@withContext emptyList()
                                return@withContext parseKlinesJson(fbBody)
                            }
                        }
                    }
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                parseKlinesJson(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseKlinesJson(jsonString: String): List<Candle> {
        return try {
            val jsonElement = com.google.gson.JsonParser.parseString(jsonString)
            if (!jsonElement.isJsonArray) return emptyList()
            val array = jsonElement.asJsonArray
            val candles = mutableListOf<Candle>()
            for (element in array) {
                if (!element.isJsonArray) continue
                val kline = element.asJsonArray
                if (kline.size() >= 6) {
                    candles.add(
                        Candle(
                            time = kline.get(0).asLong,
                            open = kline.get(1).asString.toFloatOrNull() ?: 0f,
                            high = kline.get(2).asString.toFloatOrNull() ?: 0f,
                            low = kline.get(3).asString.toFloatOrNull() ?: 0f,
                            close = kline.get(4).asString.toFloatOrNull() ?: 0f,
                            volume = kline.get(5).asString.toFloatOrNull() ?: 0f
                        )
                    )
                }
            }
            candles
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun observeLiveKline(symbol: String, timeframe: Timeframe): Flow<Candle> = callbackFlow {
        val interval = getBinanceSupportedInterval(timeframe)
        val stream = "${symbol.lowercase()}@kline_$interval"
        val request = Request.Builder().url("$wsBaseUrl/$stream").build()

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = gson.fromJson(text, com.google.gson.JsonObject::class.java)
                    if (root.has("k")) {
                        val k = root.getAsJsonObject("k")
                        val candle = Candle(
                            time = k.get("t").asLong,
                            open = k.get("o").asFloat,
                            high = k.get("h").asFloat,
                            low = k.get("l").asFloat,
                            close = k.get("c").asFloat,
                            volume = k.get("v").asFloat
                        )
                        trySend(candle)
                    }
                } catch (e: Exception) {
                    // ignore json parse error
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })

        awaitClose {
            webSocket.close(1000, "Closed flow")
        }
    }
}
