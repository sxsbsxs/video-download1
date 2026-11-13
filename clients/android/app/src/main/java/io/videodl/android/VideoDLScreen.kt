package io.videodl.android

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandBlue = Color(0xFF1967D2)
private val Canvas = Color(0xFFF7F8FA)
private val Ink = Color(0xFF202124)
private val Muted = Color(0xFF70757A)
private val Line = Color(0xFFE5E8ED)
private val SoftBlue = Color(0xFFEAF2FF)

data class VideoDLUiState(
    val page: Int = 0,
    val input: String = "",
    val mediaInfo: VideoInfo? = null,
    val selectedFormatKey: String = "",
    val selectedFormatTab: Int = 0,
    val analyzing: Boolean = false,
    val downloading: Boolean = false,
    val statusText: String = "",
    val errorText: String = "",
    val progress: Float? = null,
    val settingsMessage: String = "",
    val history: List<SavedDownload> = emptyList()
)

@Composable
fun VideoDLTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrandBlue,
            onPrimary = Color.White,
            background = Canvas,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF0F2F5),
            onSurfaceVariant = Muted,
            outline = Line,
            error = Color(0xFFB3261E)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDLApp(
    state: VideoDLUiState,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onSelectFormat: (String) -> Unit,
    onDownload: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onOpenDownload: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onClearTemp: () -> Unit
) {
    BackHandler(enabled = state.page != 0) { onPageChange(0) }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(11.dp),
                            color = BrandBlue
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "VideoDL",
                            color = Ink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Ink
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = state.page == 0,
                    onClick = { onPageChange(0) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    label = { Text("下载") }
                )
                NavigationBarItem(
                    selected = state.page == 1,
                    onClick = { onPageChange(1) },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                    label = { Text("文件") }
                )
                NavigationBarItem(
                    selected = state.page == 2,
                    onClick = { onPageChange(2) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.page) {
                1 -> FilesPage(state.history, onOpenDownload)
                2 -> SettingsPage(
                    message = state.settingsMessage,
                    onClearHistory = onClearHistory,
                    onClearTemp = onClearTemp,
                    busy = state.downloading
                )
                else -> DownloadPage(
                    state = state,
                    onInputChange = onInputChange,
                    onPaste = onPaste,
                    onAnalyze = onAnalyze,
                    onSelectTab = onSelectTab,
                    onSelectFormat = onSelectFormat,
                    onDownload = onDownload
                )
            }
        }
    }
}

@Composable
private fun DownloadPage(
    state: VideoDLUiState,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onSelectFormat: (String) -> Unit,
    onDownload: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(26.dp))
        Text(
            "下载视频",
            fontSize = 29.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            color = Ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "粘贴视频链接，选择画质，然后保存到手机。",
            fontSize = 14.sp,
            color = Muted
        )
        Spacer(Modifier.height(22.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.analyzing && !state.downloading,
            singleLine = true,
            shape = RoundedCornerShape(17.dp),
            placeholder = { Text("粘贴视频链接…", color = Muted) },
            leadingIcon = {
                Icon(Icons.Default.Link, contentDescription = null, tint = Muted)
            },
            trailingIcon = {
                TextButton(
                    onClick = onPaste,
                    enabled = !state.analyzing && !state.downloading
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("粘贴")
                }
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = Line,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color(0xFFF2F3F5)
            )
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAnalyze,
            enabled = state.input.isNotBlank() && !state.analyzing && !state.downloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            if (state.analyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("正在解析")
            } else {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("解析链接", fontWeight = FontWeight.SemiBold)
            }
        }

        if (state.errorText.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = Color(0xFFFFF1F0),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFF3D2CE))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFB3261E))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        state.errorText,
                        color = Color(0xFF8C2922),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        state.mediaInfo?.let { info ->
            Spacer(Modifier.height(24.dp))
            VideoPreview(info)
            Spacer(Modifier.height(22.dp))
            FormatPicker(
                state = state,
                onSelectTab = onSelectTab,
                onSelectFormat = onSelectFormat
            )
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "保存到 Downloads / VideoDL",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(11.dp))
            val hasSelection = (info.videoFormats + info.audioFormats)
                .any { it.key == state.selectedFormatKey }
            Button(
                onClick = { onDownload(state.selectedFormatKey) },
                enabled = hasSelection && !state.downloading && !state.analyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.downloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在下载…")
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("下载到手机", fontWeight = FontWeight.SemiBold)
                }
            }
            if (state.downloading || state.progress == 100f) {
                Spacer(Modifier.height(14.dp))
                TransferStatus(state.statusText, state.progress)
            }
            Spacer(Modifier.height(30.dp))
        } ?: run {
            if (state.statusText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(state.statusText, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(26.dp))
            if (!state.downloading) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = SoftBlue
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = BrandBlue)
                            }
                        }
                        Spacer(Modifier.width(13.dp))
                        Column {
                            Text("支持多种视频来源", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "解析由本机 yt-dlp 引擎完成，无需填写第三方 API。",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VideoPreview(info: VideoInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(path = info.thumbnailPath)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                info.title.ifBlank { "未命名视频" },
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (info.uploader.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(info.uploader, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (info.durationSeconds > 0) {
                Spacer(Modifier.height(4.dp))
                Text(durationLabel(info.durationSeconds), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Thumbnail(path: String?) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    Surface(
        modifier = Modifier
            .width(116.dp)
            .height(78.dp),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFFE9EDF3)
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = "视频封面", contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF9AA4B2), modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun FormatPicker(
    state: VideoDLUiState,
    onSelectTab: (Int) -> Unit,
    onSelectFormat: (String) -> Unit
) {
    val info = state.mediaInfo ?: return
    val formats = if (state.selectedFormatTab == 0) info.videoFormats else info.audioFormats
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "格式与清晰度",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${formats.size} 个选项",
                fontSize = 11.sp,
                color = Muted
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedFormatTab == 0,
                onClick = { onSelectTab(0) },
                label = { Text("视频") },
                leadingIcon = if (state.selectedFormatTab == 0) {
                    { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = state.selectedFormatTab == 1,
                onClick = { onSelectTab(1) },
                label = { Text("音频") },
                leadingIcon = if (state.selectedFormatTab == 1) {
                    { Icon(Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
        Spacer(Modifier.height(8.dp))
        if (formats.isEmpty()) {
            Text(
                if (state.selectedFormatTab == 0) "该来源没有提供独立视频格式。" else "该来源没有提供可用音频格式。",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            formats.forEach { format ->
                FormatOption(
                    format = format,
                    selected = format.key == state.selectedFormatKey,
                    onClick = { onSelectFormat(format.key) }
                )
            }
        }
    }
}

@Composable
private fun FormatOption(
    format: VideoFormat,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) SoftBlue else Color.White,
        border = BorderStroke(
            if (selected) 1.4.dp else 1.dp,
            if (selected) BrandBlue.copy(alpha = 0.7f) else Line
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    format.primaryLabel,
                    fontSize = 15.sp,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    format.technicalSummary,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(format.sizeLabel, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                if (selected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "已选择", tint = BrandBlue, modifier = Modifier.size(20.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .background(Color.Transparent, CircleShape)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0xFFB8BEC8))
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferStatus(status: String, progress: Float?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress == 100f) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF188038), modifier = Modifier.size(18.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    status.ifBlank { "正在处理…" },
                    fontSize = 12.sp,
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (progress != null) {
                    Text("${progress.toInt()}%", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = (progress / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = BrandBlue,
                    trackColor = Color(0xFFE7ECF3)
                )
            }
        }
    }
}

@Composable
private fun FilesPage(history: List<SavedDownload>, onOpenDownload: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text("已下载", fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("保存在手机 Downloads / VideoDL 中的文件", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        if (history.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Line)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 42.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF9AA4B2), modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("这里还没有文件", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text("完成下载后，文件会显示在这里。", fontSize = 12.sp, color = Muted)
                }
            }
        } else {
            history.forEachIndexed { index, item ->
                Surface(
                    onClick = { onOpenDownload(index) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Line)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = SoftBlue,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = BrandBlue)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title.ifBlank { item.fileName },
                                color = Ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${item.fileName} · ${dateLabel(item.savedAt)}",
                                color = Muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.FolderOpen, contentDescription = "打开文件", tint = Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsPage(
    message: String,
    onClearHistory: () -> Unit,
    onClearTemp: () -> Unit,
    busy: Boolean
) {
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text("设置", fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("管理保存位置和本机缓存", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Text("存储", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(9.dp))
        SettingRow(
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = BrandBlue) },
            title = "下载目录",
            subtitle = "Downloads / VideoDL"
        )
        Spacer(Modifier.height(20.dp))
        Text("数据管理", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(9.dp))
        SettingRow(
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = BrandBlue) },
            title = "清理临时缓存",
            subtitle = "删除未完成下载留下的临时数据",
            onClick = { showCacheDialog = true },
            enabled = !busy
        )
        Spacer(Modifier.height(9.dp))
        SettingRow(
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFB3261E)) },
            title = "清除下载记录",
            subtitle = "只清除应用内记录，不删除已保存文件",
            onClick = { showHistoryDialog = true }
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(28.dp))
        Text("VideoDL · 本地下载引擎", fontSize = 12.sp, color = Muted)
    }

    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("清理临时缓存？") },
            text = { Text("正在下载时不能清理。已保存到手机的文件不会受到影响。") },
            confirmButton = {
                TextButton(onClick = { showCacheDialog = false; onClearTemp() }, enabled = !busy) { Text("清理") }
            },
            dismissButton = { TextButton(onClick = { showCacheDialog = false }) { Text("取消") } }
        )
    }
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("清除下载记录？") },
            text = { Text("这只会清除 VideoDL 中的记录，不会删除手机里的视频文件。") },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false; onClearHistory() }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
        color = Color.White,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                color = Color(0xFFF0F4FA),
                shape = RoundedCornerShape(11.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

private fun durationLabel(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder)
    else String.format(Locale.getDefault(), "%d:%02d", minutes, remainder)
}

private fun dateLabel(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
