package com.example.data

const val SPEEDOMETER_30_URL = "https://browserbench.org/Speedometer3.0/"
const val SPEEDOMETER_31_URL = "https://browserbench.org/Speedometer3.1/"
const val SPEEDOMETER_URL = SPEEDOMETER_30_URL

enum class FlagCategory(val label: String) {
  MINIMIZATION("Minimization & IPC"),
  SCHEDULER("V8, Caching & Scheduler"),
  GPU("GPU & Rasterization"),
}

data class ChromiumFlagSpec(
  val id: String,
  val flag: String,
  val title: String,
  val description: String,
  val category: FlagCategory,
  val appliedOnAndroid: Boolean = false,
)

enum class ProcessPriority(
  val label: String,
  val windowsPriorityClass: String,
  val unixNice: Int,
  val description: String,
) {
  NORMAL("Normal", "Normal", 0, "Standard OS scheduler priority."),
  ABOVE_NORMAL(
    "Above Normal",
    "AboveNormal",
    -5,
    "Elevated renderer/browser priority with minimal system impact.",
  ),
  HIGH(
    "High",
    "High",
    -12,
    "Recommended for Speedometer 3.0: reduces OS preemption jitter.",
  ),
  REALTIME(
    "Realtime",
    "RealTime",
    -18,
    "Maximum scheduling priority (requires admin/root on desktop OS).",
  ),
}

enum class CacheStrategy(
  val label: String,
  val description: String,
) {
  WARM_DEFAULT(
    "Warm Cache (Max Score)",
    "Keeps HTTP & V8 bytecode cache active (LOAD_DEFAULT) across subtests & repeat runs.",
  ),
  CACHE_FIRST(
    "Aggressive Cache-First",
    "Serves cached framework assets directly from storage when valid (LOAD_CACHE_ELSE_NETWORK).",
  ),
  COLD_NO_CACHE(
    "Cold Bypass",
    "Forces LOAD_NO_CACHE on every load for cold-start baseline testing (lower score).",
  ),
}

enum class GpuBackend(
  val label: String,
  val angleSwitch: String?,
  val isHardware: Boolean,
  val description: String,
) {
  AUTO(
    "Hardware Auto",
    null,
    true,
    "Uses OS native GPU pipeline (Hardware layer + pre-raster on Android WebView).",
  ),
  VULKAN(
    "Hardware + Vulkan",
    "--use-angle=vulkan",
    true,
    "Forces Vulkan GPU rasterization & zero-copy compositing.",
  ),
  METAL_D3D11(
    "ANGLE Native (D3D11/Metal)",
    "--use-angle=default",
    true,
    "Uses default ANGLE hardware backend (D3D11 on Windows, Metal on macOS).",
  ),
  OPENGL(
    "Hardware + OpenGL",
    "--use-angle=gl",
    true,
    "Forces OpenGL ANGLE backend for driver comparison.",
  ),
  SOFTWARE(
    "Software (SwiftShader)",
    "--use-angle=swiftshader",
    false,
    "Disables hardware GPU layer for baseline CPU-bound comparison.",
  ),
}

enum class AndroidEngineMode(
  val label: String,
  val description: String,
) {
  DIRECT_WEBVIEW(
    "Direct WebView",
    "Runs directly in Chromium WebView with IMPORTANT renderer priority, offscreen pre-raster & SafeBrowsing disabled.",
  ),
  CUSTOM_TAB(
    "Chrome Custom Tab",
    "Opens in full Chrome/Chromium engine via Custom Tabs (often highest V8/Maglev tier on Android).",
  ),
}

enum class BenchmarkPreset(
  val id: String,
  val title: String,
  val subtitle: String,
) {
  MAX_POWER(
    "max_power",
    "Max Phone Power",
    "96MB RAM asset cache, warm V8 renderer, 120Hz+ ADPF boost & zero-overlay pure runner",
  ),
  GPU_VULKAN(
    "gpu_vulkan",
    "Vulkan Zero-Copy",
    "Aggressive GPU rasterization, Vulkan backend, RAM asset cache & zero-overlay pure mode",
  ),
  STRICT_BASELINE(
    "strict_baseline",
    "Clean Cold Baseline",
    "Wipes full cache/storage/RAM before run, Normal priority, Cold Bypass comparison",
  ),
  SOFTWARE_REF(
    "software_ref",
    "Software Reference",
    "SwiftShader / software canvas layer to isolate JS/DOM vs GPU",
  ),
}

object ChromiumFlagCatalog {
  val allFlags: List<ChromiumFlagSpec> =
    listOf(
      ChromiumFlagSpec(
        id = "disable_extensions",
        flag = "--disable-extensions",
        title = "Disable Extensions",
        description = "Eliminates extension host processes and content-script injection overhead.",
        category = FlagCategory.MINIMIZATION,
      ),
      ChromiumFlagSpec(
        id = "disable_bg_net",
        flag = "--disable-background-networking",
        title = "Disable Background Networking",
        description = "Stops safebrowsing, updater, and telemetry network calls during runs.",
        category = FlagCategory.MINIMIZATION,
        appliedOnAndroid = true,
      ),
      ChromiumFlagSpec(
        id = "disable_component_update",
        flag = "--disable-component-update",
        title = "Disable Component Updates",
        description = "Prevents Chromium from checking or unpacking components in background.",
        category = FlagCategory.MINIMIZATION,
      ),
      ChromiumFlagSpec(
        id = "disable_sync",
        flag = "--disable-sync",
        title = "Disable Account Sync",
        description = "Cuts Sync engine IPC and background storage writes.",
        category = FlagCategory.MINIMIZATION,
      ),
      ChromiumFlagSpec(
        id = "disable_default_apps",
        flag = "--disable-default-apps",
        title = "Disable Default Apps",
        description = "Skips initializing built-in web apps on clean profiles.",
        category = FlagCategory.MINIMIZATION,
      ),
      ChromiumFlagSpec(
        id = "no_first_run",
        flag = "--no-first-run",
        title = "Skip First-Run Hooks",
        description = "Suppresses first-run onboarding UI on ephemeral temp directories.",
        category = FlagCategory.MINIMIZATION,
      ),
      ChromiumFlagSpec(
        id = "metrics_only",
        flag = "--metrics-recording-only",
        title = "Disable Telemetry Uploads",
        description = "Prevents UMA metrics upload workers from waking during benchmarks.",
        category = FlagCategory.MINIMIZATION,
        appliedOnAndroid = true,
      ),
      ChromiumFlagSpec(
        id = "disable_hang_monitor",
        flag = "--disable-hang-monitor",
        title = "Disable Hang Monitor",
        description = "Removes renderer responsiveness ping IPC during tight JS loops.",
        category = FlagCategory.MINIMIZATION,
      ),
      ChromiumFlagSpec(
        id = "strip_features",
        flag = "--disable-features=Translate,OptimizationHints,MediaRouter,BackForwardCache",
        title = "Strip Unneeded Subsystems",
        description = "Disables Translate, OptimizationHints, Cast MediaRouter, and BFCache overhead.",
        category = FlagCategory.MINIMIZATION,
      ),
      ChromiumFlagSpec(
        id = "v8_code_cache",
        flag = "--v8-cache-options=code",
        title = "V8 Aggressive Code Cache",
        description = "Caches compiled V8 Ignition/Sparkplug bytecode to eliminate repeat parse time.",
        category = FlagCategory.SCHEDULER,
      ),
      ChromiumFlagSpec(
        id = "disk_cache_512",
        flag = "--disk-cache-size=536870912",
        title = "512MB Fast Asset Cache",
        description = "Expands Chromium disk cache budget so Speedometer suites stay hot.",
        category = FlagCategory.SCHEDULER,
      ),
      ChromiumFlagSpec(
        id = "disable_site_isolation",
        flag = "--disable-site-isolation-trials",
        title = "Lower Process Isolation Overhead",
        description = "Minimizes cross-process IPC allocations on single-origin benchmarks.",
        category = FlagCategory.SCHEDULER,
      ),
      ChromiumFlagSpec(
        id = "disable_timer_throttling",
        flag = "--disable-background-timer-throttling",
        title = "Disable Timer Throttling",
        description = "Prevents Chromium from clamping rapid timers across subtests.",
        category = FlagCategory.SCHEDULER,
      ),
      ChromiumFlagSpec(
        id = "disable_renderer_bg",
        flag = "--disable-renderer-backgrounding",
        title = "Disable Renderer Backgrounding",
        description = "Keeps renderer thread scheduling at full foreground priority.",
        category = FlagCategory.SCHEDULER,
        appliedOnAndroid = true,
      ),
      ChromiumFlagSpec(
        id = "disable_occlusion",
        flag = "--disable-backgrounding-occluded-windows",
        title = "Disable Occlusion Throttling",
        description = "Prevents compositor throttling if desktop window focus shifts.",
        category = FlagCategory.SCHEDULER,
      ),
      ChromiumFlagSpec(
        id = "disable_ipc_flood",
        flag = "--disable-ipc-flooding-protection",
        title = "Disable IPC Flood Protection",
        description = "Removes artificial IPC rate throttling on rapid DOM/navigation cycles.",
        category = FlagCategory.SCHEDULER,
      ),
      ChromiumFlagSpec(
        id = "gpu_rasterization",
        flag = "--enable-gpu-rasterization",
        title = "GPU Rasterization",
        description = "Offloads page layer rasterization to GPU hardware.",
        category = FlagCategory.GPU,
        appliedOnAndroid = true,
      ),
      ChromiumFlagSpec(
        id = "zero_copy",
        flag = "--enable-zero-copy",
        title = "Zero-Copy Textures",
        description = "Avoids redundant CPU-to-GPU memory copies during compositing.",
        category = FlagCategory.GPU,
      ),
      ChromiumFlagSpec(
        id = "ignore_gpu_blocklist",
        flag = "--ignore-gpu-blocklist",
        title = "Ignore GPU Blocklist",
        description = "Enforces hardware acceleration regardless of driver blocklist.",
        category = FlagCategory.GPU,
      ),
      ChromiumFlagSpec(
        id = "oop_canvas",
        flag = "--canvas-oop-rasterization",
        title = "Out-of-Process Canvas",
        description = "Accelerates HTML5 Canvas workloads used in Speedometer Charts.",
        category = FlagCategory.GPU,
        appliedOnAndroid = true,
      ),
    )

  val maxSpeedometerFlagIds: Set<String> = allFlags.map { it.id }.toSet()

  val strictBaselineFlagIds: Set<String> =
    setOf(
      "disable_extensions",
      "disable_bg_net",
      "disable_component_update",
      "no_first_run",
      "metrics_only",
    )
}

data class BrowserConfig(
  val targetUrl: String = SPEEDOMETER_30_URL,
  val enabledFlagIds: Set<String> = ChromiumFlagCatalog.maxSpeedometerFlagIds,
  val customCliFlags: String = "",
  val cleanCookiesBeforeLaunch: Boolean = false,
  val cacheStrategy: CacheStrategy = CacheStrategy.CACHE_FIRST,
  val ramAssetCacheEnabled: Boolean = true,
  val keepWebViewWarm: Boolean = true,
  val offscreenPreRaster: Boolean = false,
  val zeroOverlayPureMode: Boolean = true,
  val maxRefreshRateLock: Boolean = true,
  val peakBurstPerformance: Boolean = true,
  val processPriority: ProcessPriority = ProcessPriority.HIGH,
  val gpuBackend: GpuBackend = GpuBackend.AUTO,
  val androidEngineMode: AndroidEngineMode = AndroidEngineMode.DIRECT_WEBVIEW,
  val compactCustomTabSheet: Boolean = false,
  val preferredCustomTabPackage: String? = null,
) {
  fun resolveCommandLineFlags(): List<String> {
    val result = mutableListOf<String>()
    ChromiumFlagCatalog.allFlags.forEach { spec ->
      if (enabledFlagIds.contains(spec.id)) {
        if (gpuBackend.isHardware || spec.category != FlagCategory.GPU) {
          result.add(spec.flag)
        }
      }
    }
    if (!gpuBackend.isHardware) {
      result.add("--disable-gpu")
    }
    gpuBackend.angleSwitch?.let { result.add(it) }
    if (gpuBackend == GpuBackend.VULKAN) {
      result.add("--enable-features=Vulkan")
    }
    val extra =
      customCliFlags
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    result.addAll(extra)
    return result.distinct()
  }
}
