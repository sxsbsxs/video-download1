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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    val background = Color(0xFFF8F9FA)
    val surface = Color(0xFFFFFFFF)
    val blue = Color(0xFF1A73E8)
    val blueSoft = Color(0xFFE8F0FE)
    val ink = Color(0xFF202124)
    val muted = Color(0xFF5F6368)
    val line = Color(0xFFDADCE0)
    val error = Color(0xFFD93025)
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
    val savedAt: Long
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

    var input by rememberSaveable { mutableStateOf(initialText) }
    var mediaInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var selectedFormatKey by rememberSaveable { mutableStateOf("") }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    var analyzing by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var history by remember { mutableStateOf(loadHistory(context)) }

    val videoFormats = mediaInfo?.videoFormats.orEmpty()
    val audioFormats = mediaInfo?.audioFormats.orEmpty()
    val qualityGroups = remember(videoFormats) { buildQualityGroups(videoFormats) }
    val visibleFormats = if (selectedTab == 0) videoFormats else audioFormats
    val selectedFormat = visibleFormats.firstOrNull { it.key == selectedFormatKey }
        ?: visibleFormats.firstOrNull()

    Scaffold(containerColor = VideoDLColors.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TopBar()

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "下载视频",
                    color = VideoDLColors.ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "粘贴视频链接，查看源站实际提供的画质。",
                    color = VideoDLColors.muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            LinkInputCard(
                value = input,
                onValueChange = {
                    input = it
                    mediaInfo = null
                    selectedFormatKey = ""
                    expandedGroups = emptySet()
                    errorText = ""
                    progress = null
                },
                analyzing = analyzing,
                downloading = downloading,
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
                            selectedTab = if (result.videoFormats.isNotEmpty()) 0 else 1
                            selectedFormatKey = result.videoFormats.firstOrNull()?.key
                                ?: result.audioFormats.firstOrNull()?.key.orEmpty()
                            expandedGroups = emptySet()
                            input = result.sourceUrl
                            status = "已读取源站返回的 ${result.videoFormats.size + result.audioFormats.size} 种原始格式"
                        } catch (error: Exception) {
                            mediaInfo = null
                            status = "解析失败"
                            errorText = error.message ?: "无法读取该视频，请检查链接后重试。"
                        } finally {
                            analyzing = false
                        }
                    }
                },
                errorText = errorText
            )

            mediaInfo?.let { info ->
                VideoSummaryCard(info)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "选择画质",
                                color = VideoDLColors.ink,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "只显示这个视频真实可下载的格式",
                                color = VideoDLColors.muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "${visibleFormats.size} 种格式",
                            color = VideoDLColors.muted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    FormatTypeSelector(
                        selectedTab = selectedTab,
                        videoCount = videoFormats.size,
                        audioCount = audioFormats.size,
                        onSelect = { tab ->
                            selectedTab = tab
                            selectedFormatKey = if (tab == 0) {
                                videoFormats.firstOrNull()?.key.orEmpty()
                            } else {
                                audioFormats.firstOrNull()?.key.orEmpty()
                            }
                        }
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = VideoDLColors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
                    ) {
                        Column {
                            if (selectedTab == 0 && qualityGroups.isNotEmpty()) {
                                qualityGroups.forEachIndexed { index, group ->
                                    QualityGroupRow(
                                        group = group,
                                        selected = group.options.any { it.key == selectedFormatKey },
                                        expanded = group.key in expandedGroups,
                                        onSelect = { selectedFormatKey = group.preferred.key },
                                        onToggleExpanded = {
                                            expandedGroups = if (group.key in expandedGroups) {
                                                expandedGroups - group.key
                                            } else {
                                                expandedGroups + group.key
                                            }
                                        }
                                    )
                                    if (group.key in expandedGroups) {
                                        group.options.forEach { option ->
                                            FormatVariantRow(
                                                option = option,
                                                selected = option.key == selectedFormatKey,
                                                onSelect = { selectedFormatKey = option.key }
                                            )
                                        }
                                    }
                                    if (index != qualityGroups.lastIndex) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            color = VideoDLColors.line,
                                            thickness = 1.dp
                                        )
                                    }
                                }
                            } else if (selectedTab == 1 && audioFormats.isNotEmpty()) {
                                audioFormats.forEachIndexed { index, option ->
                                    FormatVariantRow(
                                        option = option,
                                        selected = option.key == selectedFormatKey,
                                        onSelect = { selectedFormatKey = option.key },
                                        compact = false
                                    )
                                    if (index != audioFormats.lastIndex) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            color = VideoDLColors.line,
                                            thickness = 1.dp
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    if (selectedTab == 0) "这个来源没有提供可用的视频流。"
                                    else "这个来源没有提供独立音频格式。",
                                    modifier = Modifier.padding(18.dp),
                                    color = VideoDLColors.muted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Text(
                        "分辨率、帧率、HDR、编码和格式 ID 均来自源站解析结果；标注“约”的体积是来源估算值。",
                        color = VideoDLColors.muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = {
                        val chosen = selectedFormat ?: return@Button
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
                                status = "正在保存到 Downloads/VideoDL…"
                                val savedName = withContext(Dispatchers.IO) {
                                    saveIntoDownloads(context, file, info.title)
                                }
                                history = listOf(
                                    SavedDownload(info.title, savedName, System.currentTimeMillis())
                                ) + history.filterNot { it.fileName == savedName }.take(9)
                                storeHistory(context, history)
                                LocalYtDlpEngine.cleanup(file.parentFile)
                                progress = 100f
                                status = "下载完成 · 已保存到 Downloads/VideoDL"
                            } catch (error: Exception) {
                                status = "下载失败"
                                errorText = error.message ?: "下载过程中出现错误。"
                            } finally {
                                downloading = false
                            }
                        }
                    },
                    enabled = selectedFormat != null && !downloading && !analyzing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VideoDLColors.blue,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE8EAED),
                        disabledContentColor = VideoDLColors.muted
                    )
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("正在下载…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("下载到手机", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (downloading || progress != null) {
                DownloadProgressCard(status = status, progress = progress ?: 0f)
            }

            if (errorText.isNotBlank() && mediaInfo != null) {
                Text(
                    errorText,
                    color = VideoDLColors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (history.isNotEmpty()) {
                RecentDownloads(history = history.take(5))
            }

            Text(
                "请仅下载你拥有版权、已获授权或平台允许下载的内容。",
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp),
                color = VideoDLColors.muted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(VideoDLColors.blue),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "VideoDL",
            color = VideoDLColors.ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            "视频下载器",
            color = VideoDLColors.muted,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun LinkInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    analyzing: Boolean,
    downloading: Boolean,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    errorText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VideoDLColors.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "视频链接",
                    color = VideoDLColors.ink,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onPaste, enabled = !analyzing && !downloading) {
                    Text("粘贴", color = VideoDLColors.blue, fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴链接，或包含链接的分享文案") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(13.dp),
                enabled = !analyzing && !downloading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VideoDLColors.blue,
                    unfocusedBorderColor = VideoDLColors.line,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = VideoDLColors.blue
                )
            )
            Button(
                onClick = onAnalyze,
                enabled = value.isNotBlank() && !analyzing && !downloading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VideoDLColors.blue,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE8EAED),
                    disabledContentColor = VideoDLColors.muted
                )
            ) {
                if (analyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在解析…")
                } else {
                    Text("获取视频信息", fontWeight = FontWeight.SemiBold)
                }
            }
            if (errorText.isNotBlank() && !analyzing) {
                Text(
                    errorText,
                    color = VideoDLColors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun VideoSummaryCard(info: VideoInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VideoDLColors.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!info.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = "视频封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(116.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(11.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(116.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFFF1F3F4)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("VIDEO", color = VideoDLColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    info.title,
                    color = VideoDLColors.ink,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.uploader.isNotBlank()) {
                    Text(
                        info.uploader,
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
                        style = MaterialTheme.typography.labelSmall
                    )
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
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEFF1F3))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("视频  $videoCount", "音频  $audioCount").forEachIndexed { index, label ->
            val selected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
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
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(5.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                group.title,
                color = VideoDLColors.ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        if (expanded) "收起其他格式" else "其他编码格式 ${group.options.size - 1} 个",
                        color = VideoDLColors.blue,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            group.preferred.sizeLabel,
            color = VideoDLColors.muted,
            style = MaterialTheme.typography.labelSmall
        )
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
                start = if (compact) 49.dp else 10.dp,
                end = 12.dp,
                top = 9.dp,
                bottom = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                option.primaryLabel,
                color = VideoDLColors.ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                option.technicalSummary,
                color = VideoDLColors.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            option.sizeLabel,
            color = VideoDLColors.muted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DownloadProgressCard(status: String, progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VideoDLColors.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status,
                    modifier = Modifier.weight(1f),
                    color = VideoDLColors.ink,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${progress.toInt()}%",
                    color = VideoDLColors.blue,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
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
private fun RecentDownloads(history: List<SavedDownload>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "最近下载",
            color = VideoDLColors.ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = VideoDLColors.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VideoDLColors.line)
        ) {
            Column {
                history.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(VideoDLColors.blueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↓", color = VideoDLColors.blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                item.title,
                                color = VideoDLColors.ink,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.fileName,
                                color = VideoDLColors.muted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.savedAt)),
                            color = VideoDLColors.muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (index != history.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(start = 58.dp, end = 12.dp),
                            color = VideoDLColors.line
                        )
                    }
                }
            }
        }
    }
}

private fun saveIntoDownloads(context: Context, source: File, title: String): String {
    val extension = source.extension.ifBlank { "mp4" }
    val safeTitle = title
        .filterNot { it.isISOControl() }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
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
