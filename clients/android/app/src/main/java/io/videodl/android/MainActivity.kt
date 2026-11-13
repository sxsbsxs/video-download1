package io.videodl.android

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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class SavedDownload(
    val title: String,
    val fileName: String,
    val savedAt: Long,
    val contentUri: String = ""
)

class MainActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentPage by mutableIntStateOf(0)
    private var currentInput by mutableStateOf("")
    private var mediaInfo by mutableStateOf<VideoInfo?>(null)
    private var selectedFormatKey by mutableStateOf("")
    private var selectedFormatTab by mutableIntStateOf(0)
    private var analyzing by mutableStateOf(false)
    private var downloading by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var errorText by mutableStateOf("")
    private var progress by mutableStateOf<Float?>(null)
    private var settingsMessage by mutableStateOf("")
    private var history by mutableStateOf<List<SavedDownload>>(emptyList())

    private var lastProgressPush = 0L
    private var lastDownloadProgressAt = 0L
    private var downloadWatchdogJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentInput = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }
        history = loadHistory(this)

        window.statusBarColor = android.graphics.Color.rgb(247, 248, 250)
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        setContent {
            VideoDLTheme {
                VideoDLApp(
                    state = VideoDLUiState(
                        page = currentPage,
                        input = currentInput,
                        mediaInfo = mediaInfo,
                        selectedFormatKey = selectedFormatKey,
                        selectedFormatTab = selectedFormatTab,
                        analyzing = analyzing,
                        downloading = downloading,
                        statusText = statusText,
                        errorText = errorText,
                        progress = progress,
                        settingsMessage = settingsMessage,
                        history = history
                    ),
                    onInputChange = { currentInput = it },
                    onPaste = { pasteFromClipboard() },
                    onAnalyze = { analyzeCurrentInput() },
                    onSelectTab = { selectFormatTab(it) },
                    onSelectFormat = { selectedFormatKey = it },
                    onDownload = { beginDownload(it) },
                    onPageChange = { currentPage = it.coerceIn(0, 2) },
                    onOpenDownload = { index ->
                        history.getOrNull(index)?.let { openSavedDownload(this, it) }
                    },
                    onClearHistory = { clearHistory() },
                    onClearTemp = { clearTemp() }
                )
            }
        }
    }

    override fun onDestroy() {
        downloadWatchdogJob?.cancel()
        activityScope.cancel()
        super.onDestroy()
    }

    private fun analyzeCurrentInput() {
        if (analyzing || downloading) return
        val raw = currentInput.trim()
        if (raw.isBlank()) return

        currentInput = raw
        analyzing = true
        mediaInfo = null
        selectedFormatKey = ""
        errorText = ""
        statusText = "正在读取视频信息…"
        progress = null

        activityScope.launch {
            try {
                val result = LocalYtDlpEngine.inspect(this@MainActivity, raw)
                mediaInfo = result
                selectedFormatTab = if (result.videoFormats.isNotEmpty()) 0 else 1
                selectedFormatKey = result.videoFormats.firstOrNull()?.key
                    ?: result.audioFormats.firstOrNull()?.key.orEmpty()
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
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        if (!copied.isNullOrBlank()) {
            currentInput = copied.trim()
            mediaInfo = null
            selectedFormatKey = ""
            errorText = ""
            statusText = ""
            progress = null
        } else {
            Toast.makeText(this, "剪贴板里没有可用文本", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectFormatTab(tab: Int) {
        selectedFormatTab = tab.coerceIn(0, 1)
        val formats = if (selectedFormatTab == 0) {
            mediaInfo?.videoFormats.orEmpty()
        } else {
            mediaInfo?.audioFormats.orEmpty()
        }
        if (formats.none { it.key == selectedFormatKey }) {
            selectedFormatKey = formats.firstOrNull()?.key.orEmpty()
        }
    }

    private fun clearHistory() {
        history = emptyList()
        storeHistory(this, emptyList())
        settingsMessage = "下载列表记录已清除，手机中的文件不会被删除。"
    }

    private fun clearTemp() {
        if (downloading) return
        activityScope.launch {
            val cleared = withContext(Dispatchers.IO) {
                File(cacheDir, "VideoDL").deleteRecursively()
            }
            settingsMessage = if (cleared) {
                "临时下载缓存已清理。"
            } else {
                "没有需要清理的临时文件。"
            }
            Toast.makeText(this@MainActivity, settingsMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun beginDownload(formatKey: String) {
        if (downloading || analyzing) return
        val chosen = (mediaInfo?.videoFormats.orEmpty() + mediaInfo?.audioFormats.orEmpty())
            .firstOrNull { it.key == formatKey }
        if (chosen == null) {
            errorText = "请先选择一个可用格式。"
            return
        }

        downloading = true
        progress = 0f
        statusText = "正在准备下载…"
        errorText = ""
        lastProgressPush = 0L
        lastDownloadProgressAt = SystemClock.elapsedRealtime()
        downloadWatchdogJob?.cancel()
        downloadWatchdogJob = activityScope.launch {
            var lastNotice = ""
            while (downloading) {
                delay(5_000L)
                val idleFor = SystemClock.elapsedRealtime() - lastDownloadProgressAt
                val notice = when {
                    idleFor >= 90_000L -> "当前阶段已超过 90 秒没有新进度，正在等待下载器超时返回…"
                    idleFor >= 20_000L -> "暂时没有收到新数据，正在等待当前下载阶段结束…"
                    else -> ""
                }
                if (notice.isNotBlank() && notice != lastNotice) {
                    lastNotice = notice
                    statusText = notice
                } else if (notice.isBlank()) {
                    lastNotice = ""
                }
            }
        }

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
                            lastDownloadProgressAt = now
                            if (now - lastProgressPush >= 350L || percent >= 98f) {
                                lastProgressPush = now
                                progress = percent.coerceIn(0f, 98f)
                                statusText = line
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

                val savedMedia = withContext(Dispatchers.IO) {
                    saveIntoDownloads(this@MainActivity, file, info.title) { copiedBytes, totalBytes ->
                        val now = SystemClock.elapsedRealtime()
                        lastDownloadProgressAt = now
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
                                }
                            }
                        }
                    }
                }

                val item = SavedDownload(
                    info.title,
                    savedMedia.displayName,
                    System.currentTimeMillis(),
                    savedMedia.uri.toString()
                )
                history = listOf(item) + history.filterNot { it.contentUri == item.contentUri }.take(19)
                storeHistory(this@MainActivity, history)

                progress = 99.7f
                statusText = "正在清理临时文件…"
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
                downloadWatchdogJob?.cancel()
                downloadWatchdogJob = null
                acceptProgress.set(false)
                val remainingDirectory = temporaryDirectory
                if (remainingDirectory != null) {
                    withContext(Dispatchers.IO) {
                        runCatching { LocalYtDlpEngine.cleanup(remainingDirectory) }
                    }
                }
                downloading = false
            }
        }
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
            val complete = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
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
            Toast.makeText(
                context,
                "这条旧记录没有文件打开地址，请到 Downloads/VideoDL 查看。",
                Toast.LENGTH_LONG
            ).show()
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
                SavedDownload(
                    item.optString("title"),
                    item.optString("fileName"),
                    item.optLong("savedAt"),
                    item.optString("contentUri")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun storeHistory(context: Context, items: List<SavedDownload>) {
        val array = JSONArray()
        items.take(20).forEach { item ->
            array.put(
                JSONObject()
                    .put("title", item.title)
                    .put("fileName", item.fileName)
                    .put("savedAt", item.savedAt)
                    .put("contentUri", item.contentUri)
            )
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
