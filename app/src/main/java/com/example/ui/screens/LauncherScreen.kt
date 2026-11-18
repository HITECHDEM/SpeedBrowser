package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AndroidEngineMode
import com.example.data.BenchmarkPreset
import com.example.data.CacheStrategy
import com.example.data.SPEEDOMETER_30_URL
import com.example.data.SPEEDOMETER_31_URL
import com.example.ui.BenchUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherScreen(
  uiState: BenchUiState,
  onLaunchSpeedometer: () -> Unit,
  onPrewarmCache: () -> Unit,
  onWipeFullProfileNow: () -> Unit,
  onRefreshTelemetry: () -> Unit,
  onApplyPreset: (BenchmarkPreset) -> Unit,
  onUpdateUrl: (String) -> Unit,
  onSelectSpeedometerUrl: (String) -> Unit,
  onSelectEngine: (AndroidEngineMode) -> Unit,
  onSelectCustomTabPackage: (String?) -> Unit,
  onToggleCompactSheet: (Boolean) -> Unit,
  onSelectCacheStrategy: (CacheStrategy) -> Unit,
  onToggleRamCache: (Boolean) -> Unit,
  onToggleKeepWarm: (Boolean) -> Unit,
  onToggleOffscreenPreRaster: (Boolean) -> Unit,
  onToggleCleanCookies: (Boolean) -> Unit,
  onToggleZeroOverlay: (Boolean) -> Unit,
  onToggleMaxRefreshRate: (Boolean) -> Unit,
  onTogglePeakBurst: (Boolean) -> Unit,
  onNavigateToFlags: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val config = uiState.config
  val telemetry = uiState.deviceTelemetry
  val ramStats = uiState.ramCacheStats

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hero Command Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) {
          Box(modifier = Modifier.fillMaxWidth()) {
            Image(
              painter = painterResource(id = R.drawable.img_hero_banner_1795029212041),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              alpha = 0.72f,
              modifier = Modifier.matchParentSize(),
            )
            Box(
              modifier =
                Modifier.matchParentSize()
                  .background(
                    Brush.horizontalGradient(
                      colors =
                        listOf(
                          MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                          MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        )
                    )
                  )
            )
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = stringResource(R.string.hero_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }

              Text(
                text = stringResource(R.string.hero_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )

              Text(
                text = stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                TelemetryPill(
                  label =
                    if (config.ramAssetCacheEnabled)
                      "RAM Cache (${ramStats.assetCount} assets)"
                    else config.cacheStrategy.label,
                  accent = MaterialTheme.colorScheme.primary,
                )
                TelemetryPill(
                  label =
                    if (config.maxRefreshRateLock && telemetry != null)
                      "${telemetry.maxRefreshRateHz} Hz Lock"
                    else "Default Hz",
                  accent = MaterialTheme.colorScheme.tertiary,
                )
                TelemetryPill(
                  label = if (config.gpuBackend.isHardware) "Direct GPU" else "Software",
                  accent = MaterialTheme.colorScheme.secondary,
                )
              }

              Spacer(modifier = Modifier.height(4.dp))

              Button(
                onClick = onLaunchSpeedometer,
                enabled = !uiState.isCleaningProfile,
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("launch_speedometer_button"),
                shape = RoundedCornerShape(14.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
              ) {
                if (uiState.isCleaningProfile) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text("Prepping Cold Profile...")
                } else {
                  Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text =
                      if (config.targetUrl == SPEEDOMETER_30_URL)
                        stringResource(R.string.btn_launch_speedometer)
                      else stringResource(R.string.btn_launch_custom),
                    style = MaterialTheme.typography.labelLarge,
                  )
                }
              }
            }
          }
        }
      }

      // Real Device Hardware & Thermal Readiness Card
      if (telemetry != null) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = stringResource(R.string.section_device_readiness),
                    style = MaterialTheme.typography.titleMedium,
                  )
                }
                IconButton(
                  onClick = onRefreshTelemetry,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("refresh_telemetry_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.btn_refresh_telemetry),
                  )
                }
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TelemetryPill(
                  label = "${telemetry.cpuCores} CPU Cores",
                  accent = MaterialTheme.colorScheme.primary,
                )
                TelemetryPill(
                  label = "${telemetry.availableRamMb} / ${telemetry.totalRamMb} MB Free RAM",
                  accent = MaterialTheme.colorScheme.secondary,
                )
                TelemetryPill(
                  label =
                    "Display ${telemetry.currentRefreshRateHz}Hz (Max ${telemetry.maxRefreshRateHz}Hz)",
                  accent = MaterialTheme.colorScheme.tertiary,
                )
                TelemetryPill(
                  label = "Thermal: ${telemetry.thermalHealth.label}",
                  accent =
                    if (telemetry.thermalHealth.isThrottling) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary,
                )
              }

              Text(
                text =
                  "Engine: ${telemetry.webViewPackageName} (${telemetry.webViewVersion})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              if (telemetry.isPowerSaveMode || telemetry.thermalHealth.isThrottling) {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Warning,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text =
                        if (telemetry.isPowerSaveMode) stringResource(R.string.warning_power_save)
                        else stringResource(R.string.warning_thermal),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Quick Optimization Presets
      item {
        SectionHeader(
          title = stringResource(R.string.section_presets),
          actionLabel = "Tune Flags",
          onAction = onNavigateToFlags,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(BenchmarkPreset.entries, key = { it.id }) { preset ->
            Card(
              onClick = { onApplyPreset(preset) },
              modifier = Modifier.width(230.dp).testTag("preset_card_${preset.id}"),
              shape = RoundedCornerShape(16.dp),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = preset.subtitle,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      // Target URL bar with Speedometer 3.0 & 3.1 quick switch
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.label_target_url),
              style = MaterialTheme.typography.titleMedium,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = config.targetUrl == SPEEDOMETER_30_URL,
                onClick = { onSelectSpeedometerUrl(SPEEDOMETER_30_URL) },
                label = { Text("Speedometer 3.0") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("url_chip_30"),
              )
              FilterChip(
                selected = config.targetUrl == SPEEDOMETER_31_URL,
                onClick = { onSelectSpeedometerUrl(SPEEDOMETER_31_URL) },
                label = { Text("Speedometer 3.1") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("url_chip_31"),
              )
            }
            OutlinedTextField(
              value = config.targetUrl,
              onValueChange = onUpdateUrl,
              singleLine = true,
              textStyle = MaterialTheme.typography.labelMedium,
              trailingIcon = {
                if (config.targetUrl != SPEEDOMETER_30_URL) {
                  IconButton(
                    onClick = { onSelectSpeedometerUrl(SPEEDOMETER_30_URL) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("reset_url_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Refresh,
                      contentDescription = stringResource(R.string.btn_reset_url),
                    )
                  }
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("target_url_input"),
              shape = RoundedCornerShape(12.dp),
            )
          }
        }
      }

      // HTTP, RAM & V8 Code Caching Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Cached,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = stringResource(R.string.section_caching),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CacheStrategy.entries.forEach { strategy ->
                FilterChip(
                  selected = config.cacheStrategy == strategy,
                  onClick = { onSelectCacheStrategy(strategy) },
                  label = { Text(strategy.label) },
                  modifier =
                    Modifier.heightIn(min = 48.dp)
                      .testTag("cache_chip_${strategy.name.lowercase()}"),
                )
              }
            }

            Text(
              text = config.cacheStrategy.description,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val mbUsed = String.format(Locale.US, "%.2f", ramStats.cachedBytes / (1024f * 1024f))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              TelemetryPill(
                label = "${ramStats.assetCount} Assets ($mbUsed / 96 MB)",
                accent = MaterialTheme.colorScheme.secondary,
              )
              TelemetryPill(
                label = "${ramStats.hitCount} RAM Hits / ${ramStats.missCount} Misses",
                accent = MaterialTheme.colorScheme.primary,
              )
            }

            SettingSwitchRow(
              title = stringResource(R.string.toggle_ram_cache),
              subtitle = stringResource(R.string.toggle_ram_cache_desc),
              checked = config.ramAssetCacheEnabled,
              onCheckedChange = onToggleRamCache,
              testTag = "ram_cache_switch",
            )

            SettingSwitchRow(
              title = stringResource(R.string.toggle_keep_warm),
              subtitle = stringResource(R.string.toggle_keep_warm_desc),
              checked = config.keepWebViewWarm,
              onCheckedChange = onToggleKeepWarm,
              testTag = "keep_warm_switch",
            )

            OutlinedButton(
              onClick = onPrewarmCache,
              enabled = !ramStats.isPrewarming,
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("prewarm_cache_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              if (ramStats.isPrewarming) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pre-warming RAM Cache...")
              } else {
                Icon(
                  imageVector = Icons.Rounded.Bolt,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_prewarm_cache))
              }
            }
          }
        }
      }

      // Android Engine Mode
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.OpenInBrowser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.section_android_engine),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              AndroidEngineMode.entries.forEach { mode ->
                FilterChip(
                  selected = config.androidEngineMode == mode,
                  onClick = { onSelectEngine(mode) },
                  label = { Text(mode.label) },
                  modifier =
                    Modifier.weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("engine_chip_${mode.name.lowercase()}"),
                )
              }
            }

            Text(
              text = config.androidEngineMode.description,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = config.androidEngineMode == AndroidEngineMode.CUSTOM_TAB) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uiState.installedBrowsers.isNotEmpty()) {
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    FilterChip(
                      selected = config.preferredCustomTabPackage == null,
                      onClick = { onSelectCustomTabPackage(null) },
                      label = { Text("Auto Default") },
                      modifier = Modifier.heightIn(min = 48.dp).testTag("browser_chip_auto"),
                    )
                    uiState.installedBrowsers.forEach { browser ->
                      FilterChip(
                        selected = config.preferredCustomTabPackage == browser.packageName,
                        onClick = { onSelectCustomTabPackage(browser.packageName) },
                        label = { Text(browser.label) },
                        modifier =
                          Modifier.heightIn(min = 48.dp)
                            .testTag("browser_chip_${browser.packageName}"),
                      )
                    }
                  }
                }
                SettingSwitchRow(
                  title = stringResource(R.string.toggle_compact_sheet),
                  subtitle = stringResource(R.string.toggle_compact_sheet_desc),
                  checked = config.compactCustomTabSheet,
                  onCheckedChange = onToggleCompactSheet,
                  testTag = "compact_sheet_switch",
                )
              }
            }
          }
        }
      }

      // JVM, Display & Device Power Hygiene
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
              )
              Text(
                text = stringResource(R.string.section_power_java),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            SettingSwitchRow(
              title = stringResource(R.string.toggle_max_refresh),
              subtitle = stringResource(R.string.toggle_max_refresh_desc),
              checked = config.maxRefreshRateLock,
              onCheckedChange = onToggleMaxRefreshRate,
              testTag = "max_refresh_switch",
            )

            SettingSwitchRow(
              title = stringResource(R.string.toggle_peak_burst),
              subtitle = stringResource(R.string.toggle_peak_burst_desc),
              checked = config.peakBurstPerformance,
              onCheckedChange = onTogglePeakBurst,
              testTag = "peak_burst_switch",
            )

            SettingSwitchRow(
              title = stringResource(R.string.toggle_zero_overlay),
              subtitle = stringResource(R.string.toggle_zero_overlay_desc),
              checked = config.zeroOverlayPureMode,
              onCheckedChange = onToggleZeroOverlay,
              icon = Icons.Rounded.VisibilityOff,
              testTag = "zero_overlay_switch",
            )

            SettingSwitchRow(
              title = stringResource(R.string.toggle_offscreen_preraster),
              subtitle = stringResource(R.string.toggle_offscreen_preraster_desc),
              checked = config.offscreenPreRaster,
              onCheckedChange = onToggleOffscreenPreRaster,
              testTag = "offscreen_preraster_switch",
            )

            SettingSwitchRow(
              title = stringResource(R.string.toggle_clean_cookies),
              subtitle = stringResource(R.string.toggle_clean_cookies_desc),
              checked = config.cleanCookiesBeforeLaunch,
              onCheckedChange = onToggleCleanCookies,
              testTag = "clean_profile_switch",
            )

            OutlinedButton(
              onClick = onWipeFullProfileNow,
              enabled = !uiState.isCleaningProfile,
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wipe_profile_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.CleaningServices,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_wipe_profile_now))
            }

            AnimatedVisibility(visible = uiState.lastCleanResult != null) {
              uiState.lastCleanResult?.let { result ->
                Surface(
                  color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.CheckCircle,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text =
                        "${stringResource(R.string.status_profile_cleaned)} (${result.bytesReclaimed / 1024} KB freed)",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
