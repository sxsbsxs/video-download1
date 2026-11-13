package io.videodl.android

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

data class VideoInfo(
    val sourceUrl: String,
    val title: String,
    val uploader: String,
    val thumbnailPath: String?,
    val durationSeconds: Long,
    val videoFormats: List<VideoFormat>,
    val audioFormats: List<VideoFormat>
)

data class VideoFormat(
    val key: String,
    val videoFormatId: String,
    val audioFormatId: String?,
    val isAudio: Boolean,
    val width: Int,
    val height: Int,
    val fps: Double,
    val dynamicRange: String,
    val extension: String,
    val videoCodec: String,
    val audioCodec: String,
    val fileSize: Long,
    val fileSizeIsApprox: Boolean,
    val bitrate: Double
) {
    val primaryLabel: String
        get() = if (isAudio) {
            if (bitrate > 0) "${bitrate.toInt()} kbps" else "音频"
        } else if (width > 0 && height > 0) {
            "${width} × ${height}"
        } else if (height > 0) {
            "${height}P"
        } else {
            "视频"
        }

    val codecSummary: String
        get() = buildList {
            if (videoCodec.isNotBlank() && videoCodec != "none") add(videoCodec.shortCodec())
            if (audioCodec.isNotBlank() && audioCodec != "none") add(audioCodec.shortCodec())
        }.distinct().joinToString(" + ")

    val technicalSummary: String
        get() = buildList {
            if (!isAudio && fps > 0) add("${fpsLabelForFormat(fps)} FPS")
            add(extension.uppercase())
            if (dynamicRange.isNotBlank() && !dynamicRange.equals("SDR", true)) add(dynamicRange)
            if (codecSummary.isNotBlank()) add(codecSummary)
            add("ID $videoFormatId")
        }.joinToString(" · ")

    val sizeLabel: String
        get() = if (fileSize > 0) {
            (if (fileSizeIsApprox) "约 " else "") + fileSize.readableSize()
        } else {
            "大小未知"
        }
}

private fun fpsLabelForFormat(value: Double): String =
    String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun String.shortCodec(): String = when {
    startsWith("avc", true) || startsWith("h264", true) -> "H.264"
    startsWith("hev", true) || startsWith("h265", true) || startsWith("hvc", true) -> "H.265"
    startsWith("av01", true) -> "AV1"
    startsWith("vp9", true) -> "VP9"
    startsWith("vp8", true) -> "VP8"
    startsWith("mp4a", true) || startsWith("aac", true) -> "AAC"
    startsWith("opus", true) -> "Opus"
    startsWith("vorbis", true) -> "Vorbis"
    else -> take(16).uppercase()
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
    private const val MAX_THUMBNAIL_BYTES = 12 * 1024 * 1024

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
        val sourceUrl = json.getString("source_url")
        val thumbnailUrls = buildList {
            val array = json.optJSONArray("thumbnail_urls")
            if (array != null) {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(::add)
                }
            }
            json.optString("thumbnail").takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(::add)
        }.distinct()
        val cachedThumbnail = cacheThumbnail(context, thumbnailUrls, sourceUrl)
        VideoInfo(
            sourceUrl = sourceUrl,
            title = json.optString("title", "未命名视频"),
            uploader = json.optString("uploader"),
            thumbnailPath = cachedThumbnail?.absolutePath,
            durationSeconds = json.optLong("duration"),
            videoFormats = parseFormats(json.optJSONArray("video_formats"), false),
            audioFormats = parseFormats(json.optJSONArray("audio_formats"), true)
        )
    }

    private fun cacheThumbnail(context: Context, urls: List<String>, sourceUrl: String): File? {
        if (urls.isEmpty()) return null
        val directory = File(context.cacheDir, "covers").apply { mkdirs() }
        val sourceUri = runCatching { Uri.parse(sourceUrl) }.getOrNull()
        val referer = if (sourceUri?.host != null) "${sourceUri.scheme ?: "https"}://${sourceUri.host}/" else sourceUrl

        for (thumbnailUrl in urls.take(8)) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(thumbnailUrl.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val target = File(directory, "$digest.jpg")
            if (target.isFile && target.length() > 0L && isImage(target)) return target

            for (useReferer in listOf(true, false)) {
                val connection = runCatching {
                    (URL(thumbnailUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 12_000
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
                        setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        if (useReferer) setRequestProperty("Referer", referer)
                    }
                }.getOrNull() ?: continue

                try {
                    connection.connect()
                    val contentType = connection.contentType.orEmpty().lowercase()
                    if (contentType.startsWith("text/") || contentType.contains("json")) continue
                    val length = connection.contentLengthLong
                    if (length > MAX_THUMBNAIL_BYTES) continue

                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_THUMBNAIL_BYTES) throw IOException("封面图片超过大小限制")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (!writeThumbnailJpeg(bytes, File(directory, "$digest.tmp"))) continue
                    val temporary = File(directory, "$digest.tmp")
                    if (target.exists()) target.delete()
                    if (!temporary.renameTo(target)) {
                        temporary.delete()
                        continue
                    }
                    return target
                } catch (_: Exception) {
                    // Try the same image URL without a Referer, then try the next thumbnail.
                } finally {
                    connection.disconnect()
                }
            }
        }
        return null
    }


    private fun writeThumbnailJpeg(bytes: ByteArray, target: File): Boolean {
        if (bytes.isEmpty()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        // Covers are rendered in a 16:9 preview; downsample very large originals
        // before decoding so a high-resolution source thumbnail cannot exhaust RAM.
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1200) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return false
        return try {
            FileOutputStream(target).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
            }
        } catch (_: Exception) {
            target.delete()
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun isImage(file: File): Boolean =
        runCatching { isImage(file.readBytes()) }.getOrDefault(false)

    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth > 0 && options.outHeight > 0
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
            if (audioPath.isBlank()) {
                listener.onProgressUpdate(98f, 0, "下载完成，正在准备保存到手机…")
                return@withContext mainFile
            }

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
            listener.onProgressUpdate(96f, 0, "音视频数据已下载，正在合并…")
            val commandArguments = buildList {
                add("-y")
                add("-i")
                add(mainFile.absolutePath)
                add("-i")
                add(audioFile.absolutePath)
                add("-map")
                add("0:v:0")
                add("-map")
                add("1:a:0")
                add("-c")
                add("copy")
                if (mp4Compatible) {
                    add("-movflags")
                    add("+faststart")
                }
                add(output.absolutePath)
            }.toTypedArray()
            val session = FFmpegKit.executeWithArguments(commandArguments)
            if (!ReturnCode.isSuccess(session.returnCode) || !output.isFile || output.length() == 0L) {
                val logs = runCatching { session.allLogsAsString }.getOrDefault("")
                throw IOException("音视频合并失败：${logs.takeLast(700)}")
            }
            mainFile.delete()
            audioFile.delete()
            listener.onProgressUpdate(98f, 0, "音视频合并完成，正在准备保存到手机…")
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
                width = item.optInt("width"),
                height = item.optInt("height"),
                fps = item.optDouble("fps"),
                dynamicRange = item.optString("dynamic_range"),
                extension = item.optString("ext", "mp4"),
                videoCodec = item.optString("vcodec"),
                audioCodec = item.optString("acodec"),
                fileSize = item.optLong("filesize"),
                fileSizeIsApprox = item.optBoolean("filesize_is_approx"),
                bitrate = item.optDouble("bitrate")
            )
        }
    }

    fun cleanup(directory: File?) {
        directory?.deleteRecursively()
    }
}
