# CTV Ads SDK for Android

`ctvads` — лёгкий SDK для показа **видео (VAST)** и **баннерной** рекламы на
Android / Android TV, получаемой **напрямую** от OpenRTB 2.5 биддера (без Prebid
Server). SDK сам собирает `BidRequest`, ходит в биддер, парсит `BidResponse` и
рендерит выигравший креатив (HTML — в `WebView`, VAST — через ExoPlayer).

Эта страница — **как подключить SDK**. Как им пользоваться по форматам —
см. **[docs/INTEGRATION.md](docs/INTEGRATION.md)**.

- [SDK Integration](#sdk-integration)
  - [As a Gradle module](#as-a-gradle-module-source)
  - [As an AAR](#as-an-aar)
- [Update your Android manifest](#update-your-android-manifest)
- [Initialize the SDK](#initialize-the-sdk)
- [Supported Android versions](#supported-android-versions)
- [Module structure](#module-structure)
- [Ad formats](#ad-formats)
- [Building the demo app](#building-the-demo-app)

---

## SDK Integration

Подключить `ctvads` можно двумя способами: как Gradle-модуль (исходники) или как
собранный AAR.

### As a Gradle module (source)

Скопируйте папку `ctvads/` в проект и подключите модуль в `settings.gradle.kts`:

```kotlin
include(":ctvads")
```

В `build.gradle.kts` приложения:

```kotlin
dependencies {
    implementation(project(":ctvads"))
}
```

### As an AAR

Соберите артефакт:

```bash
./gradlew :ctvads:assembleRelease
# результат: ctvads/build/outputs/aar/ctvads-release.aar
```

Положите `.aar` в `app/libs/` и добавьте его вместе с транзитивными
зависимостями (AAR их не тянет):

```kotlin
dependencies {
    implementation(files("libs/ctvads-release.aar"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("com.google.zxing:core:3.5.3") // QR-фолбэк для ClickThrough
}
```

> ProGuard/R8: модуль поставляет `consumer-rules.pro` (keep для OpenRTB-моделей),
> правила применяются автоматически — ничего добавлять не нужно.

---

## Update your Android manifest

Модуль уже объявляет `INTERNET`, `ACCESS_NETWORK_STATE`,
`com.google.android.gms.permission.AD_ID` и полноэкранную `InterstitialActivity`
(регистрировать её в приложении не нужно).

Для **Android TV** объявите форм-фактор и leanback-лаунчер:

```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.software.leanback" android:required="true" />

<application android:name=".MyApp" ...>
    <activity android:name=".MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

**Cleartext (HTTP) для локальной разработки.** При `targetSdk 34` HTTP блокируется.
Если биддер/потоки по HTTP — добавьте `res/xml/network_security_config.xml` и
укажите его в `<application android:networkSecurityConfig="@xml/network_security_config">`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">your-bidder.local</domain>
    </domain-config>
</network-security-config>
```

В продакшене биддер должен работать по HTTPS.

---

## Initialize the SDK

Один раз при старте приложения (обычно в `Application`, на главном потоке).
Указывается только **host** биддера — адреса запросов SDK строит сам:
`host/ads` (аукцион) и `host/status` (проверка статуса).

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CtvAds.initialize(
            context = this,
            config = CtvAdsConfig(
                host = "your-bidder.example.com", // можно с http/https и портом
                appName = "My CTV App",
                publisherId = "pub-123",
                test = BuildConfig.DEBUG,          // test=1 в BidRequest
            ),
        ) { result ->
            // result.status: ACTIVE / STOPPED / UNKNOWN; result.version — версия биддера
            Log.i("Ads", "init: ${result.status} ${result.version}")
        }
    }
}
```

При инициализации SDK дёргает `host/status` и ожидает JSON
`{"status":"active|stopped","version":"0.0111"}`. Если статус `stopped` — запросы
к биддеру блокируются (`CtvAds.canRequestAds() == false`, загрузка вернёт
`AdError.Stopped`). Параметры `CtvAdsConfig` описаны в
[docs/INTEGRATION.md](docs/INTEGRATION.md#configuration).

---

## Supported Android versions

| Параметр | Значение |
|---|---|
| `minSdk` | 21 |
| `compileSdk` / `targetSdk` | 34 |
| JDK | 17 |
| Язык | Kotlin (нужны корутины для `loadAd(scope, …)`) |

---

## Module structure

```
ctvads/
├── CtvAds / CtvAdsConfig        — точка входа, инициализация, /status, конфиг
├── AdListener / AdCallbacks     — колбэки жизненного цикла и трекинга
├── AdError / AdTrackingEvent    — типы ошибок и событий
├── AdSize                       — пресеты размеров (BANNER, MREC, …)
├── InterstitialAd               — полноэкранная реклама (video/banner)
├── view/
│   ├── BannerAdView             — HTML-баннер в WebView
│   ├── VideoAdView              — VAST-видео в ExoPlayer
│   ├── InterstitialActivity     — полноэкранная поверхность (внутренняя)
│   └── ClickThrough             — открытие ClickThrough + QR-фолбэк
├── vast/                        — VAST 2/3/4 парсер + резолвер wrapper'ов
├── core/                        — OpenRtbClient, StatusClient, BidRequestFactory, DeviceInfo
└── openrtb/                     — модели OpenRTB 2.5 (BidRequest / BidResponse)
```

---

## Ad formats

Все форматы и полный API — в **[docs/INTEGRATION.md](docs/INTEGRATION.md)**:

| Формат | Класс | Документация |
|---|---|---|
| Баннер (HTML) | `BannerAdView` | [Banner](docs/INTEGRATION.md#banner) |
| Видео (VAST) | `VideoAdView` | [Video](docs/INTEGRATION.md#video-vast) |
| Полноэкранная (interstitial) | `InterstitialAd` | [Interstitial](docs/INTEGRATION.md#interstitial) |

MRAID и VPAID не поддерживаются.

---

## Building the demo app

Демо-приложение **«CTV Demo»** (модуль `app`) показывает интеграцию: каталог
телеканалов/фильмов, баннер поверх live-потока и пре-ролл видео.

```bash
# 1. Укажите host биддера в app/.../AdConfig.kt
#    const val BIDDER_HOST = "http://10.0.2.2:8080"

# 2. Соберите и установите
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# Локальный биддер через эмулятор:
adb reverse tcp:8080 tcp:8080
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Требования: JDK 17, Android SDK
Platform 34, build-tools 34 (Gradle ставится через wrapper).
