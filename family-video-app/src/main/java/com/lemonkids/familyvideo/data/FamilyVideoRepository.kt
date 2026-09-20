package com.lemonkids.familyvideo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Serializable data class VideoCategory(val id: String = "", @SerialName("family_id") val familyId: String = "", val name: String = "", @SerialName("sort_order") val sortOrder: Int = 0, @SerialName("is_builtin") val isBuiltin: Boolean = false)
@Serializable data class VideoCollection(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("drive_folder_id") val driveFolderId: String = "",
    @SerialName("drive_folder_path") val driveFolderPath: String? = null,
    val name: String = "",
    @SerialName("media_type") val mediaType: String = "series",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("sync_status") val syncStatus: String = "ready",
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
)
@Serializable data class VideoMedia(val id: String = "", @SerialName("collection_id") val collectionId: String = "", @SerialName("drive_file_id") val driveFileId: String = "", val name: String = "", val path: String? = null, @SerialName("duration_seconds") val durationSeconds: Long? = null, @SerialName("sort_order") val sortOrder: Int = 0, @Transient val playbackUrl: String? = null)
@Serializable data class VideoPlaybackRecord(@SerialName("media_id") val mediaId: String = "", @SerialName("progress_seconds") val progressSeconds: Long = 0, @SerialName("duration_seconds") val durationSeconds: Long = 0, @SerialName("is_completed") val isCompleted: Boolean = false)

data class FamilyVideoLibrary(val categories: List<VideoCategory> = emptyList(), val collections: List<VideoCollection>, val media: List<VideoMedia>, val playback: List<VideoPlaybackRecord>) {
    fun mediaFor(collectionId: String) = media.filter { it.collectionId == collectionId }.sortedBy { it.sortOrder }
    fun progressFor(mediaId: String) = playback.firstOrNull { it.mediaId == mediaId }
    fun childrenFor(collectionId: String) = collections.filter { it.parentId == collectionId }.sortedBy { it.name }
    fun topLevel() = collections.filter { it.parentId == null }.sortedBy { it.name }
}

/** 云盘供应商边界：OAuth 令牌必须在受保护服务端持有，客户端只接收短期结果。 */
data class DriveConnection(
    val status: String = "disconnected",
    val accountHint: String? = null,
    val rootFolderId: String? = null,
    val rootPath: String? = null,
    val lastSyncedAt: String? = null
)

/**
 * 所有调用都只携带当前 Supabase 会话。123 云盘应用密钥与短期 access token 留在 Edge Function，
 * 不进入 APK、数据库或 Android 日志。
 */
interface CloudDriveProvider {
    suspend fun connection(): Result<DriveConnection>
    suspend fun connect(): Result<DriveConnection>
    suspend fun browse(parentFolderId: String = "0", breadcrumb: String = "123 云盘"): Result<List<CloudFolder>>
    suspend fun syncCollection(collectionId: String): Result<SyncSummary>
    suspend fun freshPlaybackUrl(fileId: String): Result<String>
}
data class CloudFolder(val id: String, val name: String, val breadcrumb: String, val isFolder: Boolean = true)
data class SyncSummary(val added: Int, val updated: Int, val unavailable: Int, val media: Int)

interface FamilyVideoRepository {
    suspend fun loadLibrary(familyId: String): Result<FamilyVideoLibrary>
    suspend fun saveCollection(collection: VideoCollection): Result<VideoCollection>
    suspend fun deleteCollection(collectionId: String): Result<Unit>
    suspend fun uploadCover(familyId: String, bytes: ByteArray): Result<String>
    suspend fun updatePlayback(record: VideoPlaybackRecord): Result<Unit>
}

@Singleton
class SupabaseFamilyVideoRepository @Inject constructor(private val supabase: SupabaseClient) : FamilyVideoRepository {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private companion object { const val VIDEO_COVERS_BUCKET = "video-covers" }
    override suspend fun loadLibrary(familyId: String): Result<FamilyVideoLibrary> = runCatching {
        // 分类是旧版根目录同步的遗留数据。继续读取是为了兼容已升级家庭，界面不再依赖它。
        val categories = postgrest.from("video_categories").select { filter { eq("family_id", familyId) }; order("sort_order", Order.ASCENDING) }.decodeList<VideoCategory>()
        val collections = postgrest.from("video_collections").select { filter { eq("family_id", familyId) }; order("name", Order.ASCENDING) }.decodeList<VideoCollection>()
        val ids = collections.map { it.id }.toSet()
        // RLS 已限制为当前家长所在家庭；这里再按本次目录集合筛选，避免依赖路径型筛选参数。
        val media = if (ids.isEmpty()) emptyList() else postgrest.from("video_media").select { order("sort_order", Order.ASCENDING) }.decodeList<VideoMedia>().filter { it.collectionId in ids }
        val mediaIds = media.map { it.id }.toSet()
        val playback = if (mediaIds.isEmpty()) emptyList() else postgrest.from("video_playback_records").select { }.decodeList<VideoPlaybackRecord>().filter { it.mediaId in mediaIds }
        FamilyVideoLibrary(categories, collections, media, playback)
    }
    override suspend fun saveCollection(collection: VideoCollection): Result<VideoCollection> = runCatching {
        if (collection.id.isBlank()) {
            postgrest.from("video_collections").insert(
                mapOf(
                    "family_id" to collection.familyId,
                    "parent_id" to collection.parentId,
                    "drive_folder_id" to collection.driveFolderId,
                    "drive_folder_path" to collection.driveFolderPath,
                    "name" to collection.name,
                    "media_type" to collection.mediaType,
                    "cover_url" to collection.coverUrl,
                    "sync_status" to "ready",
                )
            ) { select() }.decodeSingle<VideoCollection>()
        } else {
            postgrest.from("video_collections").update(
                mapOf(
                    "parent_id" to collection.parentId,
                    "drive_folder_id" to collection.driveFolderId,
                    "drive_folder_path" to collection.driveFolderPath,
                    "name" to collection.name,
                    "media_type" to collection.mediaType,
                    "cover_url" to collection.coverUrl,
                )
            ) { filter { eq("id", collection.id) }; select() }.decodeSingle<VideoCollection>()
        }
    }
    override suspend fun deleteCollection(collectionId: String): Result<Unit> = runCatching {
        postgrest.from("video_collections").delete { filter { eq("id", collectionId) } }
    }
    override suspend fun uploadCover(familyId: String, bytes: ByteArray): Result<String> = runCatching {
        val path = "$familyId/${UUID.randomUUID()}.jpg"
        val storage = supabase.pluginManager.getPlugin(Storage)
        storage.from(VIDEO_COVERS_BUCKET).upload(path = path, data = bytes, upsert = false)
        storage.from(VIDEO_COVERS_BUCKET).publicUrl(path)
    }
    override suspend fun updatePlayback(record: VideoPlaybackRecord): Result<Unit> = runCatching {
        postgrest.from("video_playback_records").upsert(record)
    }
}

@Singleton
class SupabaseEdgeCloudDriveProvider @Inject constructor(private val supabase: SupabaseClient) : CloudDriveProvider {
    private val auth get() = supabase.pluginManager.getPlugin(Auth)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun connection(): Result<DriveConnection> = call("connection_status") { result ->
        DriveConnection(
            status = result.string("authorization_status") ?: "disconnected",
            accountHint = result.string("drive_account_hint"),
            rootFolderId = result.string("sync_root_folder_id"),
            rootPath = result.string("sync_root_path"),
            lastSyncedAt = result.string("last_synced_at")
        )
    }

    override suspend fun connect(): Result<DriveConnection> = call("connect") { result ->
        DriveConnection(
            status = result.string("authorization_status") ?: "connected",
            accountHint = result.string("drive_account_hint"),
            rootFolderId = result.string("sync_root_folder_id"),
            rootPath = result.string("sync_root_path"),
            lastSyncedAt = result.string("last_synced_at")
        )
    }

    override suspend fun browse(parentFolderId: String, breadcrumb: String): Result<List<CloudFolder>> = call(
        "browse", mapOf("parentFolderId" to parentFolderId, "breadcrumb" to breadcrumb)
    ) { result ->
        result["folders"]?.jsonArray.orEmpty().map { node ->
            val item = node.jsonObject
            CloudFolder(
                id = item.string("id") ?: error("云盘目录缺少 ID"),
                name = item.string("name") ?: "未命名目录",
                breadcrumb = item.string("breadcrumb") ?: breadcrumb,
                isFolder = item["isFolder"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            )
        }
    }

    override suspend fun syncCollection(collectionId: String): Result<SyncSummary> = call("sync_collection", mapOf("collectionId" to collectionId)) { result ->
        SyncSummary(result.int("added_count"), result.int("updated_count"), result.int("unavailable_count"), result.int("media_count"))
    }

    override suspend fun freshPlaybackUrl(fileId: String): Result<String> = call("playback_url", mapOf("fileId" to fileId)) { result ->
        result.string("url") ?: error("云盘没有返回播放地址")
    }

    private suspend fun <T> call(action: String, values: Map<String, String> = emptyMap(), transform: (JsonObject) -> T): Result<T> = try {
        withTimeout(timeoutFor(action)) {
            try {
        val accessToken = auth.currentSessionOrNull()?.accessToken ?: error("登录已过期，请重新登录")
        val body = buildJsonObject {
            put("action", JsonPrimitive(action))
            values.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }
        val responseText = HttpClient().use { client ->
            client.post("https://ebiikfxehhcrtrkioxqa.supabase.co/functions/v1/family-video-drive") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                    append("apikey", SUPABASE_ANON_KEY)
                }
                setBody(body.toString())
            }.bodyAsText()
        }
        if (responseText.isBlank()) error("同步服务未返回响应，请稍后重试")
        val response = json.parseToJsonElement(responseText).jsonObject
        if (response["error"] != null) error(response.string("error") ?: "云盘服务暂不可用")
        Result.success(transform(response["data"]?.jsonObject ?: error("云盘服务返回格式错误")))
            }
            catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Result.failure(error)
            }
        }
    } catch (_: TimeoutCancellationException) {
        val message = when (action) {
            "sync_collection" -> "刷新视频等待超时，请稍后重试"
            "playback_url" -> "获取播放地址超时，请稍后重试"
            else -> "读取云盘目录超时，请稍后重试"
        }
        Result.failure(IllegalStateException(message))
    }

    private fun timeoutFor(action: String): Long = when (action) {
        "sync_collection" -> SYNC_REQUEST_TIMEOUT_MS
        // 冷启动时服务端要依次请求授权 token 和播放地址；两步都会自动重试一次。
        // 最坏约为 81 秒（2 * (20 + 0.5 + 20)），再预留鉴权和数据库校验时间。
        // 不能沿用目录浏览的 20 秒上限，否则服务端仍在完成首个播放请求时 App 已取消。
        "playback_url" -> PLAYBACK_URL_REQUEST_TIMEOUT_MS
        else -> CLOUD_REQUEST_TIMEOUT_MS
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int = string(key)?.toIntOrNull() ?: 0

    private companion object {
        const val VIDEO_COVERS_BUCKET = "video-covers"
        // 服务端或云盘异常时，不能让目录选择弹窗永久处于加载状态。
        const val CLOUD_REQUEST_TIMEOUT_MS = 20_000L
        const val PLAYBACK_URL_REQUEST_TIMEOUT_MS = 95_000L
        // 同步会递归读取多个云盘目录，所需时间通常比目录选择长。
        const val SYNC_REQUEST_TIMEOUT_MS = 120_000L
        // 与 shared SupabaseModule 相同的匿名发布密钥；不是管理员密钥。
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWlrZnhlaGhjcnRya2lveHFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAzMjQxMTMsImV4cCI6MjA5NTkwMDExM30.PbYlbBiUN7CI4EFedzzEWANrcLI1gElvAjBTlGKi7Go"
    }
}
