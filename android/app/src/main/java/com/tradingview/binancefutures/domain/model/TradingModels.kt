package com.tradingview.binancefutures.domain.model

data class Candle(
    val time: Long,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Float
)

data class TradingPair(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val price: Float,
    val priceChangePercent: Float,
    val highPrice: Float,
    val lowPrice: Float,
    val volume: Float,
    val quoteVolume: Float
)

enum class Timeframe(val interval: String, val displayName: String, val durationMs: Long) {
    M1("1m", "1m", 60 * 1000L),
    M3("3m", "3m", 3 * 60 * 1000L),
    M5("5m", "5m", 5 * 60 * 1000L),
    M9("9m", "9m", 9 * 60 * 1000L),
    M15("15m", "15m", 15 * 60 * 1000L),
    M30("30m", "30m", 30 * 60 * 1000L),
    M45("45m", "45m", 45 * 60 * 1000L),
    H1("1h", "1h", 60 * 60 * 1000L),
    H2("2h", "2h", 2 * 60 * 60 * 1000L),
    H4("4h", "4h", 4 * 60 * 60 * 1000L),
    H6("6h", "6h", 6 * 60 * 60 * 1000L),
    H8("8h", "8h", 8 * 60 * 60 * 1000L),
    H12("12h", "12h", 12 * 60 * 60 * 1000L),
    D1("1d", "1D", 24 * 60 * 60 * 1000L),
    D3("3d", "3D", 3 * 24 * 60 * 60 * 1000L),
    W1("1w", "1W", 7 * 24 * 60 * 60 * 1000L),
    MO1("1M", "1M", 30 * 24 * 60 * 60 * 1000L);

    companion object {
        fun fromInterval(interval: String): Timeframe {
            val clean = interval.trim().lowercase()
            return values().firstOrNull { it.interval.equals(clean, ignoreCase = true) } ?: M1
        }
    }
}

enum class ConnectionStatus {
    CONNECTING, CONNECTED, RECONNECTING, ERROR
}

enum class PriceType {
    LAST, MARK, INDEX
}

enum class DrawingType {
    TRENDLINE, HORIZONTAL_LINE, RECTANGLE, PRICE_RANGE
}

data class ChartPoint(
    val time: Long,
    val price: Float
)

data class Drawing(
    val id: String,
    val type: DrawingType,
    val points: List<ChartPoint>,
    val color: Long = 0xFF2962FF,
    val strokeWidth: Float = 2f,
    val isLocked: Boolean = false,
    val isSelected: Boolean = false
)

enum class AlertCondition(val displayName: String) {
    CROSSES_ABOVE("Price Crosses Above"),
    CROSSES_BELOW("Price Crosses Below"),
    CANDLE_CLOSES_ABOVE("Candle Closes Above"),
    CANDLE_CLOSES_BELOW("Candle Closes Below")
}

data class PriceAlert(
    val id: String,
    val symbol: String,
    val targetPrice: Float,
    val condition: AlertCondition,
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val isTriggered: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true
)

data class BarReplayState(
    val isActive: Boolean = false,
    val cutIndex: Int = -1,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val speedMs: Long = 1000L
)

/** Format futures prices without truncating low-priced symbols to two decimals. */
fun formatMarketPrice(price: Float): String {
    if (!price.isFinite()) return "--"
    val value = kotlin.math.abs(price)
    val decimals = when {
        value >= 100f -> 2
        value >= 1f -> 4
        value >= 0.01f -> 6
        else -> 8
    }
    return java.math.BigDecimal.valueOf(price.toDouble())
        .setScale(decimals, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
