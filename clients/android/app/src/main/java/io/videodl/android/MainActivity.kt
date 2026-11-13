package io.videodl.android

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }
        setContent {
            VideoDLTheme {
                VideoDLApp(sharedText)
            }
        }
    }
}

private object VideoDLColors {
    val background = Color(0xFFF3F4F0)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF171B18)
    val muted = Color(0xFF737B73)
    val line = Color(0xFFE4E8E1)
    val lime = Color(0xFFD5F56B)
    val limeSoft = Color(0xFFF0F8D9)
    val error = Color(0xFFB3261E)
}

@Composable
private fun VideoDLTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VideoDLColors.lime,
            onPrimary = VideoDLColors.ink,
            secondary = Color(0xFF567044),
            background = VideoDLColors.background,
            onBackground = VideoDLColors.ink,
            surface = VideoDLColors.surface,
            onSurface = VideoDLColors.ink,
            surfaceVariant = Color(0xFFEBEEE8),
            onSurfaceVariant = VideoDLColors.muted,
            outline = VideoDLColors.line,
            error = VideoDLColors.error
        ),
        content = content
    )
}

private data class SavedDownload(
    val title: String,
    val fileName: String,
    val savedAt: Long
)

@Composable
private fun VideoDLApp(initialText: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf(initialText) }
    var mediaInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var selectedFormatKey by rememberSaveable { mutableStateOf("") }
    var analyzing by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("粘贴视频链接，VideoDL 会直接在本机解析。") }
    var errorText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var history by remember { mutableStateOf(loadHistory(context)) }

    val visibleFormats = when (selectedTab) {
        0 -> mediaInfo?.videoFormats.orEmpty()
        else -> mediaInfo?.audioFormats.orEmpty()
    }
    val selectedFormat = visibleFormats.firstOrNull { it.key == selectedFormatKey }
        ?: visibleFormats.firstOrNull()

    Scaffold(containerColor = VideoDLColors.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "VideoDL",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = VideoDLColors.ink
                        )
                        Spacer(Modifier.width(9.dp))
                        Surface(shape = RoundedCornerShape(50), color = VideoDLColors.limeSoft) {
                            Text(
                                "LOCAL",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = Color(0xFF526A25),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        "VIDEO DOWNLOADER",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoDLColors.muted,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = VideoDLColors.surface,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.History, contentDescription = "下载历史", tint = VideoDLColors.ink)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "把喜欢的视频，\n保存到手机。",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = VideoDLColors.ink
                )
                Text(
                    "无需填写服务器地址。解析和下载都由本机完成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VideoDLColors.muted
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = VideoDLColors.surface,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Link, null, tint = VideoDLColors.muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("视频链接", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val copied = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (!copied.isNullOrBlank()) {
                                    input = copied
                                    errorText = ""
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("粘贴")
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            errorText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("粘贴链接，或包含链接的分享文案") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                analyzing = true
                                downloading = false
                                errorText = ""
                                progress = null
                                status = "正在连接视频来源并读取媒体信息…"
                                try {
                                    val result = LocalYtDlpEngine.inspect(context, input)
                                    mediaInfo = result
                                    selectedTab = if (result.videoFormats.isNotEmpty()) 0 else 1
                                    selectedFormatKey = result.videoFormats.firstOrNull()?.key
                                        ?: result.audioFormats.firstOrNull()?.key.orEmpty()
                                    input = result.sourceUrl
                                    status = "解析完成 · 找到 ${result.videoFormats.size} 种视频格式和 ${result.audioFormats.size} 种音频格式"
                                } catch (error: Exception) {
                                    mediaInfo = null
                                    status = "解析失败"
                                    errorText = error.message ?: "无法读取该视频，请检查链接后重试。"
                                } finally {
                                    analyzing = false
                                }
                            }
                        },
                        enabled = input.isNotBlank() && !analyzing && !downloading,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VideoDLColors.ink,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFB8BEB6),
                            disabledContentColor = Color.White
                        )
                    ) {
                        if (analyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("正在解析…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Outlined.Search, null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("解析视频", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (errorText.isNotBlank()) {
                        Text(errorText, color = VideoDLColors.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            mediaInfo?.let { info ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = VideoDLColors.surface,
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        if (!info.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = info.thumbnail,
                                contentDescription = "视频封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(15.dp))
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                shape = RoundedCornerShape(15.dp),
                                color = Color(0xFFE8ECE5)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Movie, null, tint = Color(0xFF9AA497), modifier = Modifier.size(48.dp))
                                }
                            }
                        }
                        Text(
                            info.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (info.uploader.isNotBlank()) {
                                Text(
                                    info.uploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoDLColors.muted,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (info.durationSeconds > 0) {
                                Text(
                                    formatDuration(info.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoDLColors.muted
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("选择下载格式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "显示来源实际提供的清晰度与编码",
                                style = MaterialTheme.typography.bodySmall,
                                color = VideoDLColors.muted
                            )
                        }
                        Text(
                            "${visibleFormats.size} 个选项",
                            style = MaterialTheme.typography.labelMedium,
                            color = VideoDLColors.muted
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = VideoDLColors.surface
                    ) {
                        Column {
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = VideoDLColors.surface,
                                contentColor = VideoDLColors.ink
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = {
                                        selectedTab = 0
                                        selectedFormatKey = info.videoFormats.firstOrNull()?.key.orEmpty()
                                    },
                                    text = { Text("视频  ${info.videoFormats.size}", fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Outlined.Movie, null, modifier = Modifier.size(17.dp)) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        selectedTab = 1
                                        selectedFormatKey = info.audioFormats.firstOrNull()?.key.orEmpty()
                                    },
                                    text = { Text("音频  ${info.audioFormats.size}", fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(17.dp)) }
                                )
                            }

                            if (visibleFormats.isEmpty()) {
                                Text(
                                    if (selectedTab == 0) "来源没有提供可用的视频格式" else "来源没有提供独立音频格式",
                                    modifier = Modifier.padding(18.dp),
                                    color = VideoDLColors.muted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    visibleFormats.forEach { format ->
                                        FormatOptionRow(
                                            option = format,
                                            selected = format.key == (selectedFormat?.key ?: selectedFormatKey),
                                            onClick = { selectedFormatKey = format.key }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val chosen = selectedFormat
                        if (chosen == null) return@Button
                        scope.launch {
                            downloading = true
                            progress = 0f
                            status = "正在准备下载…"
                            errorText = ""
                            try {
                                val listener = object : LocalYtDlpProgress {
                                    override fun onProgressUpdate(percent: Float, etaInSeconds: Long, line: String) {
                                        scope.launch {
                                            progress = percent.coerceIn(0f, 100f)
                                            status = line
                                        }
                                    }
                                }
                                val file = LocalYtDlpEngine.download(
                                    context = context,
                                    url = info.sourceUrl,
                                    format = chosen,
                                    listener = listener
                                )
                                status = "正在保存到手机 Downloads/VideoDL…"
                                val savedName = withContext(Dispatchers.IO) {
                                    saveIntoDownloads(context, file, info.title)
                                }
                                history = listOf(
                                    SavedDownload(info.title, savedName, System.currentTimeMillis())
                                ) + history.filterNot { it.fileName == savedName }.take(9)
                                storeHistory(context, history)
                                LocalYtDlpEngine.cleanup(file.parentFile)
                                progress = 100f
                                status = "下载完成，已保存到 Downloads/VideoDL"
                            } catch (error: Exception) {
                                status = "下载失败"
                                errorText = error.message ?: "下载过程中出现错误。"
                            } finally {
                                downloading = false
                            }
                        }
                    },
                    enabled = selectedFormat != null && !downloading && !analyzing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VideoDLColors.lime,
                        contentColor = VideoDLColors.ink,
                        disabledContainerColor = Color(0xFFDDE2D9),
                        disabledContentColor = VideoDLColors.muted
                    )
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = VideoDLColors.ink,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("下载中…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Outlined.Download, null)
                        Spacer(Modifier.width(9.dp))
                        Text("下载到手机", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (downloading || progress != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = VideoDLColors.surface
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                status,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = VideoDLColors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (progress != null) {
                                Text(
                                    "${progress!!.toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (progress ?: 0f) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF7CA52B),
                            trackColor = Color(0xFFE5EADF)
                        )
                    }
                }
            } else if (mediaInfo == null && errorText.isBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF7B9860), modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(status, color = VideoDLColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("最近保存", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${history.size} 项", color = VideoDLColors.muted, style = MaterialTheme.typography.labelSmall)
                    }
                    history.take(5).forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = VideoDLColors.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = VideoDLColors.limeSoft,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF567044))
                                    }
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(item.fileName, style = MaterialTheme.typography.bodySmall, color = VideoDLColors.muted)
                                }
                                Text(
                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.savedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoDLColors.muted
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "请仅下载你拥有版权、已获授权或平台允许下载的内容。",
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = VideoDLColors.muted
            )
        }
    }
}

@Composable
private fun FormatOptionRow(
    option: VideoFormat,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) VideoDLColors.limeSoft else VideoDLColors.surface
    val stroke = if (selected) Color(0xFFB8D96B) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = if (selected) VideoDLColors.lime else Color(0xFFF0F2ED)
        ) {
            Text(
                option.primaryLabel,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                color = VideoDLColors.ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                option.secondaryLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = VideoDLColors.ink,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                option.detailLabel,
                style = MaterialTheme.typography.labelSmall,
                color = VideoDLColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(option.sizeLabel, style = MaterialTheme.typography.labelSmall, color = VideoDLColors.muted)
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF5B7B2E), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun saveIntoDownloads(context: Context, source: File, title: String): String {
    val extension = source.extension.ifBlank { "mp4" }
    val safeTitle = title
        .replace(Regex("[\\\\/:*?\\\"<>|\\\\p{Cntrl}]"), "_")
        .trim()
        .take(90)
        .ifBlank { "VideoDL" }
    val displayName = "$safeTitle.$extension"
    val mime = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideoDL")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("无法创建系统下载文件")
    try {
        source.inputStream().use { input ->
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                input.copyTo(output)
            } ?: throw IllegalStateException("无法写入系统下载目录")
        }
        val complete = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(uri, complete, null, null)
        return displayName
    } catch (error: Exception) {
        context.contentResolver.delete(uri, null, null)
        throw error
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
                title = item.optString("title"),
                fileName = item.optString("fileName"),
                savedAt = item.optLong("savedAt")
            )
        }
    }.getOrDefault(emptyList())
}

private fun storeHistory(context: Context, items: List<SavedDownload>) {
    val array = JSONArray()
    items.take(10).forEach { item ->
        array.put(
            JSONObject()
                .put("title", item.title)
                .put("fileName", item.fileName)
                .put("savedAt", item.savedAt)
        )
    }
    context.getSharedPreferences("videodl-local", Context.MODE_PRIVATE)
        .edit().putString("history", array.toString()).apply()
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, remaining)
    else String.format("%d:%02d", minutes, remaining)
}
