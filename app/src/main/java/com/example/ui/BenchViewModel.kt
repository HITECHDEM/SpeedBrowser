package com.example.ui

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AndroidEngineMode
import com.example.data.BenchmarkPreset
import com.example.data.BenchmarkRamCache
import com.example.data.BenchmarkWebViewPool
import com.example.data.BrowserConfig
import com.example.data.CacheStrategy
import com.example.data.ChromiumFlagCatalog
import com.example.data.ConfigRepository
import com.example.data.CustomTabsHelper
import com.example.data.DesktopBundleGenerator
import com.example.data.DeviceReadinessSnapshot
import com.example.data.DeviceTelemetry
import com.example.data.GeneratedDesktopFile
import com.example.data.GpuBackend
import com.example.data.InstalledBrowserInfo
import com.example.data.ProcessPriority
import com.example.data.ProfileCleanResult
import com.example.data.RamCacheStats
import com.example.data.SPEEDOMETER_30_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BenchUiState(
  val config: BrowserConfig = BrowserConfig(),
  val resolvedFlags: List<String> = emptyList(),
  val desktopBundle: List<GeneratedDesktopFile> = emptyList(),
  val deviceTelemetry: DeviceReadinessSnapshot? = null,
  val ramCacheStats: RamCacheStats = RamCacheStats(),
  val installedBrowsers: List<InstalledBrowserInfo> = emptyList(),
  val isCleaningProfile: Boolean = false,
  val lastCleanResult: ProfileCleanResult? = null,
)

class BenchViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = ConfigRepository(application.applicationContext)

  private val isCleaning = MutableStateFlow(false)
  private val lastClean = MutableStateFlow<ProfileCleanResult?>(null)
  private val telemetry = MutableStateFlow<DeviceReadinessSnapshot?>(null)
  private val browsers = MutableStateFlow<List<InstalledBrowserInfo>>(emptyList())

  init {
    refreshTelemetry()
    refreshInstalledBrowsers()
  }

  val uiState: StateFlow<BenchUiState> =
    combine(
        repository.configFlow,
        telemetry,
        BenchmarkRamCache.stats,
        browsers,
        isCleaning,
        lastClean,
      ) { values ->
        val config = values[0] as BrowserConfig
        val snap = values[1] as DeviceReadinessSnapshot?
        val ramStats = values[2] as RamCacheStats
        @Suppress("UNCHECKED_CAST")
        val browserList = values[3] as List<InstalledBrowserInfo>
        val cleaning = values[4] as Boolean
        val cleanResult = values[5] as ProfileCleanResult?
        BenchUiState(
          config = config,
          resolvedFlags = config.resolveCommandLineFlags(),
          desktopBundle = DesktopBundleGenerator.generateBundle(config),
          deviceTelemetry = snap,
          ramCacheStats = ramStats,
          installedBrowsers = browserList,
          isCleaningProfile = cleaning,
          lastCleanResult = cleanResult,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue =
          BenchUiState(
            resolvedFlags = BrowserConfig().resolveCommandLineFlags(),
            desktopBundle = DesktopBundleGenerator.generateBundle(BrowserConfig()),
          ),
      )

  fun refreshTelemetry() {
    viewModelScope.launch {
      val snap = withContext(Dispatchers.IO) { DeviceTelemetry.capture(getApplication()) }
      telemetry.value = snap
    }
    BenchmarkRamCache.refreshStats()
  }

  fun refreshInstalledBrowsers() {
    viewModelScope.launch {
      val list = withContext(Dispatchers.IO) { CustomTabsHelper.queryCustomTabBrowsers(getApplication()) }
      browsers.value = list
    }
  }

  fun prewarmTargetAndCustomTabs() {
    val cfg = uiState.value.config
    val normalizedUrl = normalizeUrl(cfg.targetUrl)
    viewModelScope.launch {
      if (cfg.ramAssetCacheEnabled) {
        BenchmarkRamCache.prewarmUrl(normalizedUrl, BenchmarkWebViewPool.currentUserAgent())
      }
      if (cfg.androidEngineMode == AndroidEngineMode.CUSTOM_TAB) {
        withContext(Dispatchers.Main) {
          CustomTabsHelper.warmupAndPrefetch(
            getApplication(),
            normalizedUrl,
            cfg.preferredCustomTabPackage,
          )
        }
      }
    }
  }

  fun updateTargetUrl(url: String) {
    viewModelScope.launch { repository.saveConfig { it.copy(targetUrl = url) } }
  }

  fun selectSpeedometerUrl(url: String = SPEEDOMETER_30_URL) {
    viewModelScope.launch { repository.saveConfig { it.copy(targetUrl = url) } }
  }

  fun toggleCleanCookiesBeforeLaunch(enabled: Boolean) {
    viewModelScope.launch { repository.saveConfig { it.copy(cleanCookiesBeforeLaunch = enabled) } }
  }

  fun setCacheStrategy(strategy: CacheStrategy) {
    viewModelScope.launch { repository.saveConfig { it.copy(cacheStrategy = strategy) } }
  }

  fun toggleRamAssetCache(enabled: Boolean) {
    viewModelScope.launch { repository.saveConfig { it.copy(ramAssetCacheEnabled = enabled) } }
  }

  fun toggleKeepWebViewWarm(enabled: Boolean) {
    viewModelScope.launch {
      repository.saveConfig { it.copy(keepWebViewWarm = enabled) }
      if (!enabled) {
        withContext(Dispatchers.Main) { BenchmarkWebViewPool.destroyPool() }
      }
    }
  }

  fun toggleOffscreenPreRaster(enabled: Boolean) {
    viewModelScope.launch { repository.saveConfig { it.copy(offscreenPreRaster = enabled) } }
  }

  fun toggleZeroOverlayPureMode(enabled: Boolean) {
    viewModelScope.launch { repository.saveConfig { it.copy(zeroOverlayPureMode = enabled) } }
  }

  fun toggleMaxRefreshRateLock(enabled: Boolean) {
    viewModelScope.launch { repository.saveConfig { it.copy(maxRefreshRateLock = enabled) } }
  }

  fun togglePeakBurstPerformance(enabled: Boolean) {
    viewModelScope.launch { repository.saveConfig { it.copy(peakBurstPerformance = enabled) } }
  }

  fun setProcessPriority(priority: ProcessPriority) {
    viewModelScope.launch { repository.saveConfig { it.copy(processPriority = priority) } }
  }

  fun setGpuBackend(backend: GpuBackend) {
    viewModelScope.launch { repository.saveConfig { it.copy(gpuBackend = backend) } }
  }

  fun setAndroidEngineMode(mode: AndroidEngineMode) {
    viewModelScope.launch {
      repository.saveConfig { it.copy(androidEngineMode = mode) }
      if (mode == AndroidEngineMode.CUSTOM_TAB) {
        val cfg = uiState.value.config
        withContext(Dispatchers.Main) {
          CustomTabsHelper.warmupAndPrefetch(
            getApplication(),
            normalizeUrl(cfg.targetUrl),
            cfg.preferredCustomTabPackage,
          )
        }
      }
    }
  }

  fun toggleCompactCustomTabSheet(enabled: Boolean) {
    viewModelScope.launch { repository.saveConfig { it.copy(compactCustomTabSheet = enabled) } }
  }

  fun setPreferredCustomTabPackage(packageName: String?) {
    viewModelScope.launch {
      repository.saveConfig { it.copy(preferredCustomTabPackage = packageName) }
      val cfg = uiState.value.config
      withContext(Dispatchers.Main) {
        CustomTabsHelper.warmupAndPrefetch(
          getApplication(),
          normalizeUrl(cfg.targetUrl),
          packageName,
        )
      }
    }
  }

  fun toggleFlag(flagId: String) {
    viewModelScope.launch {
      repository.saveConfig { cfg ->
        val next = cfg.enabledFlagIds.toMutableSet()
        if (next.contains(flagId)) next.remove(flagId) else next.add(flagId)
        cfg.copy(enabledFlagIds = next)
      }
    }
  }

  fun updateCustomFlags(flagsText: String) {
    viewModelScope.launch { repository.saveConfig { it.copy(customCliFlags = flagsText) } }
  }

  fun applyPreset(preset: BenchmarkPreset) {
    viewModelScope.launch {
      repository.saveConfig { current ->
        when (preset) {
          BenchmarkPreset.MAX_POWER ->
            current.copy(
              enabledFlagIds = ChromiumFlagCatalog.maxSpeedometerFlagIds,
              cleanCookiesBeforeLaunch = false,
              cacheStrategy = CacheStrategy.CACHE_FIRST,
              ramAssetCacheEnabled = true,
              keepWebViewWarm = true,
              offscreenPreRaster = false,
              zeroOverlayPureMode = true,
              maxRefreshRateLock = true,
              peakBurstPerformance = true,
              processPriority = ProcessPriority.HIGH,
              gpuBackend = GpuBackend.AUTO,
            )
          BenchmarkPreset.GPU_VULKAN ->
            current.copy(
              enabledFlagIds = ChromiumFlagCatalog.maxSpeedometerFlagIds,
              cleanCookiesBeforeLaunch = false,
              cacheStrategy = CacheStrategy.CACHE_FIRST,
              ramAssetCacheEnabled = true,
              keepWebViewWarm = true,
              offscreenPreRaster = false,
              zeroOverlayPureMode = true,
              maxRefreshRateLock = true,
              peakBurstPerformance = true,
              processPriority = ProcessPriority.HIGH,
              gpuBackend = GpuBackend.VULKAN,
            )
          BenchmarkPreset.STRICT_BASELINE ->
            current.copy(
              enabledFlagIds = ChromiumFlagCatalog.strictBaselineFlagIds,
              cleanCookiesBeforeLaunch = true,
              cacheStrategy = CacheStrategy.COLD_NO_CACHE,
              ramAssetCacheEnabled = false,
              keepWebViewWarm = false,
              offscreenPreRaster = false,
              zeroOverlayPureMode = false,
              maxRefreshRateLock = false,
              peakBurstPerformance = false,
              processPriority = ProcessPriority.NORMAL,
              gpuBackend = GpuBackend.AUTO,
            )
          BenchmarkPreset.SOFTWARE_REF ->
            current.copy(
              enabledFlagIds = ChromiumFlagCatalog.strictBaselineFlagIds,
              cleanCookiesBeforeLaunch = false,
              cacheStrategy = CacheStrategy.CACHE_FIRST,
              ramAssetCacheEnabled = true,
              keepWebViewWarm = true,
              offscreenPreRaster = false,
              zeroOverlayPureMode = true,
              maxRefreshRateLock = true,
              peakBurstPerformance = true,
              processPriority = ProcessPriority.HIGH,
              gpuBackend = GpuBackend.SOFTWARE,
            )
        }
      }
    }
  }

  fun wipeFullProfileNow(webView: WebView? = null, onComplete: (() -> Unit)? = null) {
    viewModelScope.launch {
      isCleaning.value = true
      try {
        lastClean.value =
          repository.prepareRuntimeAndProfile(
            wipeCookiesAndStorage = true,
            wipeDiskCache = true,
            webView = webView,
          )
      } finally {
        isCleaning.value = false
        onComplete?.invoke()
      }
    }
  }

  suspend fun prepareForBenchmarkLaunch(): ProfileCleanResult? {
    val cfg = uiState.value.config
    val wipeCache = cfg.cacheStrategy == CacheStrategy.COLD_NO_CACHE
    if (!cfg.cleanCookiesBeforeLaunch && !wipeCache) {
      return null
    }
    isCleaning.value = true
    return try {
      val result =
        repository.prepareRuntimeAndProfile(
          wipeCookiesAndStorage = cfg.cleanCookiesBeforeLaunch,
          wipeDiskCache = wipeCache,
          webView = null,
        )
      lastClean.value = result
      result
    } finally {
      isCleaning.value = false
    }
  }

  private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim().ifBlank { SPEEDOMETER_30_URL }
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
  }
}
