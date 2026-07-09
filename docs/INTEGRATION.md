# CTV Ads SDK — Integration

Руководство по использованию `ctvads`: инициализация, конфигурация и работа с
каждым рекламным форматом. Как **подключить** SDK (Gradle/AAR, манифест,
supported versions) — см. [README](../README.md#sdk-integration).

- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Ad units](#ad-units)
  - [Banner](#banner)
  - [Video (VAST)](#video-vast)
  - [Interstitial](#interstitial)
- [Live TV overlay](#live-tv-overlay)
- [Callbacks and errors](#callbacks-and-errors)
- [Ad sizes](#ad-sizes)
- [Ad marking (ЕРИД / nroa)](#ad-marking-ерид--nroa)
- [Android TV notes](#android-tv-notes)
- [Bidder contract](#bidder-contract)
- [API reference](#api-reference)

---

## Prerequisites

1. SDK подключён (см. [README](../README.md#sdk-integration)).
2. `CtvAds.initialize(...)` вызван при старте приложения.
3. Для загрузки рекламы нужен `CoroutineScope` (например, `lifecycleScope`
   у `FragmentActivity`/`Fragment`).

---

## Configuration

`CtvAds.initialize(context, config, listener?)` инициализирует SDK и асинхронно
проверяет `host/status`. Указывается только **host** — эндпоинты `host/ads` и
`host/status` строятся автоматически.

```kotlin
CtvAds.initialize(
    context = this,
    config = CtvAdsConfig(
        host = "your-bidder.example.com",
        appName = "My CTV App",
        publisherId = "pub-123",
        timeoutMs = 1500,
        test = BuildConfig.DEBUG,
    ),
) { result ->
    Log.i("Ads", "status=${result.status} version=${result.version}")
}
```

`CtvAdsConfig`:

| Поле | Обяз. | Описание |
|---|---|---|
| `host` | да | Хост биддера (→ `host/ads`, `host/status`). Схема необязательна, по умолчанию `http://` |
| `appName` | да | `BidRequest.app.name` |
| `appBundle` | нет | по умолчанию = `packageName` |
| `appStoreUrl` | нет | `BidRequest.app.storeurl` |
| `appDomain` | нет | `BidRequest.app.domain` |
| `publisherId` | нет | `BidRequest.app.publisher.id` |
| `timeoutMs` | нет | таймаут аукциона (по умолчанию 1500) |
| `test` | нет | `BidRequest.test = 1` (без биллинга) |

**Проверка статуса.** SDK пингует `host/status`, ожидая
`{"status":"active|stopped","version":"…"}`, и передаёт результат в
`InitListener` (главный поток):

| Свойство | Описание |
|---|---|
| `CtvAds.status` | `ACTIVE` / `STOPPED` / `UNKNOWN` |
| `CtvAds.bidderVersion` | версия биддера из `/status` |
| `CtvAds.canRequestAds()` | `false`, если биддер `stopped` |

Если статус `stopped`, любая загрузка вернёт `AdError.Stopped` и запрос к `/ads`
не отправляется. При сетевой ошибке `/status` статус — `UNKNOWN`, запросы
разрешены (fail-open).

---

## Ad units

### Banner

HTML-креатив (`bid.adm`) в `WebView`.

```xml
<com.ctvhouse.ctvads.view.BannerAdView
    android:id="@+id/bannerAd"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:visibility="gone" />
```

```kotlin
class BannerScreen : FragmentActivity() { // FragmentActivity даёт lifecycleScope
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_banner)

        val banner = findViewById<BannerAdView>(R.id.bannerAd)
        banner.listener = AdCallbacks(
            onRendered = { bid ->
                banner.visibility = View.VISIBLE
                banner.requestFocus() // фокус под D-pad
            },
            onFailed = { error -> Log.w("Ads", "Banner failed: ${error.message}") },
            onClicked = { bid -> /* аналитика */ },
        )

        banner.loadAd(lifecycleScope, AdSize.MREC)
    }
}
```

Размер слота автоматически подстраивается под `w`/`h` вернувшегося креатива.

**Фиксированный слот** (например, оверлей 320×50, независимо от ответа биддера):

```kotlin
banner.loadAd(lifecycleScope, AdSize.BANNER, fixedSize = true)
// либо заранее: banner.fixedSlotSize = AdSize.BANNER
```

**Ручной рендер** уже полученного `Bid` (если аукцион вызываете сами):

```kotlin
banner.render(bid)
```

### Video (VAST)

Линейный VAST 2/3/4 через ExoPlayer, с разворачиванием **wrapper** до 5 уровней.

```xml
<com.ctvhouse.ctvads.view.VideoAdView
    android:id="@+id/videoAd"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone" />
```

```kotlin
val video = findViewById<VideoAdView>(R.id.videoAd)
video.listener = AdCallbacks(
    onRendered = { video.visibility = View.VISIBLE },
    onFailed = { finish() },
    onCompleted = { finish() }, // например, перейти к контенту
)
video.loadAd(lifecycleScope, AdSize(1920, 1080))
```

`VideoAdView`:

- запрашивает видео-бид, резолвит VAST (inline + wrapper);
- проигрывает прогрессивный `MediaFile` (MP4/webm);
- шлёт `burl`, impressions и трекинг квартилей (start / firstQuartile / midpoint /
  thirdQuartile / complete);
- D-pad **OK** = переход по `ClickThrough` + `ClickTracking`; на время перехода
  (QR-модалка / внешний браузер) видео **ставится на паузу** и возобновляется при
  возврате фокуса;
- поддерживает [маркировку](#ad-marking-ерид--nroa) `nroa_inform`.

Плеер освобождается по жизненному циклу View; вручную — `video.releasePlayer()`.

### Interstitial

Единый API `InterstitialAd` показывает креатив **на весь экран** во внутренней
активити SDK. Формат — через `AdFormat`:

- `AdFormat.VIDEO` — линейный VAST на весь экран (по завершении — `onAdCompleted`);
- `AdFormat.BANNER` — HTML-креатив по центру на непрозрачном фоне.

Сверху — **таймер обратного отсчёта** и D-pad фокусируемая кнопка «Пропустить»
(разблокируется через `skipOffsetSeconds`, по умолчанию 5с). Для баннера показ
длится `bannerDurationSeconds` (по умолчанию 15с), затем закрывается сам; для
видео таймер привязан к длительности ролика. После закрытия — `onAdDismissed()`
(для видео сначала `onAdCompleted`, при скипе — VAST-трекинг `skip`).

```kotlin
class HomeActivity : FragmentActivity() { // нужен lifecycleScope

    private fun showFullscreenAd(format: AdFormat) {
        // Листенеры задаются при создании и дополняют друг друга.
        val ad = InterstitialAd(this, AdCallbacks(
            onFailed = { error -> Log.w("Ads", "interstitial: ${error.message}") },
            onCompleted = { /* видео досмотрено */ },
            onDismissed = { /* экран закрыт, вернуть управление */ },
        ))
        ad.skipOffsetSeconds = 5                 // задержка кнопки «Пропустить»
        ad.bannerDurationSeconds = 15            // время показа fullscreen-баннера
        ad.setPlacementId("home_interstitial")   // → imp.tagid в bid request
        ad.load(lifecycleScope, format, autoShow = true) // показать сразу после загрузки
    }
}
```

Разделение `load` / `show` позволяет запросить заранее и показать в нужный момент:

```kotlin
val ad = InterstitialAd(context, myListener)
ad.load(lifecycleScope, AdFormat.VIDEO)   // заранее (autoShow не указываем)
// …позже:
if (ad.isReady) ad.show()
```

> Загруженный бид одноразовый: после `show()` нужно `load()` заново.

---

## Live TV overlay

Баннер поверх видеопотока (например, 320×50 внизу):

```xml
<FrameLayout android:layout_width="match_parent" android:layout_height="match_parent">

    <androidx.media3.ui.PlayerView
        android:id="@+id/player"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <com.ctvhouse.ctvads.view.BannerAdView
        android:id="@+id/overlayAd"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="24dp"
        android:visibility="gone" />
</FrameLayout>
```

```kotlin
// свой ExoPlayer с HLS-каналом на PlayerView …
overlayAd.listener = AdCallbacks(
    onRendered = { overlayAd.visibility = View.VISIBLE },
    onFailed = { overlayAd.visibility = View.GONE },
)
overlayAd.loadAd(lifecycleScope, AdSize.BANNER, fixedSize = true)
```

Для HLS добавьте `androidx.media3:media3-exoplayer-hls`, для DASH —
`media3-exoplayer-dash`.

---

## Callbacks and errors

`AdListener` — все вызовы на главном потоке, методы имеют пустую реализацию по
умолчанию (переопределяйте нужные):

| Метод | Когда |
|---|---|
| `onAdLoaded(bid)` | interstitial: бид загружен, можно `show()` |
| `onAdRendered(bid)` | креатив отрисован / видео стартовало |
| `onAdFailed(error)` | нет бида или ошибка рендера |
| `onAdClicked(bid)` | клик (D-pad OK) — **отдельно** от трекингов |
| `onAdCompleted(bid)` | видео завершилось |
| `onAdDismissed()` | interstitial: экран закрыт |
| `onTracking(event)` | трекинг: impression, viewable, видеоквартили и т.д. |

`AdTrackingEvent` (клик сюда **не** входит): `IMPRESSION`, `VIEWABLE`, `START`,
`FIRST_QUARTILE`, `MIDPOINT`, `THIRD_QUARTILE`, `COMPLETE`, `SKIP`.

```kotlin
banner.listener = AdCallbacks(
    onTracking = { event -> analytics.track(event) }, // impression, viewable, …
    onClicked = { bid -> analytics.click(bid) },       // клики отдельно
)
```

**`AdCallbacks`** — частичный листенер из лямбд, чтобы не реализовывать весь
интерфейс.

**Несколько листенеров: дополнение, а не замена.** Каждый получает все события
(SDK-трекинг отрабатывает первым). У баннера/видео — `listener` + `addListener()`;
у interstitial — `vararg` в конструкторе + `addListener()`:

```kotlin
banner.listener = globalListener
banner.addListener(AdCallbacks(onRendered = { screenAnalytics.impression(it) }))

val ad = InterstitialAd(this, AdCallbacks(onDismissed = { returnToApp() }))
ad.addListener(analyticsListener) // дополняет
```

`AdError` (sealed class; `message`, опционально `cause`):

| Тип | Значение |
|---|---|
| `NoBid` | биддер не вернул бид (204 / пустой `seatbid`) |
| `Network(cause)` | сетевая ошибка |
| `InvalidResponse(msg)` | некорректный ответ / VAST |
| `Render(msg, cause)` | ошибка рендера/плеера |
| `WebViewUnavailable` | на устройстве нет System WebView |
| `NotConfigured(msg)` | SDK/параметры не настроены |
| `Stopped` | биддер `stopped` по `/status` — запросы блокируются |

```kotlin
override fun onAdFailed(error: AdError) {
    when (error) {
        is AdError.NoBid -> hideSlot()
        is AdError.WebViewUnavailable -> disableHtmlAds() // видео при этом работает
        is AdError.Stopped -> { /* биддер выключен */ }
        else -> Log.w("Ads", error.message, error.cause)
    }
}
```

---

## Ad sizes

`AdSize` (в dp = OpenRTB w/h):

```kotlin
AdSize.BANNER       // 320 x 50
AdSize.MREC         // 300 x 250
AdSize.LEADERBOARD  // 728 x 90
AdSize.BILLBOARD    // 970 x 250
AdSize.TV_BANNER    // 1920 x 200
AdSize(1920, 1080)  // произвольный (например, для видео)
```

---

## Ad marking (ЕРИД / nroa)

Если VAST содержит расширение маркировки:

```xml
<Extensions>
  <Extension type="nroa_inform">
    <Title><![CDATA[Реклама]]></Title>
    <Url><![CDATA[https://example.com/...]]></Url>
    <Erid><![CDATA[12345abc]]></Erid>
  </Extension>
</Extensions>
```

то в полноэкранном интерстишле текст из `Title` **заменяет** стандартную надпись
«Реклама» в чипе рядом со счётчиком. При переходе фокуса на чип и нажатии **OK**
открывается `Url` (с [QR-фолбэком](#android-tv-notes), если браузера нет), а `Erid`
показывается в этом модальном окне. Данные доступны напрямую через
`videoAdView.nroaInform` (`title` / `url` / `erid`).

---

## Android TV notes

- **WebView может отсутствовать** на части TV (AOSP): баннер отдаёт
  `AdError.WebViewUnavailable`, приложение не падает, видео (ExoPlayer) работает.
- **Advertising ID (IFA):** SDK берёт **GAID** через Play Services
  (`AdvertisingIdClient`) и honor'ит «Limit ad tracking» (`device.lmt`). Без Play
  Services — fallback на `ANDROID_ID` c `lmt = 0`. Разрешение
  `com.google.android.gms.permission.AD_ID` уже объявлено (нужно при `targetSdk 33+`).
- **Фокус/пульт:** `BannerAdView` фокусируется (белая рамка), `VideoAdView`
  обрабатывает `DPAD_CENTER`. После рендера при необходимости вызывайте
  `requestFocus()`.
- **ClickThrough без браузера:** если открыть URL нечем (типично для ТВ), SDK
  показывает модальное окно с **QR-кодом** ссылки и кнопкой «Закрыть» — зритель
  сканирует его телефоном. Работает для видео (`ClickThrough`) и баннера.
- **Тестирование:** образ Android TV эмулятора
  (`system-images;android-34;android-tv;x86`) и/или реальное устройство.

---

## Bidder contract

**Запрос:** `POST` JSON `BidRequest`, заголовок `x-openrtb-version: 2.5` на
`host/ads`. SDK проставляет CTV-специфику: `device.devicetype = 3` (Connected TV),
`imp.secure = 1`, для видео — `video.placement = 1`, `protocols = [2,3,5,6]`.

**Ответ:** `BidResponse` c `seatbid[].bid[]` (выбирается бид с макс. `price`):

- **баннер** — HTML в `bid.adm`;
- **видео** — VAST XML в `bid.adm` (inline или wrapper);
- `bid.w` / `bid.h` — размеры (баннер масштабирует слот, если не задан `fixedSize`);
- `bid.nurl` (win notice) пингуется сразу при получении ответа, `bid.burl`
  (billing notice) — при рендеринге; VAST-трекинг шлётся автоматически;
- `204 No Content` или пустой `seatbid` = no-bid (`AdError.NoBid`).

**Status:** `GET host/status` → `{"status":"active|stopped","version":"…"}`.

---

## API reference

```kotlin
// Инициализация (host → host/ads, host/status)
CtvAds.initialize(context, CtvAdsConfig(host, appName, …)) { result -> /* status/version */ }
CtvAds.isInitialized: Boolean
CtvAds.status: SdkStatus         // ACTIVE / STOPPED / UNKNOWN
CtvAds.bidderVersion: String?
CtvAds.canRequestAds(): Boolean  // false, если биддер stopped

data class CtvAdsConfig(host, appName, appBundle, appStoreUrl,
                        appDomain, publisherId, timeoutMs, test)
data class InitResult(status: SdkStatus, version: String?, error: Throwable?)
fun interface InitListener { fun onInitialized(result: InitResult) }

// Частичный листенер из лямбд
class AdCallbacks(
    onLoaded: ((Bid) -> Unit)? = null, onRendered: ((Bid) -> Unit)? = null,
    onFailed: ((AdError) -> Unit)? = null, onClicked: ((Bid) -> Unit)? = null,
    onCompleted: ((Bid) -> Unit)? = null, onDismissed: (() -> Unit)? = null,
    onTracking: ((AdTrackingEvent) -> Unit)? = null,
) : AdListener

// Баннер
class BannerAdView : FrameLayout {
    var listener: AdListener?
    var fixedSlotSize: AdSize?
    fun addListener(listener: AdListener): BannerAdView
    fun loadAd(scope: LifecycleCoroutineScope, size: AdSize,
               bidFloor: Double? = null, fixedSize: Boolean = false)
    fun render(bid: Bid)
}

// Видео
class VideoAdView : FrameLayout {
    var listener: AdListener?
    val nroaInform: VastAd.NroaInform?
    fun addListener(listener: AdListener): VideoAdView
    fun loadAd(scope: LifecycleCoroutineScope, size: AdSize, bidFloor: Double? = null)
    fun render(bid: Bid)
    fun releasePlayer()
}

// Полноэкранная реклама (видео или баннер)
enum class AdFormat { VIDEO, BANNER }

class InterstitialAd(context: Context, vararg listeners: AdListener) {
    var skipOffsetSeconds: Int       // задержка кнопки «Пропустить» (по умолч. 5)
    var bannerDurationSeconds: Int   // время показа баннера (по умолч. 15)
    val isReady: Boolean
    fun setPlacementId(placementId: String?): InterstitialAd // → imp.tagid
    fun addListener(listener: AdListener): InterstitialAd
    fun load(scope: CoroutineScope, format: AdFormat,
             size: AdSize = /* весь экран */, bidFloor: Double? = null,
             autoShow: Boolean = false)
    fun show()
}
```
