package io.videodl.android

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private data class SavedDownload(
    val title: String,
    val fileName: String,
    val savedAt: Long,
    val contentUri: String = ""
)

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentPage = 0
    private var currentInput = ""
    private var mediaInfo: VideoInfo? = null
    private var selectedFormatKey = ""
    private var selectedFormatTab = 0
    private var analyzing = false
    private var downloading = false
    private var statusText = ""
    private var errorText = ""
    private var progress: Float? = null
    private var settingsMessage = ""
    private var history: List<SavedDownload> = emptyList()
    private var lastProgressPush = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInput = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }
        history = loadHistory(this)

        @SuppressLint("SetJavaScriptEnabled")
        val view = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(246, 248, 252))
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return url != null && !url.startsWith("file:///android_asset/")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    renderState()
                }
            }
            addJavascriptInterface(VideoDLBridge(), "VideoDL")
            loadUrl("file:///android_asset/index.html")
        }
        webView = view
        setContentView(webView)
        window.statusBarColor = android.graphics.Color.rgb(248, 249, 252)
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("VideoDL")
            webView.destroy()
        }
        activityScope.cancel()
        super.onDestroy()
    }

    private inner class VideoDLBridge {
        @JavascriptInterface
        fun rememberInput(rawText: String) {
            runOnUiThread { currentInput = rawText }
        }

        @JavascriptInterface
        fun navigate(page: Int) = runOnUiThread {
            currentPage = page.coerceIn(0, 2)
            renderState()
        }

        @JavascriptInterface
        fun analyze(rawText: String) = runOnUiThread {
            if (analyzing || downloading) return@runOnUiThread
            currentInput = rawText
            analyzing = true
            errorText = ""
            statusText = "正在读取视频信息…"
            progress = null
            renderState()
            activityScope.launch {
                try {
                    val result = LocalYtDlpEngine.inspect(this@MainActivity, currentInput)
                    mediaInfo = result
                    selectedFormatTab = if (result.videoFormats.isNotEmpty()) 0 else 1
                    selectedFormatKey = ""
                    currentInput = result.sourceUrl
                    val host = Uri.parse(result.sourceUrl).host.orEmpty().lowercase(Locale.ROOT)
                    val highestHeight = result.videoFormats.maxOfOrNull { it.height } ?: 0
                    statusText = if (host.endsWith("nicovideo.jp") && highestHeight in 1..1079) {
                        "解析完成 · Niconico 当前返回最高 " + highestHeight + "P"
                    } else {
                        "解析完成 · 找到 " + (result.videoFormats.size + result.audioFormats.size) + " 种格式"
                    }
                } catch (error: Exception) {
                    mediaInfo = null
                    statusText = "解析失败"
                    errorText = error.message ?: "无法读取该视频，请检查链接后重试。"
                } finally {
                    analyzing = false
                    renderState()
                }
            }
        }

        @JavascriptInterface
        fun paste() = runOnUiThread {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val copied = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this@MainActivity)
                ?.toString()
            if (!copied.isNullOrBlank()) {
                currentInput = copied
                mediaInfo = null
                selectedFormatKey = ""
                errorText = ""
                statusText = ""
                progress = null
                renderState()
            } else {
                Toast.makeText(this@MainActivity, "剪贴板里没有可用文本", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun selectFormat(key: String, tab: Int) = runOnUiThread {
            selectedFormatTab = tab.coerceIn(0, 1)
            selectedFormatKey = key
            renderState()
        }

        @JavascriptInterface
        fun download(key: String) = runOnUiThread { beginDownload(key) }

        @JavascriptInterface
        fun openDownload(index: Int) = runOnUiThread {
            history.getOrNull(index)?.let { openSavedDownload(this@MainActivity, it) }
        }

        @JavascriptInterface
        fun clearHistory() = runOnUiThread {
            history = emptyList()
            storeHistory(this@MainActivity, emptyList())
            settingsMessage = "下载列表记录已清除，手机中的文件不会被删除。"
            renderState()
        }

        @JavascriptInterface
        fun clearTemp() = runOnUiThread {
            if (downloading) return@runOnUiThread
            val cleared = File(cacheDir, "VideoDL").deleteRecursively()
            settingsMessage = if (cleared) "临时下载缓存已清理。" else "没有需要清理的临时文件。"
            Toast.makeText(this@MainActivity, settingsMessage, Toast.LENGTH_SHORT).show()
            renderState()
        }
    }

    private fun beginDownload(formatKey: String) {
        if (downloading || analyzing) return
        val chosen = (mediaInfo?.videoFormats.orEmpty() + mediaInfo?.audioFormats.orEmpty())
            .firstOrNull { it.key == formatKey }
        if (chosen == null) {
            errorText = "请先选择一个可用格式。"
            renderState()
            return
        }
        downloading = true
        progress = 0f
        statusText = "正在准备下载…"
        errorText = ""
        lastProgressPush = 0L
        renderState()

        activityScope.launch {
            var temporaryDirectory: File? = null
            var lastSaveProgressPush = 0L
            val acceptProgress = AtomicBoolean(true)
            try {
                val listener = object : LocalYtDlpProgress {
                    override fun onProgressUpdate(percent: Float, etaInSeconds: Long, line: String) {
                        if (!acceptProgress.get()) return
                        runOnUiThread {
                            if (!acceptProgress.get() || !downloading) return@runOnUiThread
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastProgressPush >= 350L || percent >= 98f) {
                                lastProgressPush = now
                                progress = percent.coerceIn(0f, 98f)
                                statusText = line
                                renderState()
                            }
                        }
                    }
                }
                val info = mediaInfo ?: throw IllegalStateException("视频信息已失效，请重新解析。")
                val file = LocalYtDlpEngine.download(this@MainActivity, info.sourceUrl, chosen, listener)
                acceptProgress.set(false)
                temporaryDirectory = file.parentFile
                progress = 98f
                statusText = "下载数据已就绪，正在保存到手机…"
                renderState()

                val savedMedia = withContext(Dispatchers.IO) {
                    saveIntoDownloads(this@MainActivity, file, info.title) { copiedBytes, totalBytes ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastSaveProgressPush >= 300L || copiedBytes >= totalBytes) {
                            lastSaveProgressPush = now
                            runOnUiThread {
                                if (downloading) {
                                    val ratio = if (totalBytes > 0L) {
                                        (copiedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
                                    } else {
                                        0.0
                                    }
                                    progress = (98f + (ratio * 1.4f).toFloat()).coerceIn(98f, 99.5f)
                                    val percent = (ratio * 100.0).toInt().coerceAtMost(99)
                                    statusText = "正在写入手机存储 · " + percent + "%"
                                    renderState()
                                }
                            }
                        }
                    }
                }

                val item = SavedDownload(info.title, savedMedia.displayName, System.currentTimeMillis(), savedMedia.uri.toString())
                history = listOf(item) + history.filterNot { it.contentUri == item.contentUri }.take(19)
                storeHistory(this@MainActivity, history)

                progress = 99.7f
                statusText = "正在清理临时文件…"
                renderState()
                val completedDirectory = temporaryDirectory
                withContext(Dispatchers.IO) {
                    runCatching { LocalYtDlpEngine.cleanup(completedDirectory) }
                }
                temporaryDirectory = null
                progress = 100f
                statusText = "下载完成 · 已保存到 Downloads/VideoDL"
                currentPage = 1
            } catch (error: Exception) {
                acceptProgress.set(false)
                progress = null
                currentPage = 0
                statusText = "下载失败"
                errorText = error.message ?: "下载过程中出现错误。"
            } finally {
                acceptProgress.set(false)
                val remainingDirectory = temporaryDirectory
                if (remainingDirectory != null) {
                    withContext(Dispatchers.IO) {
                        runCatching { LocalYtDlpEngine.cleanup(remainingDirectory) }
                    }
                }
                downloading = false
                renderState()
            }
        }
    }

    private fun renderState() {
        if (!::webView.isInitialized) return
        val json = buildStateJson().toString()
        val script = "window.VideoDLRender && window.VideoDLRender(JSON.parse(" +
            JSONObject.quote(json) + "));"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun buildStateJson(): JSONObject {
        val root = JSONObject()
            .put("page", currentPage)
            .put("input", currentInput)
            .put("analyzing", analyzing)
            .put("downloading", downloading)
            .put("status", statusText)
            .put("error", errorText)
            .put("settingsMessage", settingsMessage)
            .put("progress", progress ?: JSONObject.NULL)
            .put("selectedFormatKey", selectedFormatKey)
            .put("selectedFormatTab", selectedFormatTab)

        val info = mediaInfo
        if (info == null) {
            root.put("video", JSONObject.NULL)
        } else {
            root.put("video", JSONObject()
                .put("title", info.title)
                .put("uploader", info.uploader)
                .put("duration", info.durationSeconds)
                .put("thumbnail", info.thumbnailPath?.let { Uri.fromFile(File(it)).toString() } ?: "")
                .put("sourceUrl", info.sourceUrl)
                .put("videoFormats", formatArray(info.videoFormats))
                .put("audioFormats", formatArray(info.audioFormats)))
        }
        val saved = JSONArray()
        history.forEach { item ->
            saved.put(JSONObject()
                .put("title", item.title)
                .put("fileName", item.fileName)
                .put("savedAt", item.savedAt)
                .put("contentUri", item.contentUri))
        }
        root.put("history", saved)
        return root
    }

    private fun formatArray(formats: List<VideoFormat>): JSONArray {
        val array = JSONArray()
        formats.forEach { format ->
            val fps = String.format(Locale.US, "%.2f", format.fps)
            val groupKey = format.width.toString() + "|" + format.height + "|" + fps + "|" +
                format.dynamicRange.trim().lowercase(Locale.ROOT)
            array.put(JSONObject()
                .put("key", format.key)
                .put("label", format.primaryLabel)
                .put("technical", format.technicalSummary)
                .put("size", format.sizeLabel)
                .put("width", format.width)
                .put("height", format.height)
                .put("fps", format.fps)
                .put("dynamicRange", format.dynamicRange)
                .put("extension", format.extension)
                .put("isAudio", format.isAudio)
                .put("bitrate", format.bitrate)
                .put("videoCodec", format.videoCodec)
                .put("audioCodec", format.audioCodec)
                .put("groupKey", groupKey))
        }
        return array
    }

    private data class SavedMedia(val uri: Uri, val displayName: String)

    private fun saveIntoDownloads(
        context: Context,
        source: File,
        title: String,
        onCopyProgress: (copiedBytes: Long, totalBytes: Long) -> Unit
    ): SavedMedia {
        if (!source.isFile || source.length() <= 0L) {
            throw IllegalStateException("下载文件不存在或大小为 0。")
        }
        val totalBytes = source.length()
        val extension = source.extension.ifBlank { "mp4" }
        val safeTitle = title.filterNot { it.isISOControl() }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim().take(90).ifBlank { "VideoDL" }
        val displayName = safeTitle + "." + extension
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideoDL")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建系统下载文件")
        try {
            var copiedBytes = 0L
            source.inputStream().use { input ->
                val output = context.contentResolver.openOutputStream(uri, "w")
                    ?: throw IllegalStateException("无法写入系统下载目录")
                output.use { stream ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        stream.write(buffer, 0, count)
                        copiedBytes += count
                        onCopyProgress(copiedBytes, totalBytes)
                    }
                    stream.flush()
                }
            }
            if (copiedBytes != totalBytes) {
                throw IllegalStateException(
                    "保存文件不完整：已写入 " + copiedBytes + " 字节，源文件为 " + totalBytes + " 字节。"
                )
            }
            val complete = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (context.contentResolver.update(uri, complete, null, null) <= 0) {
                throw IllegalStateException("文件已复制，但系统未能完成发布。")
            }
            return SavedMedia(uri, displayName)
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun openSavedDownload(context: Context, item: SavedDownload) {
        if (item.contentUri.isBlank()) {
            Toast.makeText(context, "这条旧记录没有文件打开地址，请到 Downloads/VideoDL 查看。", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            val extension = item.fileName.substringAfterLast('.', "")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(item.contentUri), mimeTypeFor(extension))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "没有找到可以打开此文件的应用。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHistory(context: Context): List<SavedDownload> {
        val raw = context.getSharedPreferences("videodl-local", Context.MODE_PRIVATE)
            .getString("history", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SavedDownload(item.optString("title"), item.optString("fileName"),
                    item.optLong("savedAt"), item.optString("contentUri"))
            }
        }.getOrDefault(emptyList())
    }

    private fun storeHistory(context: Context, items: List<SavedDownload>) {
        val array = JSONArray()
        items.take(20).forEach { item ->
            array.put(JSONObject().put("title", item.title).put("fileName", item.fileName)
                .put("savedAt", item.savedAt).put("contentUri", item.contentUri))
        }
        context.getSharedPreferences("videodl-local", Context.MODE_PRIVATE)
            .edit().putString("history", array.toString()).apply()
    }

    private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }
}