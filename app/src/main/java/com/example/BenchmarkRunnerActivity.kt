package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.PerformanceHintManager
import android.os.Process
import android.text.TextUtils
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.data.BenchmarkRamCache
import com.example.data.BenchmarkWebViewPool
import com.example.data.BrowserConfig
import com.example.data.CacheStrategy
import com.example.data.DeviceTelemetry
import com.example.data.GpuBackend
import com.example.data.ProcessPriority
import com.example.data.SPEEDOMETER_30_URL

/**
 * Pure Android View runner activity with ZERO Compose / NavHost / AndroidViewHolder overhead.
 * Hosts the pooled Chromium WebView directly inside a minimal FrameLayout so Speedometer
 * executes without Compose recomposition or View-interop passes.
 */
class BenchmarkRunnerActivity : ComponentActivity() {

  private var webView: WebView? = null
  private var toolbarView: LinearLayout? = null
  private var titleView: TextView? = null
  private var subtitleView: TextView? = null
  private var progressBar: ProgressBar? = null
  private var chromeHidden: Boolean = true
  private var keepWebViewWarm: Boolean = true
  private var coldResetOnExit: Boolean = false
  private var prevDisplayModeId: Int = 0
  private var hintSession: PerformanceHintManager.Session? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val targetUrl = intent.getStringExtra(EXTRA_URL).orEmpty().ifBlank { SPEEDOMETER_30_URL }
    val isHardwareGpu = intent.getBooleanExtra(EXTRA_HARDWARE_GPU, true)
    val cacheStrategy =
      intent.getStringExtra(EXTRA_CACHE_STRATEGY)?.let { name ->
        CacheStrategy.entries.firstOrNull { it.name == name }
      } ?: CacheStrategy.CACHE_FIRST
    val zeroOverlay = intent.getBooleanExtra(EXTRA_ZERO_OVERLAY, true)
    val elevatePriority = intent.getBooleanExtra(EXTRA_ELEVATE_PRIORITY, true)
    val maxRefreshRateLock = intent.getBooleanExtra(EXTRA_MAX_REFRESH_LOCK, true)
    val peakBurst = intent.getBooleanExtra(EXTRA_PEAK_BURST, true)
    val ramCacheEnabled = intent.getBooleanExtra(EXTRA_RAM_CACHE, true)
    keepWebViewWarm = intent.getBooleanExtra(EXTRA_KEEP_WARM, true)
    val offscreenPreRaster = intent.getBooleanExtra(EXTRA_OFFSCREEN_PRE_RASTER, false)
    coldResetOnExit = cacheStrategy == CacheStrategy.COLD_NO_CACHE
    chromeHidden = zeroOverlay

    applyOsPerformanceBoost(elevatePriority, maxRefreshRateLock, peakBurst)

    val config =
      BrowserConfig(
        targetUrl = targetUrl,
        cacheStrategy = cacheStrategy,
        zeroOverlayPureMode = zeroOverlay,
        maxRefreshRateLock = maxRefreshRateLock,
        peakBurstPerformance = peakBurst,
        processPriority = if (elevatePriority) ProcessPriority.HIGH else ProcessPriority.NORMAL,
        gpuBackend = if (isHardwareGpu) GpuBackend.AUTO else GpuBackend.SOFTWARE,
        ramAssetCacheEnabled = ramCacheEnabled,
        keepWebViewWarm = keepWebViewWarm,
        offscreenPreRaster = offscreenPreRaster,
      )

    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AndroidColor.parseColor("#0B0F17"))
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }

    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }

    val toolbar = buildToolbar(targetUrl, isHardwareGpu, cacheStrategy, ramCacheEnabled)
    toolbar.visibility = if (chromeHidden) View.GONE else View.VISIBLE
    toolbarView = toolbar
    root.addView(toolbar)

    val pBar =
      ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
      }
    progressBar = pBar
    root.addView(pBar)

    val wv = BenchmarkWebViewPool.acquire(this, config, targetUrl)
    wv.layoutParams =
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    wv.webChromeClient =
      object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, title: String?) {
          if (!chromeHidden && !title.isNullOrBlank()) {
            titleView?.text = title
          }
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
          if (chromeHidden) return
          if (newProgress in 1..99) {
            progressBar?.visibility = View.VISIBLE
            progressBar?.progress = newProgress
          } else {
            progressBar?.visibility = View.GONE
          }
        }
      }

    webView = wv
    root.addView(wv)
    setContentView(root)

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          when {
            chromeHidden -> setChromeHidden(false)
            webView?.canGoBack() == true -> webView?.goBack()
            else -> finish()
          }
        }
      },
    )

    wv.loadUrl(targetUrl)
  }

  override fun onDestroy() {
    webView?.let { wv ->
      BenchmarkWebViewPool.release(
        webView = wv,
        appContext = applicationContext,
        keepWarm = keepWebViewWarm,
        coldReset = coldResetOnExit,
      )
    }
    webView = null
    BenchmarkRamCache.refreshStats()
    restoreOsPerformanceState()
    super.onDestroy()
  }

  private fun setChromeHidden(hidden: Boolean) {
    chromeHidden = hidden
    toolbarView?.visibility = if (hidden) View.GONE else View.VISIBLE
    if (hidden) {
      progressBar?.visibility = View.GONE
    }
  }

  private fun applyOsPerformanceBoost(
    elevatePriority: Boolean,
    maxRefreshRateLock: Boolean,
    peakBurst: Boolean,
  ) {
    if (elevatePriority || peakBurst) {
      runCatching {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        Thread.currentThread().priority = Thread.MAX_PRIORITY
      }
    }

    prevDisplayModeId = window.attributes.preferredDisplayModeId
    var targetHz = 60

    if (peakBurst) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      runCatching { window.setSustainedPerformanceMode(false) }
    }

    val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
    val bestMode = DeviceTelemetry.findPeakRefreshMode(display)
    if (bestMode != null) {
      targetHz = bestMode.refreshRate.toInt().coerceAtLeast(60)
      if (maxRefreshRateLock && bestMode.modeId != prevDisplayModeId) {
        val lp = window.attributes
        lp.preferredDisplayModeId = bestMode.modeId
        window.attributes = lp
      }
    }

    // Engage ADPF PerformanceHintManager session on Android 12+ so PowerHAL favors prime cores
    if (peakBurst && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      runCatching {
        val phm = getSystemService(PerformanceHintManager::class.java)
        val targetDurationNanos = 1_000_000_000L / targetHz.toLong()
        hintSession = phm?.createHintSession(intArrayOf(Process.myTid()), targetDurationNanos)
        hintSession?.reportActualWorkDuration(targetDurationNanos)
      }
    }
  }

  private fun restoreOsPerformanceState() {
    runCatching {
      hintSession?.close()
      hintSession = null
    }
    runCatching {
      Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
      Thread.currentThread().priority = Thread.NORM_PRIORITY
    }
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (window.attributes.preferredDisplayModeId != prevDisplayModeId) {
      val lp = window.attributes
      lp.preferredDisplayModeId = prevDisplayModeId
      window.attributes = lp
    }
  }

  private fun buildToolbar(
    targetUrl: String,
    isHardwareGpu: Boolean,
    cacheStrategy: CacheStrategy,
    ramCacheEnabled: Boolean,
  ): LinearLayout {
    val bar =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(AndroidColor.parseColor("#111824"))
        setPadding(dp(4), dp(2), dp(4), dp(2))
        layoutParams =
          LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          )
      }

    fun makeActionBtn(iconRes: Int, desc: String, onClick: () -> Unit): ImageButton =
      ImageButton(this).apply {
        setImageResource(iconRes)
        contentDescription = desc
        setColorFilter(AndroidColor.parseColor("#E2E8F0"))
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { onClick() }
      }

    bar.addView(
      makeActionBtn(android.R.drawable.ic_media_previous, getString(R.string.cd_back)) {
        if (webView?.canGoBack() == true) webView?.goBack() else finish()
      }
    )
    bar.addView(
      makeActionBtn(android.R.drawable.ic_popup_sync, getString(R.string.cd_reload)) {
        webView?.reload()
      }
    )

    val textContainer =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6), 0, dp(6), 0)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      }

    val titleTv =
      TextView(this).apply {
        text = targetUrl
        setTextColor(AndroidColor.parseColor("#F1F5F9"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
      }
    titleView = titleTv

    val subtitleTv =
      TextView(this).apply {
        val gpuStr =
          if (isHardwareGpu) getString(R.string.msg_gpu_hardware)
          else getString(R.string.msg_gpu_software)
        val ramStr = if (ramCacheEnabled) " • RAM Cache" else ""
        text = "$gpuStr • ${cacheStrategy.label}$ramStr"
        setTextColor(AndroidColor.parseColor("#38BDF8"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
      }
    subtitleView = subtitleTv

    textContainer.addView(titleTv)
    textContainer.addView(subtitleTv)
    bar.addView(textContainer)

    bar.addView(
      makeActionBtn(android.R.drawable.ic_menu_view, getString(R.string.cd_hide_chrome)) {
        setChromeHidden(true)
      }
    )
    bar.addView(
      makeActionBtn(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cd_exit_runner)) {
        finish()
      }
    )

    return bar
  }

  private fun dp(value: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
      )
      .toInt()

  companion object {
    private const val EXTRA_URL = "extra_url"
    private const val EXTRA_HARDWARE_GPU = "extra_hardware_gpu"
    private const val EXTRA_CACHE_STRATEGY = "extra_cache_strategy"
    private const val EXTRA_ZERO_OVERLAY = "extra_zero_overlay"
    private const val EXTRA_ELEVATE_PRIORITY = "extra_elevate_priority"
    private const val EXTRA_MAX_REFRESH_LOCK = "extra_max_refresh_lock"
    private const val EXTRA_PEAK_BURST = "extra_peak_burst"
    private const val EXTRA_RAM_CACHE = "extra_ram_cache"
    private const val EXTRA_KEEP_WARM = "extra_keep_warm"
    private const val EXTRA_OFFSCREEN_PRE_RASTER = "extra_offscreen_pre_raster"

    fun createIntent(
      context: Context,
      url: String,
      config: BrowserConfig,
    ): Intent =
      Intent(context, BenchmarkRunnerActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
        putExtra(EXTRA_HARDWARE_GPU, config.gpuBackend.isHardware)
        putExtra(EXTRA_CACHE_STRATEGY, config.cacheStrategy.name)
        putExtra(EXTRA_ZERO_OVERLAY, config.zeroOverlayPureMode)
        putExtra(EXTRA_ELEVATE_PRIORITY, config.processPriority != ProcessPriority.NORMAL)
        putExtra(EXTRA_MAX_REFRESH_LOCK, config.maxRefreshRateLock)
        putExtra(EXTRA_PEAK_BURST, config.peakBurstPerformance)
        putExtra(EXTRA_RAM_CACHE, config.ramAssetCacheEnabled)
        putExtra(EXTRA_KEEP_WARM, config.keepWebViewWarm)
        putExtra(EXTRA_OFFSCREEN_PRE_RASTER, config.offscreenPreRaster)
      }
  }
}
