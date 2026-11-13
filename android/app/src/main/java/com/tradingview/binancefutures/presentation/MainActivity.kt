package com.tradingview.binancefutures.presentation

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import com.google.firebase.messaging.FirebaseMessaging
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradingview.binancefutures.domain.model.AlertCondition
import com.tradingview.binancefutures.domain.model.ConnectionStatus
import com.tradingview.binancefutures.domain.model.Timeframe
import com.tradingview.binancefutures.presentation.ui.alerts.AlertsDialogCompose
import com.tradingview.binancefutures.presentation.ui.chart.TradingViewLightweightChart
import com.tradingview.binancefutures.presentation.ui.drawing.DrawingToolbarCompose
import com.tradingview.binancefutures.presentation.ui.header.TradingHeader
import com.tradingview.binancefutures.presentation.ui.theme.*
import com.tradingview.binancefutures.presentation.ui.timeframe.CustomTimeframeDialog
import com.tradingview.binancefutures.presentation.ui.watchlist.WatchlistDialogCompose
import com.tradingview.binancefutures.presentation.ui.watchlist.WatchlistScreen
import com.tradingview.binancefutures.presentation.viewmodel.ChartViewModel

class MainActivity : ComponentActivity() {

    private fun registerPushToken() {
        val prefs = getSharedPreferences("tradingview_binance_prefs", MODE_PRIVATE)
        val deviceId = prefs.getString("push_device_id", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("push_device_id", it).apply()
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Thread {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL("https://jolly-hall-eb84suraj-trading-alerts.surajali1026.workers.dev/api/device/register").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 10000
                        readTimeout = 10000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                    val body = """{"deviceId":"$deviceId","token":"$token","platform":"android"}"""
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    android.util.Log.i("TradingFCM", "Push token registration HTTP ${connection.responseCode}")
                } catch (error: Exception) {
                    android.util.Log.e("TradingFCM", "Push token registration failed", error)
                } finally {
                    connection?.disconnect()
                }
            }.start()
        }.addOnFailureListener { error ->
            android.util.Log.e("TradingFCM", "Could not get FCM token", error)
        }
    }

    private fun toggleScreenOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPushToken()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("TradingApp", "Uncaught exception on thread ${thread.name}", throwable)
        }
        setContent {
            TradingViewTheme {
                val viewModel: ChartViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return ChartViewModel(application) as T
                        }
                    }
                )
                val uiState by viewModel.uiState.collectAsState()

                var isWatchlistOpen by remember { mutableStateOf(false) }
                var isAlertsOpen by remember { mutableStateOf(false) }
                var isCustomTfOpen by remember { mutableStateOf(false) }
                var isFullScreen by remember { mutableStateOf(false) }
                var alertDraftPrice by remember { mutableFloatStateOf(0f) }
                var selectedMainTab by remember { mutableStateOf("CHART") }
                val chartPrefs = remember { getSharedPreferences("tradingview_binance_prefs", MODE_PRIVATE) }
                var favoriteTimeframes by remember {
                    mutableStateOf(
                        chartPrefs.getStringSet("favorite_timeframes", null)
                            ?.mapNotNull { Timeframe.fromInterval(it) }?.toSet()
                            ?: setOf(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1)
                    )
                }

                LaunchedEffect(isFullScreen) {
                    val bars = WindowInsetsControllerCompat(window, window.decorView)
                    if (isFullScreen) {
                        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        bars.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        bars.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                val currentPair = remember(uiState.pairs, uiState.symbol) {
                    uiState.pairs.firstOrNull { it.symbol == uiState.symbol }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TVBackground
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (selectedMainTab == "WATCHLIST") {
                            WatchlistScreen(
                                pairs = uiState.pairs,
                                currentSymbol = uiState.symbol,
                                favorites = uiState.favorites,
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onSelectSymbol = { viewModel.selectSymbol(it); selectedMainTab = "CHART" },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                        // 1. Top Header (Hidden in Full-Screen mode to maximize chart space)
                        if (!isFullScreen) {
                            TradingHeader(
                                symbol = uiState.symbol,
                                currentPair = currentPair,
                                timeframe = uiState.timeframe,
                                priceType = uiState.priceType,
                                favoriteTimeframes = favoriteTimeframes.sortedBy { it.durationMs },
                                alertsCount = uiState.alerts.filter { it.symbol == uiState.symbol && it.isEnabled && !it.isTriggered }.size,
                                isFullScreen = false,
                                onOpenWatchlist = { selectedMainTab = "WATCHLIST" },
                                onSelectTimeframe = { viewModel.selectTimeframe(it) },
                                onOpenCustomTimeframe = { isCustomTfOpen = true },
                                onSelectPriceType = { viewModel.setPriceType(it) },
                                onOpenAlerts = {
                                    alertDraftPrice = uiState.candles.lastOrNull()?.close ?: 0f
                                    isAlertsOpen = true
                                },
                                onToggleFullScreen = { isFullScreen = true },
                                onToggleOrientation = { toggleScreenOrientation() }
                            )
                        }

                        // 2. Main Chart Workspace
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            // Candlestick Chart with alert dotted lines and swipe coin navigation
                            TradingViewLightweightChart(
                                candles = uiState.candles,
                                symbol = uiState.symbol,
                                timeframe = uiState.timeframe.displayName,
                                priceType = uiState.priceType.name,
                                alerts = uiState.alerts.filter { it.symbol == uiState.symbol && it.isEnabled && !it.isTriggered },
                                onPriceSelected = { price ->
                                    alertDraftPrice = price
                                    isAlertsOpen = true
                                },
                                onNextCoin = { viewModel.selectNextCoin() },
                                onPreviousCoin = { viewModel.selectPreviousCoin() },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Left Auto-Hide Drawing Toolbar (Always accessible)
                            DrawingToolbarCompose(
                                activeDrawingTool = uiState.activeDrawingTool,
                                onSelectTool = { viewModel.setActiveDrawingTool(it) },
                                onClearAll = { viewModel.clearAllDrawings() },
                                drawingsCount = uiState.drawings.size,
                                drawings = uiState.drawings,
                                onToggleLock = { viewModel.updateDrawing(it.copy(isLocked = !it.isLocked)) },
                                onDeleteDrawing = { viewModel.deleteDrawing(it) },
                                modifier = Modifier.align(Alignment.CenterStart)
                            )

                            // Full-Screen Floating Minimal Control Bar (Only shown in Full-Screen mode)
                            if (isFullScreen) {
                                Surface(
                                    color = TVSurface.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, TVBorder),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Watchlist Button
                                            IconButton(
                                                onClick = { selectedMainTab = "WATCHLIST" },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.FormatListBulleted, contentDescription = "Watchlist", tint = TVPrimaryBlue, modifier = Modifier.size(16.dp))
                                            }

                                            Text(
                                                text = uiState.symbol,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Surface(
                                                onClick = { isCustomTfOpen = true },
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF26364A),
                                                modifier = Modifier.height(22.dp)
                                            ) {
                                                Box(modifier = Modifier.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                                                    Text("Custom", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Horizontal Timeframe Row inside Fullscreen
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                favoriteTimeframes.sortedBy { it.durationMs }.forEach { tf ->
                                                    val isSel = tf == uiState.timeframe
                                                    Surface(
                                                        onClick = { viewModel.selectTimeframe(tf) },
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isSel) TVPrimaryBlue else Color.Transparent,
                                                        modifier = Modifier.height(22.dp)
                                                    ) {
                                                        Box(modifier = Modifier.padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                                                            Text(text = tf.displayName, color = if (isSel) Color.White else TVTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Exit Full-Screen Button
                                            IconButton(
                                                onClick = { isFullScreen = false },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = TVPrimaryBlue, modifier = Modifier.size(18.dp))
                                            }

                                            // Screen Rotation
                                            IconButton(
                                                onClick = { toggleScreenOrientation() },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = TVTextSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                        }

                        // 3. Bottom Status Strip (Hidden in Full-Screen)
                        if (!isFullScreen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TVSurface)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusColor = when (uiState.connectionStatus) {
                                    ConnectionStatus.CONNECTED -> TVBullishGreen
                                    ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Color(0xFFFF9800)
                                    ConnectionStatus.ERROR -> TVBearishRed
                                }
                                Text(
                                    text = "● ${uiState.connectionStatus.name} · ${uiState.priceType.name}",
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Drawings: ${uiState.drawings.size} · Alerts: ${uiState.alerts.size}",
                                    color = TVTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        }



                    if (!isFullScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(TVSurface).navigationBarsPadding().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(onClick = { selectedMainTab = "WATCHLIST" }, color = if (selectedMainTab == "WATCHLIST") TVPrimaryBlue.copy(alpha = 0.18f) else Color.Transparent, shape = RoundedCornerShape(10.dp)) {
                                Column(Modifier.padding(horizontal = 34.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Star, contentDescription = "Watchlist", tint = if (selectedMainTab == "WATCHLIST") TVPrimaryBlue else TVTextSecondary)
                                    Text("Watchlist", color = if (selectedMainTab == "WATCHLIST") Color.White else TVTextSecondary, fontSize = 10.sp)
                                }
                            }
                            Surface(onClick = { selectedMainTab = "CHART" }, color = if (selectedMainTab == "CHART") TVPrimaryBlue.copy(alpha = 0.18f) else Color.Transparent, shape = RoundedCornerShape(10.dp)) {
                                Column(Modifier.padding(horizontal = 34.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ShowChart, contentDescription = "Chart", tint = if (selectedMainTab == "CHART") TVPrimaryBlue else TVTextSecondary)
                                    Text("Chart", color = if (selectedMainTab == "CHART") Color.White else TVTextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    }

                    // 1-Click Slide-Out Watchlist Drawer
                    WatchlistDialogCompose(
                        isOpen = isWatchlistOpen,
                        onDismiss = { isWatchlistOpen = false },
                        pairs = uiState.pairs,
                        currentSymbol = uiState.symbol,
                        favorites = uiState.favorites,
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onSelectSymbol = { viewModel.selectSymbol(it) }
                    )

                    // Customizable Price Alerts Dialog
                    AlertsDialogCompose(
                        isOpen = isAlertsOpen,
                        onDismiss = { isAlertsOpen = false },
                        symbol = uiState.symbol,
                        currentPrice = uiState.candles.lastOrNull()?.close ?: 0f,
                        initialTargetPrice = alertDraftPrice.takeIf { it > 0f },
                        alerts = uiState.alerts.filter { it.symbol == uiState.symbol },
                        onAddAlert = { price, cond, sound, vibrate ->
                            viewModel.addAlert(uiState.symbol, price, cond, sound, vibrate)
                        },
                        onTestSound = { viewModel.testAlertSound() },
                        onToggleAlert = { viewModel.toggleAlertEnabled(it) },
                        onDeleteAlert = { viewModel.deleteAlert(it) }
                    )

                    // Custom Timeframe Dialog
                    CustomTimeframeDialog(
                        isOpen = isCustomTfOpen,
                        onDismiss = { isCustomTfOpen = false },
                        currentTimeframe = uiState.timeframe,
                        onSelectTimeframe = {
                            viewModel.selectTimeframe(it)
                            favoriteTimeframes = favoriteTimeframes + it
                            chartPrefs.edit().putStringSet("favorite_timeframes", favoriteTimeframes.map { tf -> tf.interval }.toSet()).apply()
                        }
                    )
                }
            }
        }
    }
}
