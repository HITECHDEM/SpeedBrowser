package com.example.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
  name = "zerochrome_config"
)

data class ProfileCleanResult(
  val bytesReclaimed: Long,
  val cookiesCleared: Boolean,
  val cacheWiped: Boolean,
  val timestampMillis: Long,
)

class ConfigRepository(private val context: Context) {

  private object Keys {
    val TARGET_URL = stringPreferencesKey("target_url")
    val ENABLED_FLAGS = stringSetPreferencesKey("enabled_flags")
    val CUSTOM_FLAGS = stringPreferencesKey("custom_flags")
    val CLEAN_COOKIES = booleanPreferencesKey("clean_cookies_before_launch")
    val CACHE_STRATEGY = stringPreferencesKey("cache_strategy")
    val RAM_ASSET_CACHE = booleanPreferencesKey("ram_asset_cache")
    val KEEP_WEBVIEW_WARM = booleanPreferencesKey("keep_webview_warm")
    val OFFSCREEN_PRE_RASTER = booleanPreferencesKey("offscreen_pre_raster")
    val ZERO_OVERLAY_MODE = booleanPreferencesKey("zero_overlay_mode")
    val MAX_REFRESH_LOCK = booleanPreferencesKey("max_refresh_lock")
    val PEAK_BURST_PERF = booleanPreferencesKey("peak_burst_perf")
    val PROCESS_PRIORITY = stringPreferencesKey("process_priority")
    val GPU_BACKEND = stringPreferencesKey("gpu_backend")
    val ANDROID_ENGINE = stringPreferencesKey("android_engine")
    val COMPACT_CUSTOM_TAB = booleanPreferencesKey("compact_custom_tab")
    val PREFERRED_CUSTOM_TAB_PKG = stringPreferencesKey("preferred_custom_tab_pkg")
  }

  val configFlow: Flow<BrowserConfig> =
    context.dataStore.data.map { prefs -> prefs.toBrowserConfig() }

  suspend fun saveConfig(transform: (BrowserConfig) -> BrowserConfig) {
    context.dataStore.edit { prefs ->
      val updated = transform(prefs.toBrowserConfig())
      prefs[Keys.TARGET_URL] = updated.targetUrl
      prefs[Keys.ENABLED_FLAGS] = updated.enabledFlagIds
      prefs[Keys.CUSTOM_FLAGS] = updated.customCliFlags
      prefs[Keys.CLEAN_COOKIES] = updated.cleanCookiesBeforeLaunch
      prefs[Keys.CACHE_STRATEGY] = updated.cacheStrategy.name
      prefs[Keys.RAM_ASSET_CACHE] = updated.ramAssetCacheEnabled
      prefs[Keys.KEEP_WEBVIEW_WARM] = updated.keepWebViewWarm
      prefs[Keys.OFFSCREEN_PRE_RASTER] = updated.offscreenPreRaster
      prefs[Keys.ZERO_OVERLAY_MODE] = updated.zeroOverlayPureMode
      prefs[Keys.MAX_REFRESH_LOCK] = updated.maxRefreshRateLock
      prefs[Keys.PEAK_BURST_PERF] = updated.peakBurstPerformance
      prefs[Keys.PROCESS_PRIORITY] = updated.processPriority.name
      prefs[Keys.GPU_BACKEND] = updated.gpuBackend.name
      prefs[Keys.ANDROID_ENGINE] = updated.androidEngineMode.name
      prefs[Keys.COMPACT_CUSTOM_TAB] = updated.compactCustomTabSheet
      if (updated.preferredCustomTabPackage != null) {
        prefs[Keys.PREFERRED_CUSTOM_TAB_PKG] = updated.preferredCustomTabPackage
      } else {
        prefs.remove(Keys.PREFERRED_CUSTOM_TAB_PKG)
      }
    }
  }

  private fun Preferences.toBrowserConfig(): BrowserConfig =
    BrowserConfig(
      targetUrl = this[Keys.TARGET_URL] ?: SPEEDOMETER_30_URL,
      enabledFlagIds = this[Keys.ENABLED_FLAGS] ?: ChromiumFlagCatalog.maxSpeedometerFlagIds,
      customCliFlags = this[Keys.CUSTOM_FLAGS] ?: "",
      cleanCookiesBeforeLaunch = this[Keys.CLEAN_COOKIES] ?: false,
      cacheStrategy =
        this[Keys.CACHE_STRATEGY]?.let { name ->
          CacheStrategy.entries.firstOrNull { it.name == name }
        } ?: CacheStrategy.CACHE_FIRST,
      ramAssetCacheEnabled = this[Keys.RAM_ASSET_CACHE] ?: true,
      keepWebViewWarm = this[Keys.KEEP_WEBVIEW_WARM] ?: true,
      offscreenPreRaster = this[Keys.OFFSCREEN_PRE_RASTER] ?: false,
      zeroOverlayPureMode = this[Keys.ZERO_OVERLAY_MODE] ?: true,
      maxRefreshRateLock = this[Keys.MAX_REFRESH_LOCK] ?: true,
      peakBurstPerformance = this[Keys.PEAK_BURST_PERF] ?: true,
      processPriority =
        this[Keys.PROCESS_PRIORITY]?.let { name ->
          ProcessPriority.entries.firstOrNull { it.name == name }
        } ?: ProcessPriority.HIGH,
      gpuBackend =
        this[Keys.GPU_BACKEND]?.let { name ->
          GpuBackend.entries.firstOrNull { it.name == name }
        } ?: GpuBackend.AUTO,
      androidEngineMode =
        this[Keys.ANDROID_ENGINE]?.let { name ->
          AndroidEngineMode.entries.firstOrNull { it.name == name }
        } ?: AndroidEngineMode.DIRECT_WEBVIEW,
      compactCustomTabSheet = this[Keys.COMPACT_CUSTOM_TAB] ?: false,
      preferredCustomTabPackage = this[Keys.PREFERRED_CUSTOM_TAB_PKG],
    )

  /**
   * Cleans cookies, DOM WebStorage, and form DB without deleting HTTP/V8/RAM caches
   * unless [wipeDiskCache] is true. Only triggers JVM GC on explicit cleanup.
   */
  suspend fun prepareRuntimeAndProfile(
    wipeCookiesAndStorage: Boolean,
    wipeDiskCache: Boolean,
    webView: WebView? = null,
  ): ProfileCleanResult {
    if (!wipeCookiesAndStorage && !wipeDiskCache) {
      return ProfileCleanResult(
        bytesReclaimed = 0L,
        cookiesCleared = false,
        cacheWiped = false,
        timestampMillis = System.currentTimeMillis(),
      )
    }

    val bytesBefore = withContext(Dispatchers.IO) { measureWebViewDirBytes() }

    var cookiesCleared = false
    cookiesCleared =
      withContext(Dispatchers.Main) {
        if (wipeCookiesAndStorage) {
          WebStorage.getInstance().deleteAllData()
          @Suppress("DEPRECATION")
          WebViewDatabase.getInstance(context).clearFormData()
          WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
          webView?.clearHistory()
          webView?.clearFormData()
        }
        if (wipeDiskCache) {
          webView?.clearCache(true)
          BenchmarkWebViewPool.destroyPool()
          BenchmarkRamCache.clear()
        }
        if (wipeCookiesAndStorage) clearCookiesSuspending() else false
      }

    if (wipeDiskCache) {
      withContext(Dispatchers.IO) {
        context.cacheDir?.deleteRecursively()
        context.cacheDir?.mkdirs()
      }
      withContext(Dispatchers.Default) {
        System.gc()
      }
    }

    val bytesAfter = withContext(Dispatchers.IO) { measureWebViewDirBytes() }
    val reclaimed = (bytesBefore - bytesAfter).coerceAtLeast(0L)

    return ProfileCleanResult(
      bytesReclaimed = reclaimed,
      cookiesCleared = cookiesCleared,
      cacheWiped = wipeDiskCache,
      timestampMillis = System.currentTimeMillis(),
    )
  }

  private suspend fun clearCookiesSuspending(): Boolean =
    suspendCancellableCoroutine { cont ->
      val cm = CookieManager.getInstance()
      cm.removeAllCookies { removed ->
        cm.flush()
        if (cont.isActive) cont.resume(removed)
      }
    }

  private fun measureWebViewDirBytes(): Long {
    val webViewDir = File(context.dataDir, "app_webview")
    val cacheSize = directorySize(context.cacheDir)
    val wvSize = directorySize(webViewDir)
    return cacheSize + wvSize
  }

  private fun directorySize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var total = 0L
    dir.walkTopDown().forEach { file ->
      if (file.isFile) total += file.length()
    }
    return total
  }
}
