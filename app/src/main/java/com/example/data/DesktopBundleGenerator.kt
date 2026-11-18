package com.example.data

data class GeneratedDesktopFile(
  val filename: String,
  val language: String,
  val summary: String,
  val content: String,
)

object DesktopBundleGenerator {

  fun generateBundle(config: BrowserConfig): List<GeneratedDesktopFile> {
    val flags = config.resolveCommandLineFlags()
    return listOf(
      GeneratedDesktopFile(
        filename = "main.mjs",
        language = "JavaScript (Electron Main)",
        summary =
          "Direct BaseWindow + dual WebContentsView host. React renders only the 44px chrome; target page runs directly in Chromium.",
        content = generateMainMjs(config, flags),
      ),
      GeneratedDesktopFile(
        filename = "chrome-ui/App.jsx",
        language = "React (Browser Chrome Only)",
        summary =
          "Ultra-minimal 44px React toolbar with one-click Speedometer 3.1 & pure zero-overlay toggle.",
        content = generateReactChromeApp(config),
      ),
      GeneratedDesktopFile(
        filename = "chrome-ui/index.html",
        language = "HTML",
        summary = "Minimal React chrome mount point (never wraps the target webpage).",
        content = generateChromeIndexHtml(),
      ),
      GeneratedDesktopFile(
        filename = "package.json",
        language = "JSON",
        summary = "Minimal dependencies: Electron + React only, zero extensions or devtools packages.",
        content = generatePackageJson(),
      ),
      GeneratedDesktopFile(
        filename = "launch-windows.ps1",
        language = "PowerShell",
        summary = "Launches Chrome/Chromium directly with High/Realtime priority, temp profile & cleanup.",
        content = generateWindowsScript(config, flags),
      ),
      GeneratedDesktopFile(
        filename = "launch-unix.sh",
        language = "Bash",
        summary = "Launches Chromium/Chrome on macOS or Linux with nice priority & mktemp ephemeral profile.",
        content = generateUnixScript(config, flags),
      ),
    )
  }

  private fun generateMainMjs(config: BrowserConfig, flags: List<String>): String {
    val switchLines =
      flags.joinToString("\n") { raw ->
        val trimmed = raw.removePrefix("--")
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx > 0) {
          val k = trimmed.substring(0, eqIdx)
          val v = trimmed.substring(eqIdx + 1)
          "app.commandLine.appendSwitch('$k', '$v');"
        } else {
          "app.commandLine.appendSwitch('$trimmed');"
        }
      }

    val osPriorityValue =
      when (config.processPriority) {
        ProcessPriority.NORMAL -> "os.constants.priority.PRIORITY_NORMAL"
        ProcessPriority.ABOVE_NORMAL -> "os.constants.priority.PRIORITY_ABOVE_NORMAL"
        ProcessPriority.HIGH -> "os.constants.priority.PRIORITY_HIGH"
        ProcessPriority.REALTIME -> "os.constants.priority.PRIORITY_HIGHEST"
      }

    val useTempProfile =
      config.cleanCookiesBeforeLaunch || config.cacheStrategy == CacheStrategy.COLD_NO_CACHE

    val tempProfileBlock =
      if (useTempProfile) {
        """
const tempProfileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'zerochrome-'));
app.setPath('userData', tempProfileDir);
        """.trimIndent()
      } else {
        "// Warm persistent cache profile enabled for maximum V8/HTTP code-cache hit rate"
      }

    val cleanupBlock =
      if (useTempProfile) {
        """
app.on('will-quit', () => {
  try { fs.rmSync(tempProfileDir, { recursive: true, force: true }); } catch {}
});
        """.trimIndent()
      } else {
        ""
      }

    val startUrl = quoteJs(config.targetUrl.ifBlank { SPEEDOMETER_30_URL })
    val initialChromeVisible = !config.zeroOverlayPureMode

    return """
import { app, BaseWindow, WebContentsView, ipcMain } from 'electron';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const START_URL = $startUrl;
const CHROME_HEIGHT = 44;

// 1. Profile directory strategy
$tempProfileBlock

// 2. Apply Chromium command-line optimizations BEFORE app ready
$switchLines

// 3. Elevate process scheduling priority
try {
  os.setPriority(process.pid, $osPriorityValue);
} catch {
  // Ignore if OS requires elevated privileges for realtime
}

let mainWindow;
let chromeView;
let pageView;
let chromeVisible = $initialChromeVisible;

function layoutViews() {
  if (!mainWindow) return;
  const [width, height] = mainWindow.getContentSize();
  const topOffset = chromeVisible ? CHROME_HEIGHT : 0;
  chromeView.setBounds({ x: 0, y: 0, width, height: topOffset });
  pageView.setBounds({ x: 0, y: topOffset, width, height: Math.max(0, height - topOffset) });
}

app.whenReady().then(() => {
  mainWindow = new BaseWindow({
    width: 1280,
    height: 860,
    backgroundColor: '#0b0f17',
    autoHideMenuBar: true,
    title: 'ZeroChrome',
  });

  // React UI runs ONLY in this thin 44px chrome view
  chromeView = new WebContentsView({
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
      spellcheck: false,
      devTools: false,
    },
  });

  // Target webpage runs DIRECTLY in Chromium's compositor (no wrapper app layer)
  pageView = new WebContentsView({
    webPreferences: {
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false,
      spellcheck: false,
      devTools: false,
    },
  });

  mainWindow.contentView.addChildView(pageView);
  mainWindow.contentView.addChildView(chromeView);
  layoutViews();

  mainWindow.on('resize', layoutViews);

  chromeView.webContents.loadFile(path.join(__dirname, 'chrome-ui/index.html'));
  pageView.webContents.loadURL(START_URL);

  const pushState = () => {
    chromeView.webContents.send('nav-state', {
      url: pageView.webContents.getURL(),
      canGoBack: pageView.webContents.canGoBack(),
      canGoForward: pageView.webContents.canGoForward(),
      chromeVisible,
    });
  };
  pageView.webContents.on('did-navigate', pushState);
  pageView.webContents.on('did-navigate-in-page', pushState);

  ipcMain.on('nav-go', (_, url) => pageView.webContents.loadURL(url));
  ipcMain.on('nav-back', () => pageView.webContents.goBack());
  ipcMain.on('nav-forward', () => pageView.webContents.goForward());
  ipcMain.on('nav-reload', () => pageView.webContents.reload());
  ipcMain.on('toggle-chrome', () => {
    chromeVisible = !chromeVisible;
    layoutViews();
  });
});

app.on('window-all-closed', () => app.quit());
$cleanupBlock
    """.trimIndent()
  }

  private fun generateReactChromeApp(config: BrowserConfig): String {
    val defaultUrl = quoteJs(config.targetUrl.ifBlank { SPEEDOMETER_30_URL })
    return """
import React, { useEffect, useState } from 'https://esm.sh/react@19';
import { createRoot } from 'https://esm.sh/react-dom@19/client';

const { ipcRenderer } = window.require('electron');

function BrowserChrome() {
  const [url, setUrl] = useState($defaultUrl);
  const [nav, setNav] = useState({ canGoBack: false, canGoForward: false });

  useEffect(() => {
    const onState = (_, s) => {
      if (s.url) setUrl(s.url);
      setNav({ canGoBack: s.canGoBack, canGoForward: s.canGoForward });
    };
    ipcRenderer.on('nav-state', onState);
    return () => ipcRenderer.removeListener('nav-state', onState);
  }, []);

  const submit = (e) => {
    e.preventDefault();
    const target = url.startsWith('http') ? url : 'https://' + url;
    ipcRenderer.send('nav-go', target);
  };

  return React.createElement(
    'div',
    { style: styles.bar },
    React.createElement('button', {
      style: styles.btn,
      disabled: !nav.canGoBack,
      onClick: () => ipcRenderer.send('nav-back'),
    }, '\u2190'),
    React.createElement('button', {
      style: styles.btn,
      disabled: !nav.canGoForward,
      onClick: () => ipcRenderer.send('nav-forward'),
    }, '\u2192'),
    React.createElement('button', {
      style: styles.btn,
      onClick: () => ipcRenderer.send('nav-reload'),
    }, '\u21BB'),
    React.createElement(
      'form',
      { onSubmit: submit, style: { flex: 1, display: 'flex' } },
      React.createElement('input', {
        value: url,
        onChange: (e) => setUrl(e.target.value),
        style: styles.input,
        spellCheck: false,
      })
    ),
    React.createElement('button', {
      style: styles.speedoBtn,
      onClick: () => ipcRenderer.send('nav-go', 'https://browserbench.org/Speedometer3.0/'),
    }, 'Speedometer 3.0'),
    React.createElement('button', {
      style: styles.btn,
      title: 'Hide React chrome for zero-overlay benchmark run',
      onClick: () => ipcRenderer.send('toggle-chrome'),
    }, 'Hide')
  );
}

const styles = {
  bar: {
    height: '44px',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    padding: '0 8px',
    background: '#0b0f17',
    borderBottom: '1px solid #1f2a3e',
    fontFamily: 'system-ui, sans-serif',
  },
  btn: {
    height: '28px',
    padding: '0 9px',
    background: '#172030',
    color: '#e2e8f0',
    border: '1px solid #2d3c54',
    borderRadius: '6px',
    cursor: 'pointer',
  },
  speedoBtn: {
    height: '28px',
    padding: '0 10px',
    background: '#00e5ff',
    color: '#002026',
    fontWeight: 700,
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer',
  },
  input: {
    flex: 1,
    height: '28px',
    padding: '0 10px',
    background: '#111824',
    color: '#f1f5f9',
    border: '1px solid #2d3c54',
    borderRadius: '6px',
    fontSize: '12px',
    outline: 'none',
  },
};

createRoot(document.getElementById('root')).render(React.createElement(BrowserChrome));
    """.trimIndent()
  }

  private fun generateChromeIndexHtml(): String =
    """
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <style>html, body, #root { margin: 0; padding: 0; overflow: hidden; background: #0b0f17; }</style>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="./App.jsx"></script>
  </body>
</html>
    """.trimIndent()

  private fun generatePackageJson(): String =
    """
{
  "name": "zerochrome-desktop",
  "version": "1.0.0",
  "type": "module",
  "main": "main.mjs",
  "scripts": {
    "start": "electron ."
  },
  "devDependencies": {
    "electron": "^35.0.0"
  }
}
    """.trimIndent()

  private fun generateWindowsScript(config: BrowserConfig, flags: List<String>): String {
    val psFlags = flags.joinToString(",\n  ") { "\"$it\"" }
    val target = config.targetUrl.ifBlank { SPEEDOMETER_30_URL }
    val priority = config.processPriority.windowsPriorityClass
    val d = "$"
    val useTempProfile =
      config.cleanCookiesBeforeLaunch || config.cacheStrategy == CacheStrategy.COLD_NO_CACHE

    val profileSetup =
      if (useTempProfile) {
        """
${d}TempProfile = Join-Path ${d}env:TEMP ("zerochrome_" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path ${d}TempProfile | Out-Null
${d}Flags += "--user-data-dir=${d}TempProfile"
        """.trimIndent()
      } else {
        "# Warm persistent cache profile enabled"
      }

    val cleanup =
      if (useTempProfile) {
        """
if (Test-Path ${d}TempProfile) {
  Remove-Item -Path ${d}TempProfile -Recurse -Force -ErrorAction SilentlyContinue
}
        """.trimIndent()
      } else {
        ""
      }

    return """
# ZeroChrome Direct Chromium Launcher (Windows)
${d}ErrorActionPreference = "Stop"

${d}ChromePaths = @(
  "${d}env:ProgramFiles\Google\Chrome\Application\chrome.exe",
  "${d}env:LocalAppData\Chromium\Application\chrome.exe",
  "${d}env:ProgramFiles (x86)\Microsoft\Edge\Application\msedge.exe"
)
${d}Browser = ${d}ChromePaths | Where-Object { Test-Path ${d}_ } | Select-Object -First 1
if (-not ${d}Browser) { throw "Chromium/Chrome executable not found." }

${d}Flags = @(
  $psFlags,
  "--app=$target"
)

$profileSetup

${d}proc = Start-Process -FilePath ${d}Browser -ArgumentList ${d}Flags -PassThru
${d}proc.PriorityClass = "$priority"
${d}proc.WaitForExit()

$cleanup
    """.trimIndent()
  }

  private fun generateUnixScript(config: BrowserConfig, flags: List<String>): String {
    val flagArgs = flags.joinToString(" \\\n  ") { "\"$it\"" }
    val target = config.targetUrl.ifBlank { SPEEDOMETER_30_URL }
    val nice = config.processPriority.unixNice
    val d = "$"
    val useTempProfile =
      config.cleanCookiesBeforeLaunch || config.cacheStrategy == CacheStrategy.COLD_NO_CACHE

    val profileSetup =
      if (useTempProfile) {
        """
TMP_PROFILE="${d}(mktemp -d /tmp/zerochrome.XXXXXX)"
trap 'rm -rf "${d}TMP_PROFILE"' EXIT
PROFILE_FLAG="--user-data-dir=${d}TMP_PROFILE"
        """.trimIndent()
      } else {
        "PROFILE_FLAG=\"\""
      }

    return """
#!/usr/bin/env bash
set -euo pipefail

# Locate Chromium or Google Chrome
if command -v chromium >/dev/null 2>&1; then
  BIN="chromium"
elif command -v google-chrome >/dev/null 2>&1; then
  BIN="google-chrome"
elif [ -x "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
  BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
else
  echo "Chromium/Chrome binary not found." >&2
  exit 1
fi

$profileSetup

nice -n $nice "${d}BIN" \
  ${d}PROFILE_FLAG \
  $flagArgs \
  "--app=$target"
    """.trimIndent()
  }

  private fun quoteJs(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("'", "\\'")
    return "'$escaped'"
  }
}
