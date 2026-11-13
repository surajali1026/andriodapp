#include <jni.h>
#include <cmath>

extern "C" JNIEXPORT jstring JNICALL
Java_com_tradingview_binancefutures_data_nativeengine_NativeChartEngine_nativeEngineVersion(
    JNIEnv* env,
    jobject /* thiz */) {
    return env->NewStringUTF("native-chart-engine-0.1.0");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tradingview_binancefutures_data_nativeengine_NativeChartEngine_nativeValidateOhlc(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jfloat open,
    jfloat high,
    jfloat low,
    jfloat close) {
    const bool finite = std::isfinite(open) && std::isfinite(high) &&
                        std::isfinite(low) && std::isfinite(close);
    return (finite && high >= low && high >= open && high >= close &&
            low <= open && low <= close) ? JNI_TRUE : JNI_FALSE;
}
