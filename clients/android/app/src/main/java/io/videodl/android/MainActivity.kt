package io.videodl.android

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VideoDLScreen()
                }
            }
        }
    }
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.MINUTES)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

@Composable
private fun VideoDLScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences("videodl-client", android.content.Context.MODE_PRIVATE)
    }
    val ownerToken = remember(prefs) {
        prefs.getString("ownerToken", null) ?: (
            UUID.randomUUID().toString() + UUID.randomUUID().toString()
        ).also { token ->
            prefs.edit().putString("ownerToken", token).apply()
        }
    }
    var apiBase by rememberSaveable { mutableStateOf(prefs.getString("apiBase", "") ?: "") }
    var videoUrl by rememberSaveable { mutableStateOf(prefs.getString("videoUrl", "") ?: "") }
    var quality by rememberSaveable { mutableStateOf(prefs.getString("quality", "best") ?: "best") }
    var title by rememberSaveable { mutableStateOf(prefs.getString("title", "") ?: "") }
    var uploader by rememberSaveable { mutableStateOf(prefs.getString("uploader", "") ?: "") }
    var thumbnail by rememberSaveable { mutableStateOf(prefs.getString("thumbnail", null)) }
    var status by remember { mutableStateOf("填写服务端地址和公开视频链接后开始解析。") }
    var busy by remember { mutableStateOf(false) }
    var activeTaskId by rememberSaveable {
        mutableStateOf(prefs.getString("activeTaskId", null))
    }
    var progressPercent by remember { mutableStateOf<Float?>(null) }
    var recentTasks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("VideoDL", style = MaterialTheme.typography.headlineMedium)
        Text("多语言视频下载客户端", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = apiBase, onValueChange = {
                apiBase = it
                prefs.edit().putString("apiBase", it).apply()
            }, modifier = Modifier.fillMaxWidth(),
            label = { Text("服务端 API 地址（HTTPS）") },
            placeholder = { Text("https://your-videodl-service.example") }, singleLine = true
        )
        OutlinedTextField(
            value = videoUrl, onValueChange = {
                videoUrl = it
                prefs.edit().putString("videoUrl", it).apply()
            }, modifier = Modifier.fillMaxWidth(),
            label = { Text("视频链接") }, placeholder = { Text("粘贴公开视频链接") }, minLines = 2
        )
        OutlinedTextField(
            value = quality, onValueChange = {
                quality = it
                prefs.edit().putString("quality", it).apply()
            }, modifier = Modifier.fillMaxWidth(),
            label = { Text("画质（best / 1080 / 720 / audio）") }, singleLine = true
        )
        Button(
            enabled = !busy && apiBase.isNotBlank() && videoUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    status = "正在解析视频…"
                    try {
                        val info = withContext(Dispatchers.IO) {
                            postJson(apiBase, "/api/info", JSONObject().put("url", videoUrl))
                        }
                        title = info.optString("title", "未命名视频")
                        uploader = info.optString("uploader", "")
                        thumbnail = info.optString("thumbnail").takeIf { it.startsWith("http") }
                        prefs.edit().apply {
                            putString("title", title)
                            putString("uploader", uploader)
                            putString("thumbnail", thumbnail)
                        }.apply()
                        status = "解析完成。"
                    } catch (error: Exception) {
                        status = error.message ?: "解析失败，请检查服务端地址和链接。"
                    } finally {
                        busy = false
                    }
                }
            }
        ) { Text(if (busy) "处理中…" else "解析视频") }

        if (title.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    thumbnail?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "视频封面",
                            modifier = Modifier.fillMaxWidth().height(190.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (uploader.isNotBlank()) Text("上传者：" + uploader)
                }
            }
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        progressPercent = null
                        status = "正在提交下载任务…"
                        try {
                            val saved = downloadWithTaskSync(
                                apiBase = apiBase,
                                videoUrl = videoUrl,
                                quality = quality,
                                ownerToken = ownerToken,
                                onTaskCreated = {
                                    activeTaskId = it
                                    prefs.edit().putString("activeTaskId", it).apply()
                                },
                                onStatus = { status = it },
                                onProgress = { progressPercent = it },
                                onTaskTerminal = {
                                    activeTaskId = null
                                    prefs.edit().remove("activeTaskId").apply()
                                }
                            ) { taskId ->
                                withContext(Dispatchers.IO) {
                                    downloadTaskToDownloads(context.contentResolver, apiBase, taskId, quality)
                                }
                            }
                            status = "下载完成：" + saved
                            activeTaskId = null
                            prefs.edit().remove("activeTaskId").apply()
                            progressPercent = null
                        } catch (error: Exception) {
                            status = error.message ?: "下载失败。"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("下载到手机") }

            if (!busy && activeTaskId != null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            progressPercent = null
                            try {
                                val taskId = activeTaskId ?: return@launch
                                val saved = waitForDownloadTask(
                                    apiBase = apiBase,
                                    taskId = taskId,
                                    onStatus = { status = it },
                                    onProgress = { progressPercent = it },
                                    onTaskTerminal = {
                                        activeTaskId = null
                                        prefs.edit().remove("activeTaskId").apply()
                                    }
                                ) { id ->
                                    withContext(Dispatchers.IO) {
                                        downloadTaskToDownloads(context.contentResolver, apiBase, id, quality)
                                    }
                                }
                                status = "下载完成：" + saved
                                activeTaskId = null
                                prefs.edit().remove("activeTaskId").apply()
                                progressPercent = null
                            } catch (error: Exception) {
                                status = error.message ?: "恢复任务同步失败。"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("恢复同步下载任务") }
            }

            if (busy && activeTaskId != null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            try {
                                val taskId = activeTaskId ?: return@launch
                                withContext(Dispatchers.IO) {
                                    deleteJson(apiBase, "/api/tasks/" + taskId)
                                }
                                status = "已发送取消请求，正在等待引擎确认…"
                            } catch (error: Exception) {
                                status = error.message ?: "取消请求失败。"
                            }
                        }
                    }
                ) { Text("取消下载任务") }
            }
        }

        if (busy && progressPercent != null) {
            LinearProgressIndicator(
                progress = { (progressPercent!! / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text("下载进度：" + progressPercent!!.toInt() + "%")
        } else if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text(status, style = MaterialTheme.typography.bodyMedium)

        Button(
            enabled = !historyLoading && apiBase.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    historyLoading = true
                    try {
                        recentTasks = withContext(Dispatchers.IO) {
                            getRecentTasks(apiBase, ownerToken)
                        }
                    } catch (error: Exception) {
                        status = error.message ?: "读取下载历史失败。"
                    } finally {
                        historyLoading = false
                    }
                }
            }
        ) { Text(if (historyLoading) "正在读取下载记录…" else "刷新下载历史") }

        if (recentTasks.isNotEmpty()) {
            Text("最近下载", style = MaterialTheme.typography.titleLarge)
            recentTasks.forEach { item ->
                val taskId = item.optString("id")
                val taskState = item.optString("state", "unknown")
                val taskQuality = item.optString("quality", "best")
                val fileName = item.optString("filename").takeIf { it.isNotBlank() }
                    ?: "任务 " + taskId.take(8)
                val progress = item.optJSONObject("progress")
                val percent = if (progress != null && !progress.isNull("percent")) {
                    progress.optDouble("percent", -1.0).takeIf { it >= 0.0 }
                } else {
                    null
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(fileName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "状态：" + taskStateLabel(taskState) + " · 画质：" + taskQuality,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (percent != null) {
                            LinearProgressIndicator(
                                progress = { (percent.toFloat() / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("传输进度：" + percent.toInt() + "%")
                        }
                        item.optString("error").takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        if (taskState == "completed") {
                            Button(
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        status = "正在保存历史任务文件…"
                                        try {
                                            val saved = withContext(Dispatchers.IO) {
                                                downloadTaskToDownloads(
                                                    context.contentResolver,
                                                    apiBase,
                                                    taskId,
                                                    taskQuality
                                                )
                                            }
                                            status = "已保存：" + saved
                                        } catch (error: Exception) {
                                            status = error.message ?: "保存文件失败。"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            ) { Text("重新保存到手机") }
                        } else if (taskState !in listOf("failed", "cancelled") && !busy) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    activeTaskId = taskId
                                    prefs.edit().putString("activeTaskId", taskId).apply()
                                    scope.launch {
                                        busy = true
                                        progressPercent = null
                                        try {
                                            val saved = waitForDownloadTask(
                                                apiBase,
                                                taskId,
                                                { status = it },
                                                { progressPercent = it },
                                                {
                                                    activeTaskId = null
                                                    prefs.edit().remove("activeTaskId").apply()
                                                }
                                            ) { completedId ->
                                                withContext(Dispatchers.IO) {
                                                    downloadTaskToDownloads(
                                                        context.contentResolver,
                                                        apiBase,
                                                        completedId,
                                                        taskQuality
                                                    )
                                                }
                                            }
                                            status = "下载完成：" + saved
                                            activeTaskId = null
                                            prefs.edit().remove("activeTaskId").apply()
                                        } catch (error: Exception) {
                                            status = error.message ?: "恢复下载任务失败。"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            ) { Text("继续同步此任务") }
                        }
                    }
                }
            }
        }
    }
}

private fun postJson(
    base: String,
    path: String,
    payload: JSONObject,
    ownerToken: String? = null
): JSONObject {
    val body = payload.toString().toRequestBody(
        "application/json; charset=utf-8".toMediaType()
    )
    val builder = Request.Builder().url(normalizeBase(base) + path).post(body)
    ownerToken?.let { builder.header("X-VideoDL-Owner", it) }
    val request = builder.build()
    httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(extractError(text, response.code))
        return JSONObject(text)
    }
}


private fun getRecentTasks(base: String, ownerToken: String): List<JSONObject> {
    val request = Request.Builder()
        .url(normalizeBase(base) + "/api/tasks?limit=20")
        .header("X-VideoDL-Owner", ownerToken)
        .get()
        .build()
    val response = httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(extractError(text, response.code))
        JSONObject(text)
    }
    val array = response.optJSONArray("tasks") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)
    }
}

private fun taskStateLabel(state: String): String {
    return when (state) {
        "queued" -> "排队中"
        "resolving" -> "解析中"
        "downloading" -> "下载中"
        "processing" -> "处理中"
        "cancelling" -> "正在取消"
        "completed" -> "已完成"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        else -> "未知状态"
    }
}

private fun getJson(base: String, path: String): JSONObject {
    val request = Request.Builder().url(normalizeBase(base) + path).get().build()
    httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(extractError(text, response.code))
        return JSONObject(text)
    }
}

private fun deleteJson(base: String, path: String) {
    val request = Request.Builder().url(normalizeBase(base) + path).delete().build()
    httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(extractError(text, response.code))
    }
}

private suspend fun downloadWithTaskSync(
    apiBase: String,
    videoUrl: String,
    quality: String,
    ownerToken: String,
    onTaskCreated: (String) -> Unit,
    onStatus: (String) -> Unit,
    onProgress: (Float?) -> Unit,
    onTaskTerminal: () -> Unit,
    fetchCompletedFile: suspend (taskId: String) -> String
): String {
    val created = withContext(Dispatchers.IO) {
        postJson(
            apiBase,
            "/api/tasks",
            JSONObject().put("url", videoUrl).put("quality", quality),
            ownerToken = ownerToken
        )
    }
    val taskId = created.getJSONObject("task").getString("id")
    onTaskCreated(taskId)
    return waitForDownloadTask(apiBase, taskId, onStatus, onProgress, onTaskTerminal, fetchCompletedFile)
}

private suspend fun waitForDownloadTask(
    apiBase: String,
    taskId: String,
    onStatus: (String) -> Unit,
    onProgress: (Float?) -> Unit,
    onTaskTerminal: () -> Unit,
    fetchCompletedFile: suspend (taskId: String) -> String
): String {
    while (true) {
        val task = withContext(Dispatchers.IO) {
            getJson(apiBase, "/api/tasks/" + taskId).getJSONObject("task")
        }
        val state = task.optString("state", "unknown")
        val progress = task.optJSONObject("progress")
        val percent = if (progress != null && !progress.isNull("percent")) {
            progress.optDouble("percent", -1.0).takeIf { it >= 0.0 }?.toFloat()
        } else {
            null
        }
        onProgress(percent)

        when (state) {
            "queued" -> onStatus("下载任务已排队，等待空闲工作线程…")
            "resolving" -> onStatus("正在解析视频和选择媒体流…")
            "downloading" -> {
                val speed = progress?.optDouble("speed_bytes_per_second", 0.0) ?: 0.0
                val speedText = if (speed > 0.0) {
                    "，速度 " + formatSpeed(speed)
                } else {
                    ""
                }
                onStatus(
                    if (percent != null) {
                        "正在下载：" + percent.toInt() + "%" + speedText
                    } else {
                        "正在下载媒体流" + speedText
                    }
                )
            }
            "processing" -> onStatus("媒体流下载完成，正在合并和处理文件…")
            "cancelling" -> onStatus("引擎正在取消下载任务…")
            "completed" -> {
                onStatus("下载完成，正在保存到系统 Downloads/VideoDL…")
                return fetchCompletedFile(taskId)
            }
            "failed" -> {
                onTaskTerminal()
                throw IOException(task.optString("error", "下载失败。"))
            }
            "cancelled" -> {
                onTaskTerminal()
                throw IOException("下载任务已取消。")
            }
            else -> onStatus("正在同步任务状态…")
        }
        delay(800L)
    }
}

private fun formatSpeed(bytesPerSecond: Double): String {
    return when {
        bytesPerSecond >= 1024.0 * 1024.0 ->
            String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024.0 ->
            String.format("%.1f KB/s", bytesPerSecond / 1024.0)
        else -> String.format("%.0f B/s", bytesPerSecond)
    }
}

private fun downloadTaskToDownloads(
    resolver: android.content.ContentResolver,
    base: String,
    taskId: String,
    quality: String
): String {
    val request = Request.Builder()
        .url(normalizeBase(base) + "/api/tasks/" + taskId + "/file")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val text = response.body?.string().orEmpty()
            throw IOException(extractError(text, response.code))
        }

        val extension = if (quality.trim().lowercase() == "audio") "mp3" else "mp4"
        val displayName = "VideoDL-" + System.currentTimeMillis() + "." + extension
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                if (extension == "mp3") "audio/mpeg" else "video/mp4"
            )
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/VideoDL"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建下载文件")

        try {
            val input = response.body?.byteStream()
                ?: throw IOException("服务端没有返回文件内容")
            input.use { source ->
                resolver.openOutputStream(uri)?.use { output ->
                    source.copyTo(output)
                } ?: throw IOException("无法写入系统下载目录")
            }
            val ready = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, ready, null, null)
            return "Downloads/VideoDL/" + displayName
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}

private fun normalizeBase(base: String): String {
    val normalized = base.trim().trimEnd('/')
    require(normalized.startsWith("https://")) { "远程服务必须使用 HTTPS。" }
    return normalized
}

private fun extractError(body: String, status: Int): String {
    return runCatching { JSONObject(body).optString("error") }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP " + status
}
