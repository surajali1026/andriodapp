package com.tradingview.binancefutures.presentation.ui.chart

import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.tradingview.binancefutures.domain.model.Candle
import com.tradingview.binancefutures.domain.model.PriceAlert

private class ChartAlertBridge(private val onPriceSelected: (Float) -> Unit) {
    @JavascriptInterface
    fun createAlertAtPrice(price: Double) {
        if (price.isFinite() && price > 0.0 && price <= Float.MAX_VALUE) {
            Handler(Looper.getMainLooper()).post { onPriceSelected(price.toFloat()) }
        }
    }
}

@Composable
fun TradingViewLightweightChart(
    candles: List<Candle>,
    symbol: String,
    timeframe: String,
    priceType: String,
    alerts: List<PriceAlert> = emptyList(),
    onPriceSelected: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gson = remember { Gson() }
    val payload = gson.toJson(candles.takeLast(500).map {
        mapOf(
            "time" to it.time,
            "open" to it.open,
            "high" to it.high,
            "low" to it.low,
            "close" to it.close
        )
    })
    val alertPayload = gson.toJson(alerts.filter { it.isEnabled && !it.isTriggered }.map {
        mapOf("id" to it.id, "price" to it.targetPrice)
    })
    val currentPayload = rememberUpdatedState(payload)
    val currentAlertPayload = rememberUpdatedState(alertPayload)
    val currentOnPriceSelected = rememberUpdatedState(onPriceSelected)
    val currentSymbol = rememberUpdatedState(symbol)
    val currentTimeframe = rememberUpdatedState(timeframe)
    val currentPriceType = rememberUpdatedState(priceType)
    val bridge = remember {
        ChartAlertBridge { price -> currentOnPriceSelected.value(price) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.rgb(11, 14, 17))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.loadsImagesAutomatically = true
                // TradingView's hosted widget and market-data iframe must be reachable from this bundled local HTML page.
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                addJavascriptInterface(bridge, "AndroidAlert")
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        // The embedded TradingView widget owns its own live candle feed.
                        // Do not inject legacy Binance candle payloads into this chart.
                    }
                }
                loadUrl("file:///android_asset/tradingview_chart.html")
            }
        },
        update = { webView ->
            // TradingView's built-in symbol and timeframe controls manage the chart.
            // The old Lightweight Charts bridge expected setCandles(), which this widget does not expose.
        }
    )
}
