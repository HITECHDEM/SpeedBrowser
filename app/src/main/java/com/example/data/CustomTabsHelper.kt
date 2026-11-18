package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

data class InstalledBrowserInfo(
  val packageName: String,
  val label: String,
)

object CustomTabsHelper {
  private var boundClient: CustomTabsClient? = null
  private var boundSession: CustomTabsSession? = null
  private var boundPackage: String? = null
  private var activeConnection: CustomTabsServiceConnection? = null

  fun queryCustomTabBrowsers(context: Context): List<InstalledBrowserInfo> {
    val pm = context.packageManager
    val serviceIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)
    val resolveInfos =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION")
        pm.queryIntentServices(serviceIntent, 0)
      }

    return resolveInfos
      .mapNotNull { info ->
        val pkg = info.serviceInfo?.packageName ?: return@mapNotNull null
        val appLabel =
          runCatching {
              val appInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                  @Suppress("DEPRECATION")
                  pm.getApplicationInfo(pkg, 0)
                }
              pm.getApplicationLabel(appInfo).toString()
            }
            .getOrDefault(pkg)
        InstalledBrowserInfo(packageName = pkg, label = appLabel)
      }
      .distinctBy { it.packageName }
      .sortedBy { it.label }
  }

  fun resolveTargetPackage(context: Context, preferredPackage: String?): String? {
    val available = queryCustomTabBrowsers(context).map { it.packageName }
    if (preferredPackage != null && available.contains(preferredPackage)) {
      return preferredPackage
    }
    return CustomTabsClient.getPackageName(context, available) ?: available.firstOrNull()
  }

  fun warmupAndPrefetch(context: Context, url: String, preferredPackage: String?) {
    val appCtx = context.applicationContext
    val targetPkg = resolveTargetPackage(appCtx, preferredPackage) ?: return

    if (boundPackage == targetPkg && boundClient != null) {
      runCatching {
        boundClient?.warmup(0L)
        boundSession?.mayLaunchUrl(Uri.parse(url), null, null)
      }
      return
    }

    unbind(appCtx)

    val conn =
      object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
          boundClient = client
          boundPackage = name.packageName
          runCatching {
            client.warmup(0L)
            val session = client.newSession(null)
            boundSession = session
            session?.mayLaunchUrl(Uri.parse(url), null, null)
          }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          boundClient = null
          boundSession = null
          boundPackage = null
        }
      }

    activeConnection = conn
    runCatching { CustomTabsClient.bindCustomTabsService(appCtx, targetPkg, conn) }
  }

  fun launchCustomTab(
    context: Context,
    url: String,
    preferredPackage: String?,
    compactSheetMode: Boolean,
  ): Boolean {
    val targetPkg = resolveTargetPackage(context, preferredPackage)
    val session = if (targetPkg == boundPackage) boundSession else null

    val builder =
      CustomTabsIntent.Builder(session)
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .setInstantAppsEnabled(false)
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)

    if (compactSheetMode) {
      val metrics = context.resources.displayMetrics
      val sheetHeightPx = (metrics.heightPixels * 0.78f).toInt().coerceAtLeast(480)
      builder.setInitialActivityHeightPx(
        sheetHeightPx,
        CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE,
      )
    }

    val intent = builder.build()
    if (!targetPkg.isNullOrBlank()) {
      intent.intent.setPackage(targetPkg)
    }

    return try {
      intent.launchUrl(context, Uri.parse(url))
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun unbind(appContext: Context) {
    activeConnection?.let { conn ->
      runCatching { appContext.unbindService(conn) }
    }
    activeConnection = null
    boundClient = null
    boundSession = null
    boundPackage = null
  }
}
