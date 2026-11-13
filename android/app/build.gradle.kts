plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.tradingview.binancefutures"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.tradingview.binancefutures"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines & Networking (OkHttp for Binance WebSocket & REST)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


val lightweightChartsAsset = layout.projectDirectory.file("src/main/assets/lightweight-charts.standalone.production.js")
val downloadLightweightCharts by tasks.registering {
    description = "Bundle TradingView Lightweight Charts 4.2.2 for offline chart rendering"
    outputs.file(lightweightChartsAsset)
    doLast {
        val output = lightweightChartsAsset.asFile
        if (!output.exists() || output.length() < 100_000) {
            output.parentFile.mkdirs()
            val url = java.net.URI(
                "https://unpkg.com/lightweight-charts@4.2.2/dist/lightweight-charts.standalone.production.js"
            ).toURL()
            val connection = url.openConnection().apply {
                connectTimeout = 30000
                readTimeout = 60000
            }
            val temp = java.io.File(output.parentFile, output.name + ".tmp")
            connection.getInputStream().use { input ->
                temp.outputStream().use { out -> input.copyTo(out) }
            }
            check(temp.length() > 100_000) { "TradingView library download incomplete" }
            java.nio.file.Files.move(
                temp.toPath(), output.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadLightweightCharts) }
