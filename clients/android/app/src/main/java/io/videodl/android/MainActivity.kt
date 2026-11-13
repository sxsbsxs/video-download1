package io.videodl.android

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
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
    val background = Color(0xFFF6F8FC)
    val surface = Color(0xFFFFFFFF)
    val blue = Color(0xFF0B57D0)
    val blueSoft = Color(0xFFE8F0FE)
    val ink = Color(0xFF202124)
    val muted = Color(0xFF5F6368)
    val line = Color(0xFFE0E3E7)
    val green = Color(0xFF188038)
    val greenSoft = Color(0xFFE6F4EA)
    val error = Color(0xFFB3261E)
}

@Composable
private fun VideoDLTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VideoDLColors.blue,
            onPrimary = Color.White,
            secondary = VideoDLColors.blue,
            background = VideoDLColors.background,
            onBackground = VideoDLColors.ink,
            surface = VideoDLColors.surface,
            onSurface = VideoDLColors.ink,
            surfaceVariant = Color(0xFFF1F3F4),
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
    val savedAt: Long,
    val contentUri: String = ""
)

private data class QualityGroup(
    val key: String,
    val width: Int,
    val height: Int,
    val fps: Double,
    val dynamicRange: String,
    val options: List<VideoFormat>
) {
    val preferred: VideoFormat get() = options.first()
    val title: String get() = if (height > 0) "${height}P" else "视频"
    val subtitle: String
        get() = buildList {
            if (width > 0 && height > 0) add("${width} × ${height}")
            if (fps > 0) add("${fpsLabel(fps)} FPS")
            if (dynamicRange.isNotBlank() && !dynamicRange.equals("SDR", true)) add(dynamicRange)
        }.joinToString(" · ").ifBlank { preferred.technicalSummary }
}

private fun buildQualityGroups(formats: List<VideoFormat>): List<QualityGroup> {
    return formats.groupBy { format ->
        val fps = if (format.fps > 0) String.format(Locale.US, "%.2f", format.fps) else "0"
        "${format.width}|${format.height}|\$fps|${format.dynamicRange.trim().lowercase(Locale.ROOT)}"
    }.map { (key, options) ->
        val sorted = options.sortedWith(
            compareByDescending<VideoFormat> { it.bitrate }
                .thenByDescending { it.fileSize }
                .thenBy { it.extension }
        )
        val best = sorted.first()
        QualityGroup(
            key = key,
            width = best.width,
            height = best.height,
            fps = best.fps,
            dynamicRange = best.dynamicRange,
            options = sorted
        )
    }.sortedWith(
        compareByDescending<QualityGroup> { it.height }
            .thenByDescending { it.fps }
            .thenByDescending { it.width }
    )
}

private fun fpsLabel(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

@Composable
private fun VideoDLApp(initialText: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPage by rememberSaveable { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf(initialText) }
    var mediaInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var selectedFormatTab by rememberSaveable { mutableStateOf(0) }
    var selectedFormatKey by rememberSaveable { mutableStateOf("") }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    var analyzing by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var history by remember { mutableStateOf(loadHistory(context)) }
    var settingsMessage by remember { mutableStateOf("") }

    val videoFormats = mediaInfo?.videoFormats.orEmpty()
    val audioFormats = mediaInfo?.audioFormats.orEmpty()
    val qualityGroups = remember(videoFormats) { buildQualityGroups(videoFormats) }
    val visibleFormats = if (selectedFormatTab == 0) videoFormats else audioFormats
    val selectedFormat = visibleFormats.firstOrNull { it.key == selectedFormatKey }
        ?: visibleFormats.firstOrNull()

    Scaffold(
        containerColor = VideoDLColors.background,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                contentColor = VideoDLColors.blue,
                tonalElevation = 8.dp
            ) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VideoDLColors.blue,
                    selectedTextColor = VideoDLColors.blue,
                    indicatorColor = VideoDLColors.blueSoft,
                    unselectedIconColor = VideoDLColors.muted,
                    unselectedTextColor = VideoDLColors.muted
                )
                NavigationBarItem(
                    selected = selectedPage == 0,
                    onClick = { selectedPage = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("首页") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = selectedPage == 1,
                    onClick = { selectedPage = 1 },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = { Text("下载") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = selectedPage == 2,
                    onClick = { selectedPage = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                    colors = navColors
                )
            }
        }
    ) { padding ->
        when (selectedPage) {
            0 -> HomeScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                input = input,
                onInputChange = {
                    input = it
                    mediaInfo = null
                    selectedFormatKey = ""
                    expandedGroups = emptySet()
                    errorText = ""
                    progress = null
                },
                analyzing = analyzing,
                downloading = downloading,
                mediaInfo = mediaInfo,
                selectedFormatTab = selectedFormatTab,
                onFormatTabChange = { tab ->
                    selectedFormatTab = tab
                    selectedFormatKey = if (tab == 0) {
                        videoFormats.firstOrNull()?.key.orEmpty()
                    } else {
                        audioFormats.firstOrNull()?.key.orEmpty()
                    }
                },
                visibleFormats = visibleFormats,
                qualityGroups = qualityGroups,
                selectedFormatKey = selectedFormatKey,
                expandedGroups = expandedGroups,
                onFormatSelect = { selectedFormatKey = it },
                onGroupToggle = { key ->
                    expandedGroups = if (key in expandedGroups) expandedGroups - key else expandedGroups + key
                },
                errorText = errorText,
                progress = progress,
                status = status,
                onPaste = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val copied = clipboard.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    if (!copied.isNullOrBlank()) {
                        input = copied
                        mediaInfo = null
                        selectedFormatKey = ""
                        expandedGroups = emptySet()
                        errorText = ""
                    }
                },
                onAnalyze = {
                    scope.launch {
                        analyzing = true
                        downloading = false
                        progress = null
                        errorText = ""
                        status = ""
                        try {
                            val result = LocalYtDlpEngine.inspect(context, input)
                            mediaInfo = result
                            selectedFormatTab = if (result.videoFormats.isNotEmpty()) 0 else 1
                            selectedFormatKey = result.videoFormats.firstOrNull()?.key
                                ?: result.audioFormats.firstOrNull()?.key.orEmpty()
                            expandedGroups = emptySet()
                            input = result.sourceUrl
                            status = "解析完成 · 找到 ${result.videoFormats.size + result.audioFormats.size} 种可用格式"
                        } catch (error: Exception) {
                            mediaInfo = null
                            status = "解析失败"
                            errorText = error.message ?: "无法读取该视频，请检查链接后重试。"
                        } finally {
                            analyzing = false
                        }
                    }
                },
                onDownload = {
                    val chosen = selectedFormat ?: return@HomeScreen
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
                                url = mediaInfo?.sourceUrl.orEmpty(),
                                format = chosen,
                                listener = listener
                            )
                            status = "正在保存到 Downloads/VideoDL…"
                            val savedUri = withContext(Dispatchers.IO) {
                                saveIntoDownloads(context, file, mediaInfo?.title.orEmpty())
                            }
                            val infoTitle = mediaInfo?.title.orEmpty()
                            val savedName = savedUri.lastPathSegment?.substringAfterLast('/') ?: file.name
                            val item = SavedDownload(
                                title = infoTitle,
                                fileName = savedName,
                                savedAt = System.currentTimeMillis(),
                                contentUri = savedUri.toString()
                            )
                            history = listOf(item) + history.filterNot { it.contentUri == item.contentUri }.take(19)
                            storeHistory(context, history)
                            LocalYtDlpEngine.cleanup(file.parentFile)
                            progress = 100f
                            status = "下载完成 · 已保存到 Downloads/VideoDL"
                            selectedPage = 1
                        } catch (error: Exception) {
                            status = "下载失败"
                            errorText = error.message ?: "下载过程中出现错误。"
                        } finally {
                            downloading = false
                        }
                    }
                }
            )
            1 -> DownloadListScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                history = history,
                downloading = downloading,
                progress = progress,
                status = status,
                onOpen = { openSavedDownload(context, it) }
            )
            else -> SettingsScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = settingsMessage,
                onClearHistory = {
                    history = emptyList()
                    storeHistory(context, emptyList())
                    settingsMessage = "下载列表记录已清除，手机中的文件不会被删除。"
                },
                onClearTemp = {
                    val cleared = File(context.cacheDir, "VideoDL").deleteRecursively()
                    settingsMessage = if (cleared) "临时下载缓存已清理。" else "没有需要清理的临时文件。"
                    Toast.makeText(context, settingsMessage, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun PageColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        content()
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            color = VideoDLColors.ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            color = VideoDLColors.muted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    input: String,
    onInputChange: (String) -> Unit,
    analyzing: Boolean,
    downloading: Boolean,
    mediaInfo: VideoInfo?,
    selectedFormatTab: Int,
    onFormatTabChange: (Int) -> Unit,
    visibleFormats: List<VideoFormat>,
    qualityGroups: List<QualityGroup>,
    selectedFormatKey: String,
    expandedGroups: Set<String>,
    onFormatSelect: (String) -> Unit,
    onGroupToggle: (String) -> Unit,
    errorText: String,
    progress: Float?,
    status: String,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onDownload: () -> Unit
) {
    val selectedFormat = visibleFormats.firstOrNull { it.key == selectedFormatKey } ?: visibleFormats.firstOrNull()

    PageColumn(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(VideoDLColors.blue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.VideoFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("VideoDL", color = VideoDLColors.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("视频下载", color = VideoDLColors.muted, style = MaterialTheme.typography.labelSmall)
            }
            Surface(color = VideoDLColors.greenSoft, shape = CircleShape) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(VideoDLColors.green))
                    Spacer(Modifier.width(6.dp))
                    Text("本机运行", color = VideoDLColors.green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        PageHeader(
            title = "下载视频",
            subtitle = "粘贴链接，获取来源实际提供的画质。"
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = VideoDLColors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text("视频链接", color = VideoDLColors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴视频链接或分享文案") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    enabled = !analyzing && !downloading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VideoDLColors.blue,
                        unfocusedBorderColor = VideoDLColors.line,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        cursorColor = VideoDLColors.blue
                    ),
                    trailingIcon = {
                        TextButton(onClick = onPaste, enabled = !analyzing && !downloading) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("粘贴")
                        }
                    }
                )
                Button(
                    onClick = onAnalyze,
                    enabled = input.isNotBlank() && !analyzing && !downloading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VideoDLColors.blue,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE8EAED),
                        disabledContentColor = VideoDLColors.muted
                    )
                ) {
                    if (analyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(9.dp))
                        Text("正在解析…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("获取视频信息", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (errorText.isNotBlank()) {
                    Text(errorText, color = VideoDLColors.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        mediaInfo?.let { info ->
            VideoSummaryCard(info)

            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("选择画质", color = VideoDLColors.ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("只列出源站实际返回的格式", color = VideoDLColors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${visibleFormats.size} 项", color = VideoDLColors.muted, style = MaterialTheme.typography.labelMedium)
                }

                FormatTypeSelector(
                    selectedTab = selectedFormatTab,
                    videoCount = info.videoFormats.size,
                    audioCount = info.audioFormats.size,
                    onSelect = onFormatTabChange
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
                ) {
                    Column {
                        if (selectedFormatTab == 0 && qualityGroups.isNotEmpty()) {
                            qualityGroups.forEachIndexed { index, group ->
                                QualityGroupRow(
                                    group = group,
                                    selected = group.options.any { it.key == selectedFormatKey },
                                    expanded = group.key in expandedGroups,
                                    onSelect = { onFormatSelect(group.preferred.key) },
                                    onToggleExpanded = { onGroupToggle(group.key) }
                                )
                                if (group.key in expandedGroups) {
                                    group.options.forEach { option ->
                                        FormatVariantRow(
                                            option = option,
                                            selected = option.key == selectedFormatKey,
                                            onSelect = { onFormatSelect(option.key) }
                                        )
                                    }
                                }
                                if (index != qualityGroups.lastIndex) {
                                    Divider(modifier = Modifier.padding(horizontal = 18.dp), color = VideoDLColors.line, thickness = 1.dp)
                                }
                            }
                        } else if (selectedFormatTab == 1 && info.audioFormats.isNotEmpty()) {
                            info.audioFormats.forEachIndexed { index, option ->
                                FormatVariantRow(
                                    option = option,
                                    selected = option.key == selectedFormatKey,
                                    onSelect = { onFormatSelect(option.key) },
                                    compact = false
                                )
                                if (index != info.audioFormats.lastIndex) {
                                    Divider(modifier = Modifier.padding(horizontal = 18.dp), color = VideoDLColors.line, thickness = 1.dp)
                                }
                            }
                        } else {
                            Text(
                                if (selectedFormatTab == 0) "该来源没有提供可用的视频流。" else "该来源没有提供独立音频格式。",
                                modifier = Modifier.padding(20.dp),
                                color = VideoDLColors.muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Text(
                    "大小来自源站数据或码率估算；估算值会标注“约”，无法可靠估算时显示“未知”。",
                    color = VideoDLColors.muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Button(
                onClick = onDownload,
                enabled = selectedFormat != null && !downloading && !analyzing,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VideoDLColors.blue,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE8EAED),
                    disabledContentColor = VideoDLColors.muted
                )
            ) {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("正在下载…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("下载到手机", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (downloading || progress != null) {
            DownloadProgressCard(status = status, progress = progress ?: 0f)
        }
        Text(
            "请仅下载你拥有版权、已获授权或平台允许下载的内容。",
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 12.dp),
            color = VideoDLColors.muted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun VideoSummaryCard(info: VideoInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VideoDLColors.surface,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xFFE8EAED)),
                contentAlignment = Alignment.Center
            ) {
                if (!info.thumbnailPath.isNullOrBlank() && File(info.thumbnailPath).isFile) {
                    AsyncImage(
                        model = File(info.thumbnailPath),
                        contentDescription = "视频封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.VideoFile, contentDescription = null, tint = Color(0xFF9AA0A6), modifier = Modifier.size(42.dp))
                        Text("封面暂不可用", color = VideoDLColors.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    info.title,
                    color = VideoDLColors.ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.uploader.isNotBlank()) {
                        Text(
                            info.uploader,
                            modifier = Modifier.weight(1f),
                            color = VideoDLColors.muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (info.durationSeconds > 0) {
                        Text(
                            formatDuration(info.durationSeconds),
                            color = VideoDLColors.muted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatTypeSelector(
    selectedTab: Int,
    videoCount: Int,
    audioCount: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFE9EDF3))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("视频 · $videoCount", "音频 · $audioCount").forEachIndexed { index, label ->
            val selected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) VideoDLColors.blue else VideoDLColors.muted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QualityGroupRow(
    group: QualityGroup,
    selected: Boolean,
    expanded: Boolean,
    onSelect: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.title, color = VideoDLColors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                group.subtitle,
                color = VideoDLColors.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (group.options.size > 1) {
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.height(28.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(if (expanded) "收起编码选项" else "其他编码格式 ${group.options.size - 1} 个", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(group.preferred.sizeLabel, color = VideoDLColors.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FormatVariantRow(
    option: VideoFormat,
    selected: Boolean,
    onSelect: () -> Unit,
    compact: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(
                start = if (compact) 54.dp else 12.dp,
                end = 14.dp,
                top = 9.dp,
                bottom = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(option.primaryLabel, color = VideoDLColors.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                option.technicalSummary,
                color = VideoDLColors.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(option.sizeLabel, color = VideoDLColors.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DownloadProgressCard(status: String, progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, modifier = Modifier.weight(1f), color = VideoDLColors.ink, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${progress.toInt()}%", color = VideoDLColors.blue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { (progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = VideoDLColors.blue,
                trackColor = Color(0xFFE8EAED)
            )
        }
    }
}

@Composable
private fun DownloadListScreen(
    modifier: Modifier,
    history: List<SavedDownload>,
    downloading: Boolean,
    progress: Float?,
    status: String,
    onOpen: (SavedDownload) -> Unit
) {
    PageColumn(modifier) {
        PageHeader("下载列表", "查看当前进度和已经保存到手机的文件。")

        if (downloading) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("正在下载", color = VideoDLColors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DownloadProgressCard(status = status, progress = progress ?: 0f)
            }
        }

        if (history.isEmpty() && !downloading) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(VideoDLColors.blueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = VideoDLColors.blue, modifier = Modifier.size(31.dp))
                    }
                    Text("还没有下载记录", color = VideoDLColors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("完成下载后，文件会显示在这里。", color = VideoDLColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (history.isNotEmpty()) {
            Text("已完成", color = VideoDLColors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
            ) {
                Column {
                    history.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item) }
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(VideoDLColors.greenSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VideoDLColors.green)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.title, color = VideoDLColors.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.fileName, color = VideoDLColors.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.savedAt)),
                                    color = VideoDLColors.muted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Icon(Icons.Filled.FolderOpen, contentDescription = "打开文件", tint = VideoDLColors.blue)
                        }
                        if (index != history.lastIndex) {
                            Divider(modifier = Modifier.padding(start = 69.dp, end = 14.dp), color = VideoDLColors.line)
                        }
                    }
                }
            }
            Text("点击任意记录可调用手机上的视频或音频播放器。", color = VideoDLColors.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    message: String,
    onClearHistory: () -> Unit,
    onClearTemp: () -> Unit
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    PageColumn(modifier) {
        PageHeader("设置", "管理保存位置、临时文件和应用信息。")

        SettingsGroup(title = "下载") {
            SettingsRow(
                icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = VideoDLColors.blue) },
                title = "保存位置",
                subtitle = "Downloads/VideoDL"
            )
            Divider(color = VideoDLColors.line)
            SettingsRow(
                icon = { Icon(Icons.Filled.VideoFile, contentDescription = null, tint = VideoDLColors.blue) },
                title = "下载引擎",
                subtitle = "本机 Python + yt-dlp，不需要服务器或 API"
            )
        }

        SettingsGroup(title = "存储管理") {
            SettingsRow(
                icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = VideoDLColors.blue) },
                title = "清理临时文件",
                subtitle = "删除未完成任务的临时缓存，不影响已保存文件",
                trailing = {
                    TextButton(onClick = onClearTemp) { Text("清理") }
                }
            )
            Divider(color = VideoDLColors.line)
            SettingsRow(
                icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = VideoDLColors.error) },
                title = "清空下载列表",
                subtitle = "只清除应用内记录，不会删除手机文件",
                trailing = {
                    TextButton(onClick = { showClearHistoryDialog = true }) {
                        Text("清空", color = VideoDLColors.error)
                    }
                }
            )
        }

        SettingsGroup(title = "关于") {
            SettingsRow(
                icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = VideoDLColors.blue) },
                title = "VideoDL",
                subtitle = "版本 0.4.0 · ARM64"
            )
            Divider(color = VideoDLColors.line)
            Text(
                "请仅下载你拥有版权、已获授权或平台允许下载的内容。应用不会绕过 DRM、付费墙或访问控制。",
                modifier = Modifier.padding(16.dp),
                color = VideoDLColors.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (message.isNotBlank()) {
            Surface(color = VideoDLColors.greenSoft, shape = RoundedCornerShape(16.dp)) {
                Text(message, modifier = Modifier.padding(14.dp), color = VideoDLColors.green, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("清空下载列表？") },
            text = { Text("这只会删除 VideoDL 中的下载记录，已经保存到手机的文件不会受到影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearHistory()
                    }
                ) { Text("清空记录", color = VideoDLColors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, color = VideoDLColors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(VideoDLColors.blueSoft),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = VideoDLColors.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = VideoDLColors.muted, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke()
    }
}

private fun saveIntoDownloads(context: Context, source: File, title: String): Uri {
    val extension = source.extension.ifBlank { "mp4" }
    val safeTitle = title
        .filterNot { it.isISOControl() }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .trim()
        .take(90)
        .ifBlank { "VideoDL" }
    val displayName = "$safeTitle.$extension"
    val mime = mimeTypeFor(extension)
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
        return uri
    } catch (error: Exception) {
        context.contentResolver.delete(uri, null, null)
        throw error
    }
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
            SavedDownload(
                title = item.optString("title"),
                fileName = item.optString("fileName"),
                savedAt = item.optLong("savedAt"),
                contentUri = item.optString("contentUri")
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

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, remaining)
    else String.format("%d:%02d", minutes, remaining)
}
