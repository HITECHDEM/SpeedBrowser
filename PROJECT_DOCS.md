# ZeroChrome — Project Documentation & Engineering Log

## 1. Overview & Architecture
- **App Name**: `ZeroChrome` (`com.aistudio.zerochrome.v8kpqm`)
- **Platform**: Android (Kotlin + Jetpack Compose + Material 3, plus pure-View `BenchmarkRunnerActivity`)
- **Purpose**:
  1. **Direct On-Device Speedometer 3.0 / 3.1 Runner**: Launches `https://browserbench.org/Speedometer3.0/` (or `3.1/`) inside a pure-View `BenchmarkRunnerActivity` (zero Compose tree overhead during runs) backed by a warm `WebView` pool + 96 MB In-Memory RAM asset cache, OR via a pre-warmed Chrome Custom Tab (fullscreen or 78% compact sheet).
  2. **Desktop Chromium + React Chrome Configurator & Exporter**: Generates a ready-to-run ultra-minimal Electron `BaseWindow` + `WebContentsView` shell (where React renders *only* the 44px browser chrome toolbar and Speedometer runs directly in Chromium's `WebContentsView`), plus Windows PowerShell & macOS/Linux Bash launch scripts.

## 2. File Structure
- `app/src/main/java/com/example/ZeroChromeApplication.kt` — App-launch `WebViewCompat.startUpWebView(...)` async pre-warm, `ProfileStore` initialization, and background `BenchmarkRamCache.prewarmUrl(...)`.
- `app/src/main/java/com/example/BenchmarkRunnerActivity.kt` — Pure Android `LinearLayout` + `WebView` runner activity (zero Compose / `NavHost` / `AndroidViewHolder` overhead), applying `THREAD_PRIORITY_URGENT_DISPLAY`, `FLAG_KEEP_SCREEN_ON`, `setSustainedPerformanceMode(false)`, `preferredDisplayModeId` max Hz lock, and Android 12+ ADPF `PerformanceHintManager.Session`.
- `app/src/main/java/com/example/data/BenchmarkWebViewPool.kt` — Holds a warm `MutableContextWrapper`-backed `WebView` across consecutive launches so the renderer process, V8 isolate, Blink `MemoryCache`, and GPU shader cache remain hot without leaking `Activity` context. Uses `View.LAYER_TYPE_NONE` for direct hardware compositing.
- `app/src/main/java/com/example/data/BenchmarkRamCache.kt` — 96 MB `LruCache` backed by HTTP/2 `OkHttpClient`, intercepting static `GET` benchmark assets via `shouldInterceptRequest` and returning `Cache-Control: public, max-age=31536000, immutable` + CORS headers with zero disk I/O or 304 network revalidations.
- `app/src/main/java/com/example/data/CustomTabsHelper.kt` — Discovers installed Custom Tabs browsers, binds `CustomTabsService`, runs `warmup(0L)` + `mayLaunchUrl(...)`, and launches either Fullscreen or 78%-height Compact Sheet Custom Tabs.
- `app/src/main/java/com/example/data/BrowserConfig.kt` — Immutable configuration models (`CACHE_FIRST` default, `ramAssetCacheEnabled = true`, `keepWebViewWarm = true`, `cleanCookiesBeforeLaunch = false`, `offscreenPreRaster = false`), Chromium CLI flags, GPU/priority/engine enums, and presets.
- `app/src/main/java/com/example/data/DeviceTelemetry.kt` — Real Android hardware telemetry (`availableProcessors`, `ActivityManager.MemoryInfo`, `PowerManager.currentThermalStatus`, `isPowerSaveMode`, `Display.supportedModes` max Hz, `WebView.getCurrentWebViewPackage()`).
- `app/src/main/java/com/example/data/ConfigRepository.kt` — Jetpack DataStore Preferences persistence, full reset cleanup (`WebStorage`, `CookieManager`, `BenchmarkWebViewPool.destroyPool()`, `BenchmarkRamCache.clear()`).
- `app/src/main/java/com/example/data/DesktopBundleGenerator.kt` — Deterministic code generator producing `main.mjs`, `chrome-ui/index.html`, `chrome-ui/App.jsx`, `package.json`, `launch-windows.ps1`, and `launch-unix.sh`.
- `app/src/main/java/com/example/ui/BenchViewModel.kt` — `StateFlow<BenchUiState>` combining config, hardware telemetry, live `RamCacheStats`, and discovered Custom Tab browsers.
- `app/src/main/java/com/example/ui/screens/LauncherScreen.kt` & `LauncherComponents.kt` — Hero command surface, Device Readiness telemetry, RAM cache & warm pool controls, Custom Tab browser selector + compact sheet toggle, presets, and power toggles.
- `app/src/main/java/com/example/ui/screens/FlagsScreen.kt` — Categorized Chromium command-line flags, process priority selector, GPU/ANGLE selector, and custom CLI flag input.
- `app/src/main/java/com/example/ui/screens/DesktopBundleScreen.kt` — Live preview, copy, and Android Share export for the generated desktop bundle.
- `app/src/main/java/com/example/MainActivity.kt` — Edge-to-edge Compose host (`ROUTE_LAUNCHER`, `ROUTE_FLAGS`, `ROUTE_DESKTOP`) launching `BenchmarkRunnerActivity` or `CustomTabsHelper`.

## 3. Engineering Log (What Worked / What Didn't / Recurring Issues)
- **Root Causes of Previous WebView 12.4–12.6 Score & Cache Stalls (Fixed)**:
  1. `View.setLayerType(View.LAYER_TYPE_HARDWARE, null)` on `WebView` forced Android's View system to allocate an extra offscreen GPU FBO texture buffer and blit-composite every frame; switching hardware mode to `View.LAYER_TYPE_NONE` restores direct GPU compositing.
  2. `cleanCookiesBeforeLaunch = true` + `WARM_DEFAULT` (`LOAD_DEFAULT`) wiped `WebStorage` (IndexedDB/CacheStorage) and triggered `System.gc()` + `System.runFinalization()` before every launch, while `LOAD_DEFAULT` still allowed 304 HTTP revalidations across subtest iframes. Fixed by defaulting to `CACHE_FIRST` (`LOAD_CACHE_ELSE_NETWORK`), `cleanCookiesBeforeLaunch = false`, and adding `BenchmarkRamCache` (96 MB in-memory `LruCache` with `immutable` cache headers).
  3. Destroying `WebView` (`wv.destroy()`) on every exit forced cold renderer/V8/shader recompilation on every launch. Replaced with `BenchmarkWebViewPool` (`MutableContextWrapper` parking on `about:blank` when idle) + `WebViewCompat.startUpWebView` on app launch.
  4. Hosting `WebView` inside Compose `NavHost` + `AndroidView` added Compose layout/recomposition plumbing; replaced with pure Android View `BenchmarkRunnerActivity` + ADPF `PerformanceHintManager.Session` + `android:largeHeap="true"` + `android.webkit.WebView.MetricsOptOut`.
  5. `CustomTabsIntent` previously passed a null binder without `CustomTabsClient.warmup` or `mayLaunchUrl`. Replaced with `CustomTabsHelper` (`androidx.browser:browser:1.8.0`).
- **API & Dependency Notes**:
  - `androidx.webkit:webkit:1.14.0`: `WebViewCompat.WebViewStartUpCallback.onSuccess(result: WebViewStartUpResult)` takes a non-null `WebViewStartUpResult` (not nullable). `WebViewFeature.ORIGIN_PRECONNECT` / `Profile.preconnect` is NOT in `1.14.0` (1.15+); use `BenchmarkRamCache.prewarmUrl` via OkHttp HTTP/2 instead.
  - `androidx.browser:browser:1.8.0` and `okhttp:4.10.0` resolve cleanly.
- **Build & Test Notes**:
  - **Important**: Never run `compile_applet` and `gradle ...` concurrently in the same turn — parallel Gradle daemons collide on `app/build/kotlin/compileDebugKotlin/cacheable/caches-jvm/jvm/kotlin/package-parts.tab_i` (`CorruptedException`). Always run them sequentially.
  - Running all `:app:testDebugUnitTest` without filtering triggers `ExampleRobolectricTest`, which can stall downloading Robolectric's `android-all` artifact; run pure JVM unit tests with `--tests "com.example.DesktopBundleGeneratorTest"` for fast verification.
  - Public GitHub readiness: `.gitignore` excludes `.env`, `debug.keystore`, `debug.keystore.base64`, `.build-outputs`, `*.apk`, `*.aab`, `*.jks`. `LICENSE` (MIT) and `README.md` (with privacy & trademark disclaimers) added at root; zero API keys or PII present.
