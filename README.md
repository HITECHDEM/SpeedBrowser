# ZeroChrome

**ZeroChrome** is an open-source Android benchmark launcher and Chromium tuning toolkit designed to run web benchmarks (such as Speedometer 3.0 / 3.1) with minimal shell overhead, aggressive in-memory caching, and full hardware clock/refresh-rate headroom. It also generates a minimal Electron `BaseWindow` + dual `WebContentsView` shell and OS priority scripts for Desktop Chromium benchmarking.

## Key Features

### 1. Pure-View Android WebView Runner (`BenchmarkRunnerActivity`)
- **Zero Compose Overhead During Runs**: Executes inside a dedicated pure Android `LinearLayout` + `WebView` activity (zero Jetpack Compose tree, `NavHost`, or `AndroidViewHolder` interop passes while benchmarking).
- **Direct Hardware Compositing (`View.LAYER_TYPE_NONE`)**: Avoids offscreen FBO texture copies caused by `View.LAYER_TYPE_HARDWARE` on `WebView`, letting Chromium composite frames directly.
- **96 MB In-Memory RAM Asset Cache (`BenchmarkRamCache`)**: Intercepts static benchmark assets (`.js`, `.mjs`, `.css`, `.html`, `.svg`, `.wasm`, `.json`, fonts, images) via an HTTP/2 connection-pooled `OkHttpClient` and serves repeat hits directly from RAM with `Cache-Control: public, max-age=31536000, immutable` so Blink's `MemoryCache` and V8 bytecode cache stay hot without disk flash I/O or 304 network revalidation stalls.
- **Warm Renderer & Shader Pool (`BenchmarkWebViewPool`)**: Reuses a single `MutableContextWrapper`-backed `WebView` across consecutive runs so the Chromium renderer process, V8 isolate, and GPU shader cache stay warm without leaking `Activity` context.
- **Jetpack WebKit Startup & Preconnect (`ZeroChromeApplication`)**: Calls `WebViewCompat.startUpWebView` asynchronously on app launch, pre-connects to `https://browserbench.org`, and opts out of WebView UMA metrics (`android.webkit.WebView.MetricsOptOut`).
- **OS & Hardware Boost**:
  - `WebView.RENDERER_PRIORITY_IMPORTANT` renderer priority
  - `Process.THREAD_PRIORITY_URGENT_DISPLAY` + `Thread.MAX_PRIORITY`
  - `Window.setSustainedPerformanceMode(false)` + `FLAG_KEEP_SCREEN_ON`
  - `preferredDisplayModeId` unlocked to peak display refresh rate (90–144 Hz)
  - Android 12+ ADPF `PerformanceHintManager.Session` frame-pacing hint
  - SafeBrowsing URL lookup IPC and zoom gesture controllers disabled during runs

### 2. Warmed Chrome Custom Tabs (`CustomTabsHelper`)
- Discovers installed Custom Tabs providers on the device (Chrome, Opera, Edge, Brave, Samsung Internet, etc.).
- Binds `CustomTabsService`, calls `warmup(0L)` and `mayLaunchUrl(...)` ahead of launch.
- Supports both **Fullscreen Custom Tab** and **Compact Sheet Mode** (78% height adjustable sheet).

### 3. Desktop React-Chrome + Direct Chromium Exporter (`DesktopBundleGenerator`)
- Generates `main.mjs`, `chrome-ui/App.jsx`, `chrome-ui/index.html`, `package.json`, `launch-windows.ps1`, and `launch-unix.sh`.
- Uses Electron `BaseWindow` + dual `WebContentsView` so React renders *only* the 44px top toolbar while the target webpage executes directly inside Chromium's native compositor.

## Building

Requires Android SDK (Compile SDK 36, Min SDK 24) and JDK 17+.

```bash
gradle assembleDebug
gradle :app:testDebugUnitTest --tests "com.example.DesktopBundleGeneratorTest"
```

## Privacy & Security

- Zero analytics, tracking, or telemetry SDKs.
- No hardcoded API keys or user credentials; all preferences are stored locally via Jetpack DataStore.
- Built-in device hardware telemetry (CPU cores, free RAM, refresh rate, thermal status) is read locally on-device and never transmitted.

## Disclaimer & Trademarks

ZeroChrome is an independent open-source utility and is not affiliated with, endorsed by, or sponsored by Google LLC, The Chromium Authors, Apple Inc., Opera, or BrowserBench. Benchmark suites such as Speedometer (`https://browserbench.org/Speedometer3.0/`) are loaded live from their respective public origins and are not bundled in this repository.

## License

MIT License — see [LICENSE](LICENSE).
