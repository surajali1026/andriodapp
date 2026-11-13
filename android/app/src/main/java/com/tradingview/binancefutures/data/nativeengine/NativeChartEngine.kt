package com.tradingview.binancefutures.data.nativeengine

/** JNI entry point for the native chart renderer. The existing Kotlin chart remains active
 * until the native renderer has passed device-level performance and stability tests.
 */
object NativeChartEngine {
    private val loaded: Boolean = try {
        System.loadLibrary("chart_engine")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    private external fun nativeEngineVersion(): String
    private external fun nativeValidateOhlc(open: Float, high: Float, low: Float, close: Float): Boolean

    fun isAvailable(): Boolean = loaded

    fun engineVersion(): String? = if (loaded) nativeEngineVersion() else null

    fun validateOhlc(open: Float, high: Float, low: Float, close: Float): Boolean =
        loaded && nativeValidateOhlc(open, high, low, close)
}
