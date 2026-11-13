package io.videodl.android

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SnapYellow = Color(0xFFFFD52A)
private val SnapYellowSoft = Color(0xFFFFF5C8)
private val PageBackground = Color(0xFFF7F7F5)
private val PrimaryText = Color(0xFF191919)
private val SecondaryText = Color(0xFF777777)
private val Hairline = Color(0xFFEAE9E5)
private val CardWhite = Color.White
private val SuccessGreen = Color(0xFF16834A)

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
            primary = SnapYellow,
            onPrimary = PrimaryText,
            secondary = Color(0xFF333333),
            onSecondary = Color.White,
            background = PageBackground,
            onBackground = PrimaryText,
            surface = CardWhite,
            onSurface = PrimaryText,
            surfaceVariant = Color(0xFFF1F0EC),
            onSurfaceVariant = SecondaryText,
            outline = Hairline,
            error = Color(0xFFB3261E)
        ),
        content = content
    )
}

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
        containerColor = PageBackground,
        bottomBar = {
            NavigationBar(
                containerColor = CardWhite,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = state.page == 0,
                    onClick = { onPageChange(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("首页") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryText,
                        selectedTextColor = PrimaryText,
                        indicatorColor = SnapYellowSoft,
                        unselectedIconColor = SecondaryText,
                        unselectedTextColor = SecondaryText
                    )
                )
                NavigationBarItem(
                    selected = state.page == 1,
                    onClick = { onPageChange(1) },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                    label = { Text("我的文件") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryText,
                        selectedTextColor = PrimaryText,
                        indicatorColor = SnapYellowSoft,
                        unselectedIconColor = SecondaryText,
                        unselectedTextColor = SecondaryText
                    )
                )
                NavigationBarItem(
                    selected = state.page == 2,
                    onClick = { onPageChange(2) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("我的") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryText,
                        selectedTextColor = PrimaryText,
                        indicatorColor = SnapYellowSoft,
                        unselectedIconColor = SecondaryText,
                        unselectedTextColor = SecondaryText
                    )
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
                1 -> FilesPage(
                    history = state.history,
                    onOpenDownload = onOpenDownload
                )
                2 -> MePage(
                    message = state.settingsMessage,
                    onClearHistory = onClearHistory,
                    onClearTemp = onClearTemp,
                    busy = state.downloading
                )
                else -> HomePage(
                    state = state,
                    onInputChange = onInputChange,
                    onPaste = onPaste,
                    onAnalyze = onAnalyze,
                    onSelectTab = onSelectTab,
                    onSelectFormat = onSelectFormat,
                    onDownload = onDownload,
                    onOpenFiles = { onPageChange(1) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePage(
    state: VideoDLUiState,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onSelectFormat: (String) -> Unit,
    onDownload: (String) -> Unit,
    onOpenFiles: () -> Unit
) {
    var showFormatSheet by remember { mutableStateOf(false) }
    var homeCategory by remember { mutableStateOf(0) }
    val info = state.mediaInfo
    val allFormats = info?.videoFormats.orEmpty() + info?.audioFormats.orEmpty()
    val selectedFormat = allFormats.firstOrNull { it.key == state.selectedFormatKey }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // SnapTube-inspired yellow masthead with a persistent URL search field.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SnapYellow)
                .padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 19.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = PrimaryText,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "VideoDL",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryText,
                        letterSpacing = (-0.7).sp
                    )
                    Text(
                        "视频与音乐下载",
                        fontSize = 11.sp,
                        color = Color(0xFF4C3E12)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0x33FFFFFF),
                    modifier = Modifier.size(39.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "我的文件",
                            tint = PrimaryText,
                            modifier = Modifier
                                .size(21.dp)
                                .clickable(onClick = onOpenFiles)
                        )
                    }
                }
            }

            Spacer(Modifier.height(17.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                color = CardWhite,
                shadowElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.analyzing && !state.downloading,
                    singleLine = true,
                    placeholder = {
                        Text(
                            "搜索或粘贴视频链接",
                            color = Color(0xFF929292),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = SecondaryText
                        )
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = onPaste,
                            enabled = !state.analyzing && !state.downloading
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = PrimaryText
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("粘贴", color = PrimaryText, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    shape = RoundedCornerShape(17.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite,
                        disabledContainerColor = CardWhite
                    )
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onAnalyze,
                enabled = state.input.isNotBlank() && !state.analyzing && !state.downloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryText,
                    contentColor = SnapYellow,
                    disabledContainerColor = Color(0xFF786A38),
                    disabledContentColor = Color(0xFFFFEAA1)
                )
            ) {
                if (state.analyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = SnapYellow,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(9.dp))
                    Text("正在解析…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("解析链接", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Functional category strip: it changes the actual video/audio format category.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeCategory("全部", homeCategory == 0) {
                homeCategory = 0
                onSelectTab(0)
            }
            HomeCategory("视频", homeCategory == 1) {
                homeCategory = 1
                onSelectTab(0)
            }
            HomeCategory("音乐", homeCategory == 2) {
                homeCategory = 2
                onSelectTab(1)
            }
            HomeCategory("下载记录", false, onOpenFiles)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "快速入口",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "本机解析引擎",
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                QuickEntry(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    title = "粘贴链接",
                    subtitle = "剪贴板",
                    onClick = onPaste
                )
                QuickEntry(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                    title = "视频下载",
                    subtitle = "选择画质",
                    onClick = { homeCategory = 1; onSelectTab(0) }
                )
                QuickEntry(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.AudioFile, contentDescription = null) },
                    title = "音乐提取",
                    subtitle = "仅下载音频",
                    onClick = { homeCategory = 2; onSelectTab(1) }
                )
            }

            if (state.errorText.isNotBlank()) {
                Spacer(Modifier.height(15.dp))
                MessageCard(
                    text = state.errorText,
                    isError = true
                )
            }

            if (info != null) {
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "解析结果",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = Color(0xFFE7F4EA),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("已识别", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                VideoResultCard(info = info)

                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, Hairline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("下载格式", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "根据来源实际提供的格式选择",
                                    fontSize = 11.sp,
                                    color = SecondaryText
                                )
                            }
                            TextButton(onClick = { showFormatSheet = true }) {
                                Text("全部格式", color = Color(0xFF9A7400), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FormatTypeChip(
                                modifier = Modifier.weight(1f),
                                label = "视频",
                                count = info.videoFormats.size,
                                selected = state.selectedFormatTab == 0,
                                onClick = { onSelectTab(0); homeCategory = 1 }
                            )
                            FormatTypeChip(
                                modifier = Modifier.weight(1f),
                                label = "音乐",
                                count = info.audioFormats.size,
                                selected = state.selectedFormatTab == 1,
                                onClick = { onSelectTab(1); homeCategory = 2 }
                            )
                        }
                        Spacer(Modifier.height(11.dp))
                        if (selectedFormat != null) {
                            SelectedFormatSummary(selectedFormat)
                        } else {
                            Text(
                                "请选择一种可用格式",
                                color = SecondaryText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onDownload(state.selectedFormatKey) },
                            enabled = selectedFormat != null && !state.downloading && !state.analyzing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SnapYellow,
                                contentColor = PrimaryText
                            )
                        ) {
                            if (state.downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = PrimaryText,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在下载…", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("下载到手机", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (state.downloading || state.progress == 100f) {
                    Spacer(Modifier.height(14.dp))
                    TransferStatus(state.statusText, state.progress)
                }
            } else {
                Spacer(Modifier.height(20.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = CardWhite,
                    border = BorderStroke(1.dp, Hairline)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 25.dp, horizontal = 19.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(62.dp),
                            shape = CircleShape,
                            color = SnapYellowSoft
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF806400),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "粘贴链接，开始下载",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "支持视频与音频格式选择，解析和下载都在本机完成。",
                            color = SecondaryText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("最近下载", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenFiles) {
                        Text("查看全部", color = Color(0xFF9A7400))
                    }
                }
                state.history.take(3).forEach { item ->
                    RecentFileRow(item)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(25.dp))
        }
    }

    if (showFormatSheet && info != null) {
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            containerColor = CardWhite,
            dragHandle = {
                Surface(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .width(38.dp)
                        .height(4.dp),
                    color = Color(0xFFD9D9D9),
                    shape = CircleShape
                ) {}
            }
        ) {
            FormatSheetContent(
                state = state,
                onSelectTab = onSelectTab,
                onSelectFormat = {
                    onSelectFormat(it)
                    showFormatSheet = false
                }
            )
        }
    }
}

@Composable
private fun HomeCategory(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) PrimaryText else SecondaryText,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 21.dp else 7.dp)
                .height(3.dp)
                .background(
                    if (selected) SnapYellow else Color.Transparent,
                    CircleShape
                )
        )
    }
}

@Composable
private fun QuickEntry(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(94.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                modifier = Modifier.size(31.dp),
                shape = RoundedCornerShape(10.dp),
                color = SnapYellowSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides PrimaryText
                    ) {
                        androidx.compose.material3.ProvideTextStyle(
                            MaterialTheme.typography.labelLarge
                        ) {
                            icon()
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = SecondaryText, maxLines = 1)
        }
    }
}

@Composable
private fun VideoResultCard(info: VideoInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(path = info.thumbnailPath, modifier = Modifier.width(116.dp).height(82.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.title.ifBlank { "未命名视频" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.uploader.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        info.uploader,
                        color = SecondaryText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (info.durationSeconds > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        durationLabel(info.durationSeconds),
                        color = SecondaryText,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(path: String?, modifier: Modifier = Modifier.size(112.dp, 78.dp)) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF0F0ED)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "视频封面",
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFFAAA9A2),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun FormatTypeChip(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SnapYellowSoft else Color(0xFFF7F7F5),
        border = BorderStroke(
            1.dp,
            if (selected) SnapYellow else Hairline
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 11.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                if (label == "视频") Icons.Default.Movie else Icons.Default.AudioFile,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.width(5.dp))
            Text(count.toString(), color = SecondaryText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SelectedFormatSummary(format: VideoFormat) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFFF8F8F6),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(35.dp),
                shape = RoundedCornerShape(10.dp),
                color = SnapYellowSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (format.isAudio) Icons.Default.AudioFile else Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    format.primaryLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    format.technicalSummary,
                    fontSize = 10.sp,
                    color = SecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(format.sizeLabel, fontSize = 11.sp, color = SecondaryText)
                Spacer(Modifier.height(3.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = "已选择", tint = Color(0xFF9B7800), modifier = Modifier.size(17.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSheetContent(
    state: VideoDLUiState,
    onSelectTab: (Int) -> Unit,
    onSelectFormat: (String) -> Unit
) {
    val info = state.mediaInfo ?: return
    val formats = if (state.selectedFormatTab == 0) info.videoFormats else info.audioFormats

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(horizontal = 18.dp)
    ) {
        Text(
            "选择下载格式",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "只显示来源实际返回的格式，不会虚构不存在的画质。",
            fontSize = 11.sp,
            color = SecondaryText
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedFormatTab == 0,
                onClick = { onSelectTab(0) },
                label = { Text("视频 · ${info.videoFormats.size}") },
                leadingIcon = if (state.selectedFormatTab == 0) {
                    { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = state.selectedFormatTab == 1,
                onClick = { onSelectTab(1) },
                label = { Text("音乐 · ${info.audioFormats.size}") },
                leadingIcon = if (state.selectedFormatTab == 1) {
                    { Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
        Spacer(Modifier.height(6.dp))
        if (formats.isEmpty()) {
            Text(
                if (state.selectedFormatTab == 0) "该来源没有提供可用的视频格式。" else "该来源没有提供可用的音频格式。",
                color = SecondaryText,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 28.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 22.dp)
            ) {
                formats.forEach { format ->
                    FormatOption(
                        format = format,
                        selected = format.key == state.selectedFormatKey,
                        onClick = { onSelectFormat(format.key) }
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun FormatOption(
    format: VideoFormat,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) SnapYellowSoft else CardWhite,
        border = BorderStroke(
            if (selected) 1.4.dp else 1.dp,
            if (selected) Color(0xFFE2B900) else Hairline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (selected) SnapYellow else Color(0xFFF0F0ED)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (format.isAudio) Icons.Default.AudioFile else Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(format.primaryLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    format.technicalSummary,
                    color = SecondaryText,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(format.sizeLabel, color = SecondaryText, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                if (selected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "已选择", tint = Color(0xFF987400), modifier = Modifier.size(18.dp))
                } else {
                    Surface(
                        modifier = Modifier.size(17.dp),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, Color(0xFFB8B8B2))
                    ) {}
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
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Hairline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress == 100f) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    status.ifBlank { "正在处理…" },
                    fontSize = 12.sp,
                    color = PrimaryText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (progress != null) {
                    Text(
                        "${progress.toInt()}%",
                        fontSize = 12.sp,
                        color = SecondaryText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(11.dp))
                LinearProgressIndicator(
                    progress = (progress / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = SnapYellow,
                    trackColor = Color(0xFFE9E7DF)
                )
            }
        }
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) Color(0xFFFFF0EF) else Color(0xFFE9F5EB),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(
            1.dp,
            if (isError) Color(0xFFF3D0CE) else Color(0xFFCCE8D1)
        )
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = if (isError) Color(0xFFB3261E) else SuccessGreen,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                color = if (isError) Color(0xFF8C2922) else Color(0xFF22633A),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun RecentFileRow(item: SavedDownload) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(37.dp),
                shape = RoundedCornerShape(10.dp),
                color = SnapYellowSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isAudioFile(item.fileName)) Icons.Default.AudioFile else Icons.Default.Movie,
                        contentDescription = null,
                        tint = PrimaryText
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { item.fileName },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.fileName} · ${dateLabel(item.savedAt)}",
                    fontSize = 10.sp,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FilesPage(
    history: List<SavedDownload>,
    onOpenDownload: (Int) -> Unit
) {
    var filter by remember { mutableStateOf(0) }
    val filtered = history.withIndex().filter { (_, item) ->
        when (filter) {
            1 -> !isAudioFile(item.fileName)
            2 -> isAudioFile(item.fileName)
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp)
    ) {
        Spacer(Modifier.height(25.dp))
        Text("我的文件", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
        Spacer(Modifier.height(4.dp))
        Text("已保存到手机的内容", color = SecondaryText, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("全部 · ${history.size}") })
            FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("视频") })
            FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("音乐") })
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 43.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(61.dp),
                        shape = CircleShape,
                        color = SnapYellowSoft
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(27.dp))
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Text("这里还没有文件", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("完成下载后，文件会显示在这里。", fontSize = 12.sp, color = SecondaryText)
                }
            }
        } else {
            filtered.forEach { indexed ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clickable { onOpenDownload(indexed.index) },
                    shape = RoundedCornerShape(15.dp),
                    color = CardWhite,
                    border = BorderStroke(1.dp, Hairline)
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(43.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = SnapYellowSoft
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isAudioFile(indexed.value.fileName)) Icons.Default.AudioFile else Icons.Default.Movie,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                indexed.value.title.ifBlank { indexed.value.fileName },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${indexed.value.fileName} · ${dateLabel(indexed.value.savedAt)}",
                                color = SecondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.Default.PlayArrow, contentDescription = "打开", tint = SecondaryText)
                    }
                }
            }
        }
        Spacer(Modifier.height(25.dp))
    }
}

@Composable
private fun MePage(
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
            .padding(horizontal = 17.dp)
    ) {
        Spacer(Modifier.height(25.dp))
        Text("我的", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
        Spacer(Modifier.height(17.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SnapYellow
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(53.dp),
                    shape = CircleShape,
                    color = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text("VideoDL", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text("本机下载 · 无需配置服务端", fontSize = 11.sp, color = Color(0xFF514312))
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("存储与管理", color = SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        MeSettingRow(
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            title = "下载保存位置",
            subtitle = "Downloads / VideoDL"
        )
        Spacer(Modifier.height(9.dp))
        MeSettingRow(
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
            title = "清理临时缓存",
            subtitle = "移除未完成下载留下的临时文件",
            enabled = !busy,
            onClick = { showCacheDialog = true }
        )
        Spacer(Modifier.height(9.dp))
        MeSettingRow(
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = "清除下载记录",
            subtitle = "不会删除已保存到手机的文件",
            onClick = { showHistoryDialog = true }
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(13.dp))
            Text(message, fontSize = 12.sp, color = SecondaryText)
        }
        Spacer(Modifier.height(24.dp))
        Text("VideoDL · Kotlin + Compose + Material 3", color = SecondaryText, fontSize = 11.sp)
        Spacer(Modifier.height(25.dp))
    }

    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("清理临时缓存？") },
            text = { Text("正在下载时不能清理。已保存的文件不会受到影响。") },
            confirmButton = {
                TextButton(
                    onClick = { showCacheDialog = false; onClearTemp() },
                    enabled = !busy
                ) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showCacheDialog = false }) { Text("取消") }
            }
        )
    }
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("清除下载记录？") },
            text = { Text("这只会清除 VideoDL 中的记录，不会删除手机里的视频文件。") },
            confirmButton = {
                TextButton(
                    onClick = { showHistoryDialog = false; onClearHistory() }
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MeSettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(15.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(37.dp),
                shape = RoundedCornerShape(11.dp),
                color = SnapYellowSoft
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 11.sp, color = SecondaryText)
            }
        }
    }
}

private fun isAudioFile(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in
        setOf("mp3", "m4a", "aac", "opus", "ogg", "wav", "flac")

private fun durationLabel(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, remainder)
    }
}

private fun dateLabel(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
