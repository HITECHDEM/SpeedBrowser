package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AndroidEngineMode
import com.example.data.CustomTabsHelper
import com.example.data.SPEEDOMETER_30_URL
import com.example.ui.BenchViewModel
import com.example.ui.screens.DesktopBundleScreen
import com.example.ui.screens.FlagsScreen
import com.example.ui.screens.LauncherScreen
import com.example.ui.theme.ZeroChromeTheme
import kotlinx.coroutines.launch

const val ROUTE_LAUNCHER = "launcher"
const val ROUTE_FLAGS = "flags"
const val ROUTE_DESKTOP = "desktop"

private data class TopLevelTab(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val TOP_LEVEL_TABS =
  listOf(
    TopLevelTab(
      route = ROUTE_LAUNCHER,
      labelRes = R.string.nav_launch,
      selectedIcon = Icons.Filled.RocketLaunch,
      unselectedIcon = Icons.Outlined.RocketLaunch,
      testTag = "nav_tab_launch",
    ),
    TopLevelTab(
      route = ROUTE_FLAGS,
      labelRes = R.string.nav_flags,
      selectedIcon = Icons.Filled.Tune,
      unselectedIcon = Icons.Outlined.Tune,
      testTag = "nav_tab_flags",
    ),
    TopLevelTab(
      route = ROUTE_DESKTOP,
      labelRes = R.string.nav_desktop,
      selectedIcon = Icons.Filled.Code,
      unselectedIcon = Icons.Outlined.Code,
      testTag = "nav_tab_desktop",
    ),
  )

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { ZeroChromeTheme { ZeroChromeApp() } }
  }
}

@Composable
fun ZeroChromeApp(viewModel: BenchViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val customTabFallbackMsg = stringResource(R.string.msg_custom_tab_fallback)

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTelemetry()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  fun showMessage(msg: String) {
    scope.launch { snackbarHostState.showSnackbar(msg) }
  }

  fun launchBenchmark() {
    scope.launch {
      viewModel.prepareForBenchmarkLaunch()
      val cfg = uiState.config
      val normalizedUrl =
        cfg.targetUrl.trim().ifBlank { SPEEDOMETER_30_URL }.let { raw ->
          if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        }

      if (cfg.androidEngineMode == AndroidEngineMode.CUSTOM_TAB) {
        val launched =
          CustomTabsHelper.launchCustomTab(
            context = context,
            url = normalizedUrl,
            preferredPackage = cfg.preferredCustomTabPackage,
            compactSheetMode = cfg.compactCustomTabSheet,
          )
        if (!launched) {
          showMessage(customTabFallbackMsg)
          context.startActivity(BenchmarkRunnerActivity.createIntent(context, normalizedUrl, cfg))
        }
      } else {
        context.startActivity(BenchmarkRunnerActivity.createIntent(context, normalizedUrl, cfg))
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar {
            TOP_LEVEL_TABS.forEach { tab ->
              val selected =
                backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
              NavigationBarItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail {
            TOP_LEVEL_TABS.forEach { tab ->
              val selected =
                backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
              NavigationRailItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = ROUTE_LAUNCHER,
          modifier = Modifier.weight(1f),
        ) {
          composable(ROUTE_LAUNCHER) {
            LauncherScreen(
              uiState = uiState,
              onLaunchSpeedometer = ::launchBenchmark,
              onPrewarmCache = viewModel::prewarmTargetAndCustomTabs,
              onWipeFullProfileNow = { viewModel.wipeFullProfileNow() },
              onRefreshTelemetry = viewModel::refreshTelemetry,
              onApplyPreset = viewModel::applyPreset,
              onUpdateUrl = viewModel::updateTargetUrl,
              onSelectSpeedometerUrl = viewModel::selectSpeedometerUrl,
              onSelectEngine = viewModel::setAndroidEngineMode,
              onSelectCustomTabPackage = viewModel::setPreferredCustomTabPackage,
              onToggleCompactSheet = viewModel::toggleCompactCustomTabSheet,
              onSelectCacheStrategy = viewModel::setCacheStrategy,
              onToggleRamCache = viewModel::toggleRamAssetCache,
              onToggleKeepWarm = viewModel::toggleKeepWebViewWarm,
              onToggleOffscreenPreRaster = viewModel::toggleOffscreenPreRaster,
              onToggleCleanCookies = viewModel::toggleCleanCookiesBeforeLaunch,
              onToggleZeroOverlay = viewModel::toggleZeroOverlayPureMode,
              onToggleMaxRefreshRate = viewModel::toggleMaxRefreshRateLock,
              onTogglePeakBurst = viewModel::togglePeakBurstPerformance,
              onNavigateToFlags = {
                navController.navigate(ROUTE_FLAGS) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
            )
          }

          composable(ROUTE_FLAGS) {
            FlagsScreen(
              uiState = uiState,
              onSelectPriority = viewModel::setProcessPriority,
              onSelectGpu = viewModel::setGpuBackend,
              onToggleFlag = viewModel::toggleFlag,
              onUpdateCustomFlags = viewModel::updateCustomFlags,
            )
          }

          composable(ROUTE_DESKTOP) {
            DesktopBundleScreen(uiState = uiState, onShowMessage = ::showMessage)
          }
        }
      }
    }
  }
}
