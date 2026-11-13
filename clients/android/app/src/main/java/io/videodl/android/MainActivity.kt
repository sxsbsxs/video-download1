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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
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
    .build()

@Composable
private fun VideoDLScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiBase by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("best") }
    var title by remember { mutableStateOf("") }
    var uploader by remember { mutableStateOf("") }
    var thumbnail by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("填写服务端地址和公开视频链接后开始解析。") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("VideoDL", style = MaterialTheme.typography.headlineMedium)
        Text("多语言视频下载客户端", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = apiBase, onValueChange = { apiBase = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("服务端 API 地址（HTTPS）") },
            placeholder = { Text("https://your-videodl-service.example") }, singleLine = true
        )
        OutlinedTextField(
            value = videoUrl, onValueChange = { videoUrl = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("视频链接") }, placeholder = { Text("粘贴公开视频链接") }, minLines = 2
        )
        OutlinedTextField(
            value = quality, onValueChange = { quality = it }, modifier = Modifier.fillMaxWidth(),
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
                        AsyncImage(model = it, contentDescription = "视频封面",
                            modifier = Modifier.fillMaxWidth().height(190.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (uploader.isNotBlank()) Text("上传者：" + uploader)
                }
            }
            Button(
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        status = "正在下载并保存到系统下载目录…"
                        try {
                            val saved = withContext(Dispatchers.IO) {
                                downloadToDownloads(context.contentResolver, apiBase, videoUrl, quality)
                            }
                            status = "下载完成：" + saved
                        } catch (error: Exception) {
                            status = error.message ?: "下载失败。"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("下载到手机") }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun postJson(base: String, path: String, payload: JSONObject): JSONObject {
    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder().url(normalizeBase(base) + path).post(body).build()
    httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(extractError(text, response.code))
        return JSONObject(text)
    }
}

private fun downloadToDownloads(
    resolver: android.content.ContentResolver,
    base: String,
    videoUrl: String,
    quality: String
): String {
    val payload = JSONObject().put("url", videoUrl).put("quality", quality)
    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder().url(normalizeBase(base) + "/api/download").post(body).build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val text = response.body?.string().orEmpty()
            throw IOException(extractError(text, response.code))
        }
        val extension = if (quality == "audio") "mp3" else "mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VideoDL-" + System.currentTimeMillis() + "." + extension)
            put(MediaStore.MediaColumns.MIME_TYPE, if (quality == "audio") "audio/mpeg" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideoDL")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建下载文件")
        try {
            val input = response.body?.byteStream() ?: throw IOException("服务端没有返回文件内容")
            input.use { source ->
                resolver.openOutputStream(uri)?.use { output ->
                    source.copyTo(output)
                } ?: throw IOException("无法写入系统下载目录")
            }
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
            return "Downloads/VideoDL"
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
        .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $status"
}
