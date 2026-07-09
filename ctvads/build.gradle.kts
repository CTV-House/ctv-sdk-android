plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.ctvhouse.ctvads"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Advertising ID (GAID / IFA) resolution on devices with Play Services.
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking (OpenRTB 2.5 transport, direct-to-bidder)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON (OpenRTB models)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Video ad playback (VAST rendering)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // QR code fallback for ClickThrough when the TV has no browser.
    implementation("com.google.zxing:core:3.5.3")

    // NOTE (Variant A): if the bidder is exposed as a Prebid-Server-compatible
    // host, add the Prebid Rendering SDK and use Host.CUSTOM instead of the
    // built-in OpenRtbClient:
    // implementation("org.prebid:prebid-mobile-sdk:2.2.3")
}
