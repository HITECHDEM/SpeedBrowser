package com.example.data

import android.net.Uri
import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class CachedWebAsset(
  val url: String,
  val mimeType: String,
  val encoding: String,
  val contentTypeHeader: String,
  val bytes: ByteArray,
)

data class RamCacheStats(
  val assetCount: Int = 0,
  val cachedBytes: Long = 0L,
  val maxBudgetBytes: Long = BenchmarkRamCache.MAX_RAM_CACHE_BYTES.toLong(),
  val hitCount: Long = 0L,
  val missCount: Long = 0L,
  val isPrewarming: Boolean = false,
)

object BenchmarkRamCache {
  // 96 MB in-memory LruCache keeps full Speedometer 3.x asset tree hot in RAM with zero disk I/O
  const val MAX_RAM_CACHE_BYTES: Int = 96 * 1024 * 1024
  private const val MAX_SINGLE_ASSET_BYTES: Int = 12 * 1024 * 1024

  private val cache =
    object : LruCache<String, CachedWebAsset>(MAX_RAM_CACHE_BYTES) {
      override fun sizeOf(key: String, value: CachedWebAsset): Int = value.bytes.size.coerceAtLeast(1)
    }

  private val hitCounter = AtomicLong(0L)
  private val missCounter = AtomicLong(0L)

  private val _stats = MutableStateFlow(RamCacheStats())
  val stats: StateFlow<RamCacheStats> = _stats.asStateFlow()

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
      .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()
  }

  private val cacheableExtensions =
    setOf(
      "js",
      "mjs",
      "cjs",
      "css",
      "html",
      "htm",
      "svg",
      "json",
      "wasm",
      "woff",
      "woff2",
      "ttf",
      "otf",
      "png",
      "jpg",
      "jpeg",
      "gif",
      "webp",
      "ico",
    )

  fun shouldIntercept(request: WebResourceRequest, targetOriginHost: String?): Boolean {
    if (!request.method.equals("GET", ignoreCase = true)) return false
    val uri = request.url ?: return false
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
    if (scheme != "https" && scheme != "http") return false
    if (request.requestHeaders?.containsKey("Range") == true) return false

    val host = uri.host?.lowercase(Locale.US) ?: return false
    val expectedHost = targetOriginHost?.lowercase(Locale.US)
    if (expectedHost != null && host != expectedHost && !host.endsWith(".$expectedHost")) {
      return false
    }

    val path = uri.path ?: return false
    if (path.endsWith("/")) return true
    val lastSegment = path.substringAfterLast('/', "")
    val ext = lastSegment.substringAfterLast('.', "").lowercase(Locale.US)
    return ext.isEmpty() || cacheableExtensions.contains(ext)
  }

  fun interceptOrFetch(
    request: WebResourceRequest,
    userAgent: String?,
  ): WebResourceResponse? {
    val urlStr = request.url.toString()
    val requestOrigin = request.requestHeaders?.get("Origin")

    val hit = cache.get(urlStr)
    if (hit != null) {
      hitCounter.incrementAndGet()
      return buildWebResourceResponse(hit, requestOrigin)
    }

    missCounter.incrementAndGet()
    val fetched = fetchAndStoreSync(urlStr, request.requestHeaders, userAgent) ?: return null
    return buildWebResourceResponse(fetched, requestOrigin)
  }

  suspend fun prewarmUrl(targetUrl: String, userAgent: String? = null): RamCacheStats =
    withContext(Dispatchers.IO) {
      _stats.value = snapshotStats(prewarming = true)
      try {
        val entryAsset = fetchAndStoreSync(targetUrl, emptyMap(), userAgent)
        if (entryAsset != null && entryAsset.mimeType.contains("html", ignoreCase = true)) {
          val htmlText = entryAsset.bytes.toString(Charsets.UTF_8)
          val baseUri = Uri.parse(targetUrl)
          val assetUrls = extractSubresourceUrls(baseUri, htmlText).take(48)
          coroutineScope {
            assetUrls
              .map { subUrl ->
                async(Dispatchers.IO) {
                  if (cache.get(subUrl) == null) {
                    fetchAndStoreSync(subUrl, emptyMap(), userAgent)
                  }
                }
              }
              .awaitAll()
          }
        }
      } catch (_: Throwable) {
        // Best-effort prewarm; failures fall back to live fetch
      } finally {
        _stats.value = snapshotStats(prewarming = false)
      }
      _stats.value
    }

  fun refreshStats() {
    _stats.value = snapshotStats(prewarming = _stats.value.isPrewarming)
  }

  fun clear() {
    cache.evictAll()
    hitCounter.set(0L)
    missCounter.set(0L)
    _stats.value = snapshotStats(prewarming = false)
  }

  private fun snapshotStats(prewarming: Boolean): RamCacheStats {
    val snapshot = cache.snapshot()
    var bytes = 0L
    snapshot.values.forEach { bytes += it.bytes.size }
    return RamCacheStats(
      assetCount = snapshot.size,
      cachedBytes = bytes,
      maxBudgetBytes = MAX_RAM_CACHE_BYTES.toLong(),
      hitCount = hitCounter.get(),
      missCount = missCounter.get(),
      isPrewarming = prewarming,
    )
  }

  private fun fetchAndStoreSync(
    urlStr: String,
    requestHeaders: Map<String, String>?,
    userAgent: String?,
  ): CachedWebAsset? {
    return try {
      val reqBuilder = Request.Builder().url(urlStr).get()
      if (!userAgent.isNullOrBlank()) {
        reqBuilder.header("User-Agent", userAgent)
      }
      requestHeaders?.forEach { (k, v) ->
        if (!k.equals("Host", ignoreCase = true) && !k.equals("Accept-Encoding", ignoreCase = true)) {
          reqBuilder.header(k, v)
        }
      }
      runCatching { CookieManager.getInstance().getCookie(urlStr) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { cookie -> reqBuilder.header("Cookie", cookie) }

      client.newCall(reqBuilder.build()).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val bytes = resp.body?.bytes() ?: return null
        if (bytes.size > MAX_SINGLE_ASSET_BYTES) return null

        val rawContentType = resp.header("Content-Type")
        val (mime, encoding, contentTypeHeader) = parseMimeAndEncoding(urlStr, rawContentType)
        val asset =
          CachedWebAsset(
            url = urlStr,
            mimeType = mime,
            encoding = encoding,
            contentTypeHeader = contentTypeHeader,
            bytes = bytes,
          )
        cache.put(urlStr, asset)
        asset
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun buildWebResourceResponse(
    asset: CachedWebAsset,
    requestOrigin: String?,
  ): WebResourceResponse {
    val headers = HashMap<String, String>(6)
    headers["Cache-Control"] = "public, max-age=31536000, immutable"
    headers["Content-Type"] = asset.contentTypeHeader
    headers["Timing-Allow-Origin"] = "*"
    if (!requestOrigin.isNullOrBlank()) {
      headers["Access-Control-Allow-Origin"] = requestOrigin
      headers["Access-Control-Allow-Credentials"] = "true"
      headers["Vary"] = "Origin"
    } else {
      headers["Access-Control-Allow-Origin"] = "*"
    }
    return WebResourceResponse(
      asset.mimeType,
      asset.encoding,
      200,
      "OK",
      headers,
      ByteArrayInputStream(asset.bytes),
    )
  }

  internal fun parseMimeAndEncoding(
    urlStr: String,
    rawContentType: String?,
  ): Triple<String, String, String> {
    var mime = ""
    var encoding = "utf-8"

    if (!rawContentType.isNullOrBlank()) {
      val parts = rawContentType.split(';')
      mime = parts.firstOrNull()?.trim()?.lowercase(Locale.US).orEmpty()
      for (i in 1 until parts.size) {
        val param = parts[i].trim()
        if (param.startsWith("charset=", ignoreCase = true)) {
          encoding = param.substringAfter('=').trim().trim('"').ifEmpty { "utf-8" }
        }
      }
    }

    if (mime.isEmpty() || mime == "application/octet-stream") {
      mime = inferMimeFromUrl(urlStr)
    }

    val fullHeader =
      if (mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json" || mime == "image/svg+xml") {
        "$mime; charset=$encoding"
      } else {
        mime
      }
    return Triple(mime, encoding, fullHeader)
  }

  private fun inferMimeFromUrl(urlStr: String): String {
    val path = urlStr.substringBefore('?').substringBefore('#').lowercase(Locale.US)
    return when {
      path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".cjs") -> "text/javascript"
      path.endsWith(".css") -> "text/css"
      path.endsWith(".html") || path.endsWith(".htm") || path.endsWith("/") -> "text/html"
      path.endsWith(".svg") -> "image/svg+xml"
      path.endsWith(".json") -> "application/json"
      path.endsWith(".wasm") -> "application/wasm"
      path.endsWith(".woff2") -> "font/woff2"
      path.endsWith(".woff") -> "font/woff"
      path.endsWith(".ttf") -> "font/ttf"
      path.endsWith(".png") -> "image/png"
      path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
      path.endsWith(".webp") -> "image/webp"
      path.endsWith(".gif") -> "image/gif"
      path.endsWith(".ico") -> "image/x-icon"
      else -> "text/html"
    }
  }

  private fun extractSubresourceUrls(baseUri: Uri, html: String): List<String> {
    val regex = Regex("""(?:src|href)\s*=\s*["']([^"'#]+)["']""", RegexOption.IGNORE_CASE)
    val results = LinkedHashSet<String>()
    val baseStr = baseUri.toString()
    val baseDir =
      if (baseStr.endsWith("/")) baseStr
      else baseStr.substringBeforeLast('/') + "/"

    regex.findAll(html).forEach { match ->
      val raw = match.groupValues[1].trim()
      if (raw.isEmpty() || raw.startsWith("data:") || raw.startsWith("javascript:")) return@forEach
      val resolved =
        when {
          raw.startsWith("http://") || raw.startsWith("https://") -> raw
          raw.startsWith("//") -> "${baseUri.scheme ?: "https"}:$raw"
          raw.startsWith("/") -> "${baseUri.scheme}://${baseUri.authority}$raw"
          else -> "$baseDir$raw"
        }
      results.add(resolved)
    }
    return results.toList()
  }
}
