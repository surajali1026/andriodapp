package com.tradingview.binancefutures.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tradingview.binancefutures.data.repository.TradingRepository
import com.tradingview.binancefutures.data.repository.TradingRepositoryImpl
import com.tradingview.binancefutures.domain.model.*
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChartUiState(
    val symbol: String = "BTCUSDT",
    val timeframe: Timeframe = Timeframe.M1,
    val priceType: PriceType = PriceType.LAST,
    val candles: List<Candle> = emptyList(),
    val pairs: List<TradingPair> = emptyList(),
    val favorites: Set<String> = setOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT"),
    val drawings: List<Drawing> = emptyList(),
    val activeDrawingTool: DrawingType? = null,
    val selectedDrawingId: String? = null,
    val alerts: List<PriceAlert> = emptyList(),
    val replayState: BarReplayState = BarReplayState(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
    val serverTimeOffset: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ChartViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TradingRepository = TradingRepositoryImpl()
) : AndroidViewModel(application) {

    private val gson = Gson()
    private val prefs = application.getSharedPreferences("tradingview_binance_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var wsJob: Job? = null
    private var pollJob: Job? = null
    private var serverTimeJob: Job? = null
    private var previousPrice: Float? = null

    init {
        loadSavedFavoritesAndAlerts()
        loadTradingPairs()
        syncServerTime()
        loadCandles("BTCUSDT", Timeframe.M1)
    }

    private fun syncServerTime() {
        serverTimeJob?.cancel()
        serverTimeJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val serverTime = repository.getServerTime()
                    val offset = serverTime - System.currentTimeMillis()
                    _uiState.value = _uiState.value.copy(serverTimeOffset = offset)
                } catch (e: Exception) {
                    // keep current offset
                }
                delay(60_000L) // Resync every minute
            }
        }
    }

    private fun loadSavedFavoritesAndAlerts() {
        try {
            val favsJson = prefs.getString("saved_favorites", null)
            val savedFavs: Set<String>? = if (favsJson != null) {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson(favsJson, type)
            } else null

            val alertsJson = prefs.getString("saved_alerts", null)
            val savedAlerts: List<PriceAlert>? = if (alertsJson != null) {
                val type = object : TypeToken<List<PriceAlert>>() {}.type
                gson.fromJson(alertsJson, type)
            } else null

            _uiState.value = _uiState.value.copy(
                favorites = savedFavs ?: _uiState.value.favorites,
                alerts = savedAlerts ?: emptyList()
            )
        } catch (e: Exception) {
            // fallback to defaults
        }
    }

    private fun saveAlertsToDisk(alerts: List<PriceAlert>) {
        try {
            val json = gson.toJson(alerts)
            prefs.edit().putString("saved_alerts", json).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun saveFavoritesToDisk(favs: Set<String>) {
        try {
            val json = gson.toJson(favs)
            prefs.edit().putString("saved_favorites", json).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun loadTradingPairs() {
        viewModelScope.launch {
            try {
                val pairs = repository.getFuturesTradingPairs()
                _uiState.value = _uiState.value.copy(pairs = pairs)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun selectSymbol(symbol: String) {
        if (_uiState.value.symbol == symbol) return
        _uiState.value = _uiState.value.copy(symbol = symbol)
        previousPrice = null
        loadCandles(symbol, _uiState.value.timeframe, _uiState.value.priceType)
    }

    fun selectTimeframe(tf: Timeframe) {
        if (_uiState.value.timeframe == tf) return
        _uiState.value = _uiState.value.copy(timeframe = tf)
        loadCandles(_uiState.value.symbol, tf, _uiState.value.priceType)
    }

    fun setPriceType(type: PriceType) {
        if (_uiState.value.priceType == type) return
        _uiState.value = _uiState.value.copy(priceType = type)
        loadCandles(_uiState.value.symbol, _uiState.value.timeframe, type)
    }

    fun toggleFavorite(symbol: String) {
        val current = _uiState.value.favorites
        val updated = if (current.contains(symbol)) current - symbol else current + symbol
        _uiState.value = _uiState.value.copy(favorites = updated)
        saveFavoritesToDisk(updated)
    }

    // Navigate within the saved favourites when the current coin is in the watchlist;
    // otherwise keep the full Binance USD-M pair order.
    private fun navigationPairs(): List<TradingPair> {
        val state = _uiState.value
        val favouritePairs = state.pairs.filter { state.favorites.contains(it.symbol) }
            .sortedByDescending { it.quoteVolume }
        return if (state.favorites.contains(state.symbol) && favouritePairs.isNotEmpty()) favouritePairs else state.pairs
    }

    fun selectNextCoin() {
        val pairs = navigationPairs()
        if (pairs.isEmpty()) return
        val currentIndex = pairs.indexOfFirst { it.symbol == _uiState.value.symbol }
        if (currentIndex >= 0 && currentIndex < pairs.lastIndex) selectSymbol(pairs[currentIndex + 1].symbol)
        else if (currentIndex == -1) selectSymbol(pairs.first().symbol)
    }

    fun selectPreviousCoin() {
        val pairs = navigationPairs()
        if (pairs.isEmpty()) return
        val currentIndex = pairs.indexOfFirst { it.symbol == _uiState.value.symbol }
        if (currentIndex > 0) selectSymbol(pairs[currentIndex - 1].symbol)
    }

    private fun loadCandles(symbol: String, tf: Timeframe, pType: PriceType = _uiState.value.priceType) {
        loadJob?.cancel()
        wsJob?.cancel()
        pollJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                connectionStatus = ConnectionStatus.CONNECTING,
                errorMessage = null
            )
            try {
                val history = repository.getHistoricalCandles(symbol, tf, pType)
                _uiState.value = _uiState.value.copy(
                    candles = history,
                    isLoading = false,
                    connectionStatus = ConnectionStatus.CONNECTED
                )
                if (history.isNotEmpty()) {
                    previousPrice = history.last().close
                }

                // 1. WebSocket Live Stream with persistent auto-reconnection loop
                wsJob = launch {
                    while (isActive) {
                        try {
                            repository.getLiveCandleStream(symbol, tf)
                                .catch { 
                                    _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.RECONNECTING)
                                }
                                .collect { updatedCandle ->
                                    _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTED)
                                    handleCandleUpdate(updatedCandle)
                                }
                        } catch (e: Exception) {
                            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.RECONNECTING)
                        }
                        delay(2500L) // Persistent auto-reconnect after 2.5s if WebSocket disconnects
                    }
                }

                // 2. Gentle Fallback Poller (runs every 10s only when disconnected, preventing socket exhaustion & OOM)
                pollJob = launch {
                    while (isActive) {
                        delay(10_000L)
                        try {
                            if (_uiState.value.connectionStatus != ConnectionStatus.CONNECTED) {
                                val latest = repository.getHistoricalCandles(symbol, tf, pType)
                                if (latest.isNotEmpty()) {
                                    handleCandleUpdate(latest.last())
                                }
                            }
                        } catch (e: Exception) {
                            // ignore transient network hiccup
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    private fun handleCandleUpdate(updatedCandle: Candle) {
        val currentCandles = _uiState.value.candles.toMutableList()
        if (currentCandles.isNotEmpty() && currentCandles.last().time == updatedCandle.time) {
            currentCandles[currentCandles.size - 1] = updatedCandle
        } else if (currentCandles.isNotEmpty() && updatedCandle.time > currentCandles.last().time) {
            currentCandles.add(updatedCandle)
        } else if (currentCandles.isEmpty()) {
            currentCandles.add(updatedCandle)
        }
        _uiState.value = _uiState.value.copy(candles = currentCandles)
        checkAlerts(updatedCandle)
    }

    // Drawings
    fun setActiveDrawingTool(tool: DrawingType?) {
        _uiState.value = _uiState.value.copy(activeDrawingTool = tool)
    }

    fun addDrawing(drawing: Drawing) {
        _uiState.value = _uiState.value.copy(
            drawings = _uiState.value.drawings + drawing,
            activeDrawingTool = null,
            selectedDrawingId = drawing.id
        )
    }

    fun updateDrawing(drawing: Drawing) {
        _uiState.value = _uiState.value.copy(
            drawings = _uiState.value.drawings.map { if (it.id == drawing.id) drawing else it }
        )
    }

    fun deleteDrawing(id: String) {
        _uiState.value = _uiState.value.copy(
            drawings = _uiState.value.drawings.filter { it.id != id },
            selectedDrawingId = null
        )
    }

    fun selectDrawing(id: String?) {
        _uiState.value = _uiState.value.copy(selectedDrawingId = id)
    }

    fun clearAllDrawings() {
        _uiState.value = _uiState.value.copy(drawings = emptyList(), selectedDrawingId = null)
    }

    // Customizable Alerts
    fun addAlert(
        symbol: String,
        targetPrice: Float,
        condition: AlertCondition,
        soundEnabled: Boolean = true,
        vibrateEnabled: Boolean = true
    ) {
        val alert = PriceAlert(
            id = UUID.randomUUID().toString(),
            symbol = symbol,
            targetPrice = targetPrice,
            condition = condition,
            soundEnabled = soundEnabled,
            vibrateEnabled = vibrateEnabled,
            isEnabled = true,
            isTriggered = false
        )
        val updated = _uiState.value.alerts + alert
        _uiState.value = _uiState.value.copy(alerts = updated)
        saveAlertsToDisk(updated)
    }

    fun toggleAlertEnabled(id: String) {
        val updated = _uiState.value.alerts.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled, isTriggered = false) else it
        }
        _uiState.value = _uiState.value.copy(alerts = updated)
        saveAlertsToDisk(updated)
    }

    fun updateAlert(updatedAlert: PriceAlert) {
        val updated = _uiState.value.alerts.map {
            if (it.id == updatedAlert.id) updatedAlert else it
        }
        _uiState.value = _uiState.value.copy(alerts = updated)
        saveAlertsToDisk(updated)
    }

    fun deleteAlert(id: String) {
        val updated = _uiState.value.alerts.filter { it.id != id }
        _uiState.value = _uiState.value.copy(alerts = updated)
        saveAlertsToDisk(updated)
    }

    private fun checkAlerts(candle: Candle) {
        val currentPrice = candle.close
        val prev = previousPrice ?: currentPrice
        val currentSymbol = _uiState.value.symbol

        var updatedAlerts = false
        val newAlertList = _uiState.value.alerts.map { alert ->
            if (alert.symbol == currentSymbol && alert.isEnabled && !alert.isTriggered) {
                val isTriggered = when (alert.condition) {
                    AlertCondition.CROSSES_ABOVE -> prev <= alert.targetPrice && currentPrice >= alert.targetPrice
                    AlertCondition.CROSSES_BELOW -> prev >= alert.targetPrice && currentPrice <= alert.targetPrice
                    AlertCondition.CANDLE_CLOSES_ABOVE -> candle.close >= alert.targetPrice
                    AlertCondition.CANDLE_CLOSES_BELOW -> candle.close <= alert.targetPrice
                }
                if (isTriggered) {
                    updatedAlerts = true
                    if (alert.vibrateEnabled) {
                        triggerDeviceVibration(getApplication())
                    }
                    if (alert.soundEnabled) {
                        playAlertSound(getApplication())
                    }
                    alert.copy(isTriggered = true)
                } else {
                    alert
                }
            } else {
                alert
            }
        }

        previousPrice = currentPrice

        if (updatedAlerts) {
            _uiState.value = _uiState.value.copy(alerts = newAlertList)
            saveAlertsToDisk(newAlertList)
        }
    }

    fun triggerDeviceVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(350)
            }
        } catch (e: Exception) {
            // ignore vibration error
        }
    }

    private fun playAlertSound(context: Context) {
        try {
            // Use the alarm stream at full ToneGenerator level for a much more noticeable alert.
            val generator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 850)
            Handler(Looper.getMainLooper()).postDelayed({
                try { generator.release() } catch (_: Exception) { }
            }, 1000L)
        } catch (e: Exception) {
            android.util.Log.e("TradingApp", "Unable to play alert tone", e)
        }
    }
}
