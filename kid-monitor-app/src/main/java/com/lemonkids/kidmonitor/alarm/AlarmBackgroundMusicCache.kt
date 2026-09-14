package com.lemonkids.kidmonitor.alarm

import android.content.Context
import com.lemonkids.shared.model.AlarmBackgroundMusicAsset
import com.lemonkids.shared.repository.AlarmBackgroundMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 网络同步期下载，响铃冷启动只通过 [cachedFile] 读取已验证的私有文件。 */
@Singleton
class AlarmBackgroundMusicCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: AlarmBackgroundMusicRepository
) {
    data class CachedMusic(val fileName: String)

    suspend fun cache(music: AlarmBackgroundMusicAsset): Result<CachedMusic> = withContext(Dispatchers.IO) {
        runCatching {
        require(music.isUsable()) { "曲目元数据不符合客户端限制" }
        val target = File(directory(), fileName(music))
        if (target.isFile && target.length() == music.sizeBytes && sha256(target) == music.sha256.lowercase()) {
            return@runCatching CachedMusic(target.name)
        }
        val signedUrl = musicRepository.createDownloadUrl(music).getOrThrow()
        val temporary = File(directory(), ".${target.name}.${System.nanoTime()}.part")
        try {
            val connection = (URL(signedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS; readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = false
            }
            connection.connect()
            check(connection.responseCode in 200..299) { "下载响应 ${connection.responseCode}" }
            val actualMime = connection.contentType?.substringBefore(';')?.lowercase()
            check(actualMime == music.mimeType) { "MIME 不匹配：$actualMime" }
            check(connection.contentLengthLong in -1..music.sizeBytes) { "文件大小超过声明值" }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(bytes)
                        if (read < 0) break
                        total += read
                        check(total <= music.sizeBytes) { "文件大小超过声明值" }
                        output.write(bytes, 0, read)
                    }
                }
            }
            check(temporary.length() == music.sizeBytes) { "文件大小不匹配" }
            check(sha256(temporary) == music.sha256.lowercase()) { "SHA-256 不匹配" }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            CachedMusic(target.name)
        } finally {
            temporary.delete()
        }
        }
    }

    fun cachedFile(fileName: String): File? = fileName.takeIf { it.matches(FILE_NAME) }
        ?.let { File(directory(), it) }?.takeIf { it.isFile }

    /** 只清理未被任何本地闹钟引用、且至少保留一天的旧文件。 */
    fun cleanUnreferenced(referenced: Set<String>) {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        directory().listFiles()?.filter { it.isFile && it.name !in referenced && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun directory(): File = File(context.filesDir, DIRECTORY).also { it.mkdirs() }
    private fun fileName(music: AlarmBackgroundMusicAsset): String {
        val key = "${music.id}:${music.version}:${music.sha256}".toByteArray()
        return "${MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it) }}.${if (music.mimeType == "audio/ogg") "ogg" else "mp3"}"
    }
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input ->
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val read = input.read(bytes); if (read < 0) break; update(bytes, 0, read) }
        }
        digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DIRECTORY = "alarm-background-music"
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val RETENTION_MILLIS = 24 * 60 * 60 * 1000L
        private val FILE_NAME = Regex("[a-f0-9]{64}\\.(ogg|mp3)")
    }
}
