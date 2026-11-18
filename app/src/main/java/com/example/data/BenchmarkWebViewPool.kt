package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Holds a warm, reusable Chromium WebView instance backed by a MutableContextWrapper
 * so the Chromium renderer process, V8 isolate, Blink MemoryCache, and GPU shader cache
 * remain hot across consecutive benchmark launches without leaking any Activity context.
 */
object BenchmarkWebViewPool {
  private var contextWrapper: MutableContextWrapper? = null
  private var pooledWebView: WebView? = null
  private var cachedUserAgent: String? = null

  @SuppressLint("SetJavaScriptEnabled")
  fun acquire(
    activityContext: Context,
    config: BrowserConfig,
    targetUrl: String,
  ): WebView {
    val appCtx = activityContext.applicationContext
    val coldMode = config.cacheStrategy == CacheStrategy.COLD_NO_CACHE

    val webView =
      if (config.keepWebViewWarm && !coldMode && pooledWebView != null && contextWrapper != null) {
        contextWrapper!!.baseContext = activityContext
        pooledWebView!!.also { existing ->
          (existing.parent as? ViewGroup)?.removeView(existing)
        }
      } else {
        destroyInternal()
        val wrapper = MutableContextWrapper(activityContext)
        contextWrapper = wrapper
        WebView(wrapper).also { created ->
          pooledWebView = created
        }
      }

    configureWebView(
      webView = webView,
      appContext = appCtx,
      config = config,
      targetHost = runCatching { Uri.parse(targetUrl).host }.getOrNull(),
    )
    return webView
  }

  fun release(
    webView: WebView,
    appContext: Context,
    keepWarm: Boolean,
    coldReset: Boolean,
  ) {
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.webChromeClient = null
    webView.stopLoading()

    if (!keepWarm || coldReset) {
      if (coldReset) {
        runCatching {
          webView.clearCache(true)
          webView.clearHistory()
          webView.clearFormData()
        }
      }
      destroyInternal()
    } else {
      // Park at about:blank so active JS timers stop while keeping renderer/V8/GPU shaders hot
      webView.loadUrl("about:blank")
      contextWrapper?.baseContext = appContext.applicationContext
    }
  }

  fun destroyPool() {
    destroyInternal()
  }

  fun currentUserAgent(): String? = cachedUserAgent

  private fun destroyInternal() {
    pooledWebView?.let { wv ->
      (wv.parent as? ViewGroup)?.removeView(wv)
      runCatching {
        wv.stopLoading()
        wv.destroy()
      }
    }
    pooledWebView = null
    contextWrapper = null
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun configureWebView(
    webView: WebView,
    appContext: Context,
    config: BrowserConfig,
    targetHost: String?,
  ) {
    val isHardware = config.gpuBackend.isHardware
    webView.keepScreenOn = config.peakBurstPerformance
    webView.setBackgroundColor(AndroidColor.WHITE)
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false
    webView.overScrollMode = View.OVER_SCROLL_NEVER

    // CRITICAL: Use LAYER_TYPE_NONE (normal hardware acceleration) instead of LAYER_TYPE_HARDWARE,
    // which otherwise allocates an extra offscreen GPU FBO texture copy on every frame.
    val desiredLayer = if (isHardware) View.LAYER_TYPE_NONE else View.LAYER_TYPE_SOFTWARE
    if (webView.layerType != desiredLayer) {
      webView.setLayerType(desiredLayer, null)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
    }

    webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      useWideViewPort = true
      loadWithOverviewMode = true
      textZoom = 100
      loadsImagesAutomatically = true
      mediaPlaybackRequiresUserGesture = false
      setNeedInitialFocus(false)
      // Disable zoom controller / double-tap zoom gesture overhead during benchmark runs
      setSupportZoom(false)
      builtInZoomControls = false
      displayZoomControls = false
      offscreenPreRaster = isHardware && config.offscreenPreRaster
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        safeBrowsingEnabled = false
      }
      cacheMode =
        when (config.cacheStrategy) {
          CacheStrategy.WARM_DEFAULT -> WebSettings.LOAD_DEFAULT
          CacheStrategy.CACHE_FIRST -> WebSettings.LOAD_CACHE_ELSE_NETWORK
          CacheStrategy.COLD_NO_CACHE -> WebSettings.LOAD_NO_CACHE
        }
      if (cachedUserAgent == null) {
        cachedUserAgent = userAgentString
      }
    }

    runCatching {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
      }
    }

    val useRamCache =
      config.ramAssetCacheEnabled && config.cacheStrategy != CacheStrategy.COLD_NO_CACHE

    webView.webViewClient =
      object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
          view: WebView?,
          request: WebResourceRequest?,
        ): Boolean = false

        override fun shouldInterceptRequest(
          view: WebView?,
          request: WebResourceRequest?,
        ): WebResourceResponse? {
          if (!useRamCache || request == null) return null
          if (!BenchmarkRamCache.shouldIntercept(request, targetHost)) return null
          return BenchmarkRamCache.interceptOrFetch(request, cachedUserAgent)
        }

        override fun onRenderProcessGone(
          view: WebView?,
          detail: RenderProcessGoneDetail?,
        ): Boolean {
          // Renderer was reclaimed by OS or crashed: clean up pooled WebView safely
          if (view === pooledWebView) {
            pooledWebView = null
            contextWrapper = null
          }
          (view?.parent as? ViewGroup)?.removeView(view)
          runCatching { view?.destroy() }
          return true
        }
      }
  }
}
