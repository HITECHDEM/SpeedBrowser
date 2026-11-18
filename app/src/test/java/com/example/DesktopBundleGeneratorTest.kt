package com.example

import com.example.data.BrowserConfig
import com.example.data.CacheStrategy
import com.example.data.DesktopBundleGenerator
import com.example.data.GpuBackend
import com.example.data.ProcessPriority
import com.example.data.SPEEDOMETER_30_URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBundleGeneratorTest {

  @Test
  fun resolveFlags_vulkanAndCustomFlags_includedCleanly() {
    val config =
      BrowserConfig(
        gpuBackend = GpuBackend.VULKAN,
        customCliFlags = "--js-flags=--max-old-space-size=4096 --disable-extensions",
      )
    val flags = config.resolveCommandLineFlags()
    assertTrue(flags.contains("--use-angle=vulkan"))
    assertTrue(flags.contains("--enable-features=Vulkan"))
    assertTrue(flags.contains("--v8-cache-options=code"))
    assertTrue(flags.contains("--js-flags=--max-old-space-size=4096"))
    assertEqualsOnce(flags, "--disable-extensions")
  }

  @Test
  fun resolveFlags_softwareBackend_omitsHardwareGpuSwitches() {
    val config = BrowserConfig(gpuBackend = GpuBackend.SOFTWARE)
    val flags = config.resolveCommandLineFlags()
    assertTrue(flags.contains("--disable-gpu"))
    assertTrue(flags.contains("--use-angle=swiftshader"))
    assertFalse(flags.contains("--enable-gpu-rasterization"))
  }

  @Test
  fun generateBundle_producesValidDirectWebContentsViewAndScripts() {
    val config =
      BrowserConfig(
        targetUrl = SPEEDOMETER_30_URL,
        cleanCookiesBeforeLaunch = true,
        cacheStrategy = CacheStrategy.WARM_DEFAULT,
        processPriority = ProcessPriority.HIGH,
      )
    val bundle = DesktopBundleGenerator.generateBundle(config)
    val byName = bundle.associateBy { it.filename }

    val mainMjs = byName.getValue("main.mjs").content
    assertTrue(mainMjs.contains("WebContentsView"))
    assertTrue(mainMjs.contains("BaseWindow"))
    assertTrue(mainMjs.contains("app.setPath('userData', tempProfileDir)"))
    assertTrue(mainMjs.contains(SPEEDOMETER_30_URL))

    val appJsx = byName.getValue("chrome-ui/App.jsx").content
    assertTrue(appJsx.contains("Speedometer 3.0"))
    assertTrue(appJsx.contains("toggle-chrome"))

    val winPs1 = byName.getValue("launch-windows.ps1").content
    assertTrue(winPs1.contains("\$TempProfile"))
    assertTrue(winPs1.contains("PriorityClass = \"High\""))

    val unixSh = byName.getValue("launch-unix.sh").content
    assertTrue(unixSh.contains("nice -n -12"))
    assertTrue(unixSh.contains("mktemp -d"))
  }

  @Test
  fun defaultConfig_prioritizesWarmRamAndCacheFirst() {
    val cfg = BrowserConfig()
    assertTrue(cfg.ramAssetCacheEnabled)
    assertTrue(cfg.keepWebViewWarm)
    assertFalse(cfg.cleanCookiesBeforeLaunch)
    assertFalse(cfg.offscreenPreRaster)
    assertTrue(cfg.cacheStrategy == CacheStrategy.CACHE_FIRST)
  }

  @Test
  fun ramCacheMimeParser_handlesJsMjsWasmAndCharset() {
    val js = com.example.data.BenchmarkRamCache.parseMimeAndEncoding(
      "https://browserbench.org/Speedometer3.0/resources/main.mjs?v=1",
      "application/javascript; charset=UTF-8",
    )
    assertTrue(js.first == "application/javascript")
    assertTrue(js.second == "UTF-8")

    val wasm = com.example.data.BenchmarkRamCache.parseMimeAndEncoding(
      "https://browserbench.org/Speedometer3.0/app.wasm",
      null,
    )
    assertTrue(wasm.first == "application/wasm")
  }

  private fun assertEqualsOnce(list: List<String>, value: String) {
    val count = list.count { it == value }
    assertTrue("Expected exactly 1 '$value', got $count", count == 1)
  }
}
