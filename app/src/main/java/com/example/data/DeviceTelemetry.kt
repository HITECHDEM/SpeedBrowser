package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.webkit.WebView
import kotlin.math.roundToInt

enum class ThermalHealth(val label: String, val isThrottling: Boolean) {
  NOMINAL("Nominal (Peak Clocks)", false),
  LIGHT("Light Warmth", false),
  MODERATE("Moderate (May Throttle)", true),
  SEVERE("Severe Thermal Throttling", true),
  UNKNOWN("Unavailable", false),
}

data class DeviceReadinessSnapshot(
  val cpuCores: Int,
  val availableRamMb: Long,
  val totalRamMb: Long,
  val maxRefreshRateHz: Int,
  val currentRefreshRateHz: Int,
  val isPowerSaveMode: Boolean,
  val thermalHealth: ThermalHealth,
  val webViewPackageName: String,
  val webViewVersion: String,
)

object DeviceTelemetry {

  fun capture(context: Context): DeviceReadinessSnapshot {
    val cpuCores = Runtime.getRuntime().availableProcessors()

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    val availMb = memInfo.availMem / (1024L * 1024L)
    val totalMb = memInfo.totalMem / (1024L * 1024L)

    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val powerSave = pm?.isPowerSaveMode == true
    val thermal =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
        when (pm.currentThermalStatus) {
          PowerManager.THERMAL_STATUS_NONE -> ThermalHealth.NOMINAL
          PowerManager.THERMAL_STATUS_LIGHT -> ThermalHealth.LIGHT
          PowerManager.THERMAL_STATUS_MODERATE -> ThermalHealth.MODERATE
          PowerManager.THERMAL_STATUS_SEVERE,
          PowerManager.THERMAL_STATUS_CRITICAL,
          PowerManager.THERMAL_STATUS_EMERGENCY,
          PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalHealth.SEVERE
          else -> ThermalHealth.UNKNOWN
        }
      } else {
        ThermalHealth.UNKNOWN
      }

    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display: Display? = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    val currentHz = display?.refreshRate?.roundToInt() ?: 60
    val maxHz =
      display?.supportedModes?.maxOfOrNull { it.refreshRate }?.roundToInt()?.coerceAtLeast(currentHz)
        ?: currentHz

    val webViewPkg =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WebView.getCurrentWebViewPackage()
      } else {
        null
      }

    return DeviceReadinessSnapshot(
      cpuCores = cpuCores,
      availableRamMb = availMb,
      totalRamMb = totalMb,
      maxRefreshRateHz = maxHz,
      currentRefreshRateHz = currentHz,
      isPowerSaveMode = powerSave,
      thermalHealth = thermal,
      webViewPackageName = webViewPkg?.packageName ?: "System WebView",
      webViewVersion = webViewPkg?.versionName ?: "Default",
    )
  }

  /**
   * Finds the Display.Mode with highest refresh rate matching current resolution
   * so Window.attributes.preferredDisplayModeId can unlock 90Hz/120Hz/144Hz rAF pacing.
   */
  fun findPeakRefreshMode(display: Display?): Display.Mode? {
    if (display == null) return null
    val currentMode = display.mode
    val sameResModes =
      display.supportedModes.filter {
        it.physicalWidth == currentMode.physicalWidth &&
          it.physicalHeight == currentMode.physicalHeight
      }
    return (sameResModes.ifEmpty { display.supportedModes.toList() })
      .maxByOrNull { it.refreshRate }
  }

  fun findPeakRefreshModeId(display: Display?): Int? = findPeakRefreshMode(display)?.modeId
}
