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

private class ChartAlertBridge(
    private val onPriceSelected: (Float) -> Unit,
    private val onNextCoin: () -> Unit,
    private val onPreviousCoin: () -> Unit
) {
    @JavascriptInterface
    fun navigateCoin(direction: String) {
        Handler(Looper.getMainLooper()).post {
            if (direction == "next") onNextCoin() else if (direction == "previous") onPreviousCoin()
        }
    }

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
    onNextCoin: () -> Unit = {},
    onPreviousCoin: () -> Unit = {},
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
    val currentOnNextCoin = rememberUpdatedState(onNextCoin)
    val currentOnPreviousCoin = rememberUpdatedState(onPreviousCoin)
    val currentSymbol = rememberUpdatedState(symbol)
    val currentTimeframe = rememberUpdatedState(timeframe)
    val currentPriceType = rememberUpdatedState(priceType)
    val bridge = remember {
        ChartAlertBridge(
            onPriceSelected = { price -> currentOnPriceSelected.value(price) },
            onNextCoin = { currentOnNextCoin.value() },
            onPreviousCoin = { currentOnPreviousCoin.value() }
        )
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
                // The bundled lightweight chart loads its library over HTTPS.
                // Keep local-file cross-origin access disabled; only the public CDN is needed.
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                addJavascriptInterface(bridge, "AndroidAlert")
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        val safeSymbol = currentSymbol.value.replace("'", "")
                        view.evaluateJavascript(
                            "window.setSymbolInfo && window.setSymbolInfo('$safeSymbol','" + currentTimeframe.value + "');" +
                                "window.setCandles && window.setCandles(${currentPayload.value});" +
                                "window.setAlerts && window.setAlerts(${currentAlertPayload.value});",
                            null
                        )
                    }
                }
                loadUrl("file:///android_asset/tradingview_chart.html")
            }
        },
        update = { webView ->
            val safeSymbol = symbol.replace("'", "")
            webView.evaluateJavascript(
                "window.setSymbolInfo && window.setSymbolInfo('$safeSymbol','$timeframe');" +
                    "window.setCandles && window.setCandles($payload);" +
                    "window.setAlerts && window.setAlerts($alertPayload);",
                null
            )
        }
    )
}
