package io.videodl.android

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

data class VideoInfo(
    val sourceUrl: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSeconds: Long,
    val videoFormats: List<VideoFormat>,
    val audioFormats: List<VideoFormat>
)

data class VideoFormat(
    val key: String,
    val videoFormatId: String,
    val audioFormatId: String?,
    val isAudio: Boolean,
    val height: Int,
    val fps: Double,
    val extension: String,
    val videoCodec: String,
    val audioCodec: String,
    val fileSize: Long,
    val bitrate: Double
) {
    val primaryLabel: String
        get() = if (isAudio) {
            if (bitrate > 0) "${bitrate.toInt()} kbps" else "音频"
        } else if (height > 0) {
            "${height}P"
        } else {
            "视频"
        }

    val secondaryLabel: String
        get() = buildList {
            if (!isAudio && fps > 0) add("${fps.toInt()} FPS")
            add(extension.uppercase())
            if (isAudio && bitrate > 0) add("${bitrate.toInt()} kbps")
        }.joinToString(" · ")

    val detailLabel: String
        get() = buildList {
            if (videoCodec.isNotBlank() && videoCodec != "none") add(videoCodec.shortCodec())
            if (audioCodec.isNotBlank() && audioCodec != "none") add(audioCodec.shortCodec())
            if (!isAudio && audioFormatId != null) add("独立音视频流")
            else if (!isAudio) add("含音频")
        }.joinToString(" · ").ifBlank { "源站原始格式" }

    val sizeLabel: String
        get() = if (fileSize > 0) fileSize.readableSize() else "大小未知"
}

private fun String.shortCodec(): String = when {
    startsWith("avc", true) || startsWith("h264", true) -> "H.264"
    startsWith("hev", true) || startsWith("h265", true) || startsWith("hvc", true) -> "H.265"
    startsWith("av01", true) -> "AV1"
    startsWith("vp9", true) -> "VP9"
    startsWith("vp8", true) -> "VP8"
    startsWith("mp4a", true) || startsWith("aac", true) -> "AAC"
    startsWith("opus", true) -> "Opus"
    startsWith("vorbis", true) -> "Vorbis"
    else -> take(12).uppercase()
}

private fun Long.readableSize(): String = when {
    this >= 1024L * 1024L * 1024L -> String.format("%.1f GB", this / (1024.0 * 1024.0 * 1024.0))
    this >= 1024L * 1024L -> String.format("%.1f MB", this / (1024.0 * 1024.0))
    this >= 1024L -> String.format("%.0f KB", this / 1024.0)
    else -> "$this B"
}

interface LocalYtDlpProgress {
    fun onProgressUpdate(progress: Float, etaInSeconds: Long, line: String)
}

object LocalYtDlpEngine {
    private fun python(context: Context): Python {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        return Python.getInstance()
    }

    suspend fun inspect(context: Context, pastedText: String): VideoInfo = withContext(Dispatchers.IO) {
        val module = python(context).getModule("videodl_mobile")
        val raw = module.callAttr("inspect", pastedText).toString()
        val json = JSONObject(raw)
        VideoInfo(
            sourceUrl = json.getString("source_url"),
            title = json.optString("title", "未命名视频"),
            uploader = json.optString("uploader"),
            thumbnail = json.optString("thumbnail").takeIf { it.startsWith("http") },
            durationSeconds = json.optLong("duration"),
            videoFormats = parseFormats(json.optJSONArray("video_formats"), false),
            audioFormats = parseFormats(json.optJSONArray("audio_formats"), true)
        )
    }

    suspend fun download(
        context: Context,
        url: String,
        format: VideoFormat,
        listener: LocalYtDlpProgress
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.cacheDir, "VideoDL/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val module = python(context).getModule("videodl_mobile")
            val raw = module.callAttr(
                "download_plan",
                url,
                format.videoFormatId,
                format.audioFormatId.orEmpty(),
                outputDir.absolutePath,
                listener,
                format.isAudio
            ).toString()
            val result = JSONObject(raw)
            val mainFile = File(result.getString("main_path"))
            if (!mainFile.isFile || mainFile.length() == 0L) {
                throw IOException("yt-dlp 没有生成下载文件。")
            }

            val audioPath = result.optString("audio_path")
            if (audioPath.isBlank()) return@withContext mainFile

            val audioFile = File(audioPath)
            if (!audioFile.isFile || audioFile.length() == 0L) {
                throw IOException("音频流下载不完整。")
            }
            val videoExtension = mainFile.extension.lowercase()
            val audioExtension = audioFile.extension.lowercase()
            val mp4Compatible =
                videoExtension in listOf("mp4", "m4v") &&
                audioExtension in listOf("m4a", "mp4") &&
                (format.videoCodec.startsWith("avc", true) || format.videoCodec.startsWith("h264", true)) &&
                (format.audioCodec.startsWith("mp4a", true) || format.audioCodec.startsWith("aac", true))
            val output = File(outputDir, if (mp4Compatible) "videodl_merged.mp4" else "videodl_merged.mkv")
            val command = buildString {
                append("-y -i ${mainFile.absolutePath} -i ${audioFile.absolutePath}")
                append(" -map 0:v:0 -map 1:a:0 -c copy")
                if (mp4Compatible) append(" -movflags +faststart")
                append(" ${output.absolutePath}")
            }
            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode) || !output.isFile || output.length() == 0L) {
                val logs = runCatching { session.allLogsAsString }.getOrDefault("")
                throw IOException("音视频合并失败：${logs.takeLast(700)}")
            }
            mainFile.delete()
            audioFile.delete()
            output
        } catch (error: Exception) {
            outputDir.deleteRecursively()
            throw error
        }
    }

    private fun parseFormats(array: org.json.JSONArray?, audioOnly: Boolean): List<VideoFormat> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("format_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VideoFormat(
                key = item.optString("key", if (audioOnly) "a:$id" else "v:$id"),
                videoFormatId = id,
                audioFormatId = item.optString("audio_format_id").takeIf { it.isNotBlank() },
                isAudio = audioOnly,
                height = item.optInt("height"),
                fps = item.optDouble("fps"),
                extension = item.optString("ext", "mp4"),
                videoCodec = item.optString("vcodec"),
                audioCodec = item.optString("acodec"),
                fileSize = item.optLong("filesize"),
                bitrate = item.optDouble("bitrate")
            )
        }
    }

    fun cleanup(directory: File?) {
        directory?.deleteRecursively()
    }
}
