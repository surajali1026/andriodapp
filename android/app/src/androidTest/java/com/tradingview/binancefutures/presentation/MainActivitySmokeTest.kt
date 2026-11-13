package com.tradingview.binancefutures.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import com.tradingview.binancefutures.data.nativeengine.NativeChartEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun mainActivityLaunchesAndRendersWindow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("MainActivity should remain active", !activity.isFinishing)
                assertNotNull("Activity window should be created", activity.window)
                assertNotNull("Activity decor view should be created", activity.window.decorView)
            }
        }
    }

    @Test
    fun nativeChartEngineLoadsAndValidatesOhlc() {
        assertTrue("Native chart library should load on emulator ABI", NativeChartEngine.isAvailable())
        assertNotNull("Native engine should report its version", NativeChartEngine.engineVersion())
        assertTrue("Valid OHLC data should pass native validation",
            NativeChartEngine.validateOhlc(100f, 110f, 90f, 105f))
        assertTrue("Invalid OHLC data should be rejected",
            !NativeChartEngine.validateOhlc(100f, 90f, 110f, 105f))
    }
    @Test
    fun lightweightChartsLibraryAndPriceLevelAlertButtonAreBundled() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val libraryBytes = activity.assets.open("lightweight-charts.standalone.production.js").use {
                    it.readBytes()
                }
                assertTrue("TradingView Lightweight Charts asset must be bundled locally", libraryBytes.size > 100_000)

                val html = activity.assets.open("tradingview_chart.html").bufferedReader().use { it.readText() }
                assertTrue("Chart must load the bundled local library", html.contains("lightweight-charts.standalone.production.js"))
                assertTrue("Chart must expose the price-level alert plus button", html.contains("id=\"pricePlus\""))
                assertTrue("Chart plus button must call the Android alert bridge", html.contains("createAlertAtPrice"))
            }
        }
    }

}
