package com.tradingview.binancefutures.presentation.ui.chart

import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.tradingview.binancefutures.domain.model.Candle
import kotlinx.coroutines.delay

@Composable
fun TradingViewLightweightChart(
    candles: List<Candle>,
    symbol: String,
    timeframe: String,
    priceType: String,
    modifier: Modifier = Modifier
) {
    val gson = Gson()
    val payload = gson.toJson(candles.takeLast(500).map {
        mapOf(
            "time" to it.time,
            "open" to it.open,
            "high" to it.high,
            "low" to it.low,
            "close" to it.close
        )
    })
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.rgb(11, 14, 17))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.loadsImagesAutomatically = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript("window.setSymbolInfo && window.setSymbolInfo('$symbol','$timeframe','$priceType');", null)
                        view.evaluateJavascript("window.setCandles && window.setCandles($payload);", null)
                    }
                }
                loadUrl("file:///android_asset/tradingview_chart.html")
            }
        },
        update = { webView ->
            val safeSymbol = symbol.replace("'", "")
            val safeTimeframe = timeframe.replace("'", "")
            val safePriceType = priceType.replace("'", "")
            webView.evaluateJavascript(
                "window.setSymbolInfo && window.setSymbolInfo('$safeSymbol','$safeTimeframe','$safePriceType');window.setCandles && window.setCandles($payload);",
                null
            )
        }
    )
}
