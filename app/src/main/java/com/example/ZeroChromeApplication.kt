package com.example

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import com.example.data.BenchmarkRamCache
import com.example.data.SPEEDOMETER_30_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class ZeroChromeApplication : Application() {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    prewarmWebViewStartup()
  }

  private fun prewarmWebViewStartup() {
    val bgExecutor = Executors.newSingleThreadExecutor()
    runCatching {
      val config = WebViewStartUpConfig.Builder(bgExecutor).build()
      WebViewCompat.startUpWebView(
        this,
        config,
        object : WebViewCompat.WebViewStartUpCallback {
          override fun onSuccess(result: WebViewStartUpResult) {
            Handler(Looper.getMainLooper()).post {
              warmChromiumProfile()
            }
          }
        },
      )
    }

    // Pre-populate RAM cache & warm HTTP/2 TLS socket to browserbench.org in background
    appScope.launch {
      BenchmarkRamCache.prewarmUrl(SPEEDOMETER_30_URL)
    }
  }

  private fun warmChromiumProfile() {
    runCatching {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        ProfileStore.getInstance().getProfile(Profile.DEFAULT_PROFILE_NAME)
      }
    }
  }
}
