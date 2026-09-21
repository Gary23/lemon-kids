package com.lemonkids.familyvideo.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
 * 所有调用都只携带当前 Supabase 会话。123 云盘应用密钥与短期 access token 不进入 APK、
 * Android 日志或函数响应；token 仅在服务端受限缓存表中短期复用。
 */
interface CloudDriveProvider {
    suspend fun connection(): Result<DriveConnection>
    suspend fun connect(): Result<DriveConnection>
    suspend fun browse(parentFolderId: String = "0", breadcrumb: String = "123 云盘", cursor: String = "0", forceRefresh: Boolean = false): Result<CloudFolderPage>
    suspend fun folderCover(folderId: String): Result<CloudFolderCover>
    suspend fun syncCollection(collectionId: String): Result<SyncSummary>
    suspend fun freshPlaybackUrl(fileId: String): Result<String>
}
data class CloudFolder(val id: String, val name: String, val breadcrumb: String, val isFolder: Boolean = true)
data class CloudFolderPage(val folders: List<CloudFolder>, val nextCursor: String? = null, val hasMore: Boolean = false)
data class CloudFolderCover(val url: String, val name: String, val fileId: String, val locatorProof: String = "")
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
class SupabaseEdgeCloudDriveProvider @Inject constructor(
    private val supabase: SupabaseClient,
    @ApplicationContext private val context: Context,
) : CloudDriveProvider {
    private val auth get() = supabase.pluginManager.getPlugin(Auth)
    private val json = Json { ignoreUnknownKeys = true }
    private val connectionPreferences by lazy {
        context.applicationContext.getSharedPreferences(CONNECTION_CACHE_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun connection(): Result<DriveConnection> {
        val userId = auth.currentUserOrNull()?.id ?: return Result.failure(IllegalStateException("登录已过期，请重新登录"))
        readCachedConnection(userId)?.let { return Result.success(it) }
        return call("connection_status") { result ->
            connectionFrom(result)
        }.onSuccess { writeCachedConnection(userId, it) }
    }

    override suspend fun connect(): Result<DriveConnection> {
        val userId = auth.currentUserOrNull()?.id ?: return Result.failure(IllegalStateException("登录已过期，请重新登录"))
        return call("connect") { result ->
            connectionFrom(result)
        }.onSuccess { writeCachedConnection(userId, it) }
    }

    private fun connectionFrom(result: JsonObject) =
        DriveConnection(
            status = result.string("authorization_status") ?: "disconnected",
            accountHint = result.string("drive_account_hint"),
            rootFolderId = result.string("sync_root_folder_id"),
            rootPath = result.string("sync_root_path"),
            lastSyncedAt = result.string("last_synced_at")
        )

    override suspend fun browse(parentFolderId: String, breadcrumb: String, cursor: String, forceRefresh: Boolean): Result<CloudFolderPage> {
        val userId = auth.currentUserOrNull()?.id ?: return Result.failure(IllegalStateException("登录已过期，请重新登录"))
        if (!forceRefresh) readCachedDirectoryPage(userId, parentFolderId, cursor)?.let { return Result.success(it) }
        return call(
            "browse", mapOf(
                "parentFolderId" to parentFolderId,
                "breadcrumb" to breadcrumb,
                "cursor" to cursor,
                "forceRefresh" to forceRefresh.toString(),
            )
        ) { result ->
            CloudFolderPage(
                folders = result["folders"]?.jsonArray.orEmpty().map { node ->
                    val item = node.jsonObject
                    CloudFolder(
                        id = item.string("id") ?: error("云盘目录缺少 ID"),
                        name = item.string("name") ?: "未命名目录",
                        breadcrumb = item.string("breadcrumb") ?: breadcrumb,
                        isFolder = item["isFolder"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                    )
                },
                nextCursor = result.string("next_cursor"),
                hasMore = result.boolean("has_more"),
            )
        }.onSuccess { writeCachedDirectoryPage(userId, parentFolderId, cursor, it) }
    }

    override suspend fun folderCover(folderId: String): Result<CloudFolderCover> {
        val userId = auth.currentUserOrNull()?.id ?: return Result.failure(IllegalStateException("登录已过期，请重新登录"))
        val cached = readCachedCoverLocator(userId, folderId)
        return call("folder_cover", buildMap {
            put("folderId", folderId)
            cached?.locatorProof?.takeIf(String::isNotBlank)?.let { put("cachedLocatorProof", it) }
        }) { result ->
            CloudFolderCover(
                url = result.string("url") ?: error("云盘没有返回封面地址"),
                name = result.string("name") ?: "folder.jpg",
                fileId = result.string("file_id") ?: error("云盘没有返回封面文件 ID"),
                locatorProof = result.string("locator_proof") ?: error("云盘没有返回封面归属凭据"),
            )
        }.onSuccess { writeCachedCoverLocator(userId, folderId, it) }
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
        if (response["error"] != null) error(userFacingDriveError(action, response.string("error") ?: "云盘服务暂不可用"))
        Result.success(transform(response["data"]?.jsonObject ?: error("云盘服务返回格式错误")))
            }
            catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Result.failure(error)
            }
        }
    } catch (_: TimeoutCancellationException) {
        val message = when (action) {
            "sync_collection" -> "目录整体同步超时，已保留上次视频，可稍后刷新"
            "connect" -> "连接 123 云盘超时，请稍后重试"
            "playback_url" -> "获取播放地址超时，请稍后重试"
            "folder_cover" -> "读取云盘封面超时，请稍后重试"
            "browse" -> "读取云盘目录超时，请稍后重试"
            else -> "读取云盘连接状态超时，请稍后重试"
        }
        Result.failure(IllegalStateException(message))
    }

    private fun timeoutFor(action: String): Long = when (action) {
        "sync_collection" -> SYNC_REQUEST_TIMEOUT_MS
        // 123 交互统一由服务端在 85 秒总预算内完成 token 复用、一次短暂重试与业务读取；
        // 客户端额外预留响应传输时间，不能再让目录浏览在 20 秒本地提前取消。
        "connect", "browse", "playback_url", "folder_cover" -> LONG_CLOUD_REQUEST_TIMEOUT_MS
        else -> CLOUD_REQUEST_TIMEOUT_MS
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int = string(key)?.toIntOrNull() ?: 0
    private fun JsonObject.boolean(key: String): Boolean = this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

    private fun userFacingDriveError(action: String, message: String): String = when {
        action == "sync_collection" && message.contains("整体同步超时") -> "目录整体同步超时，已保留上次视频，可稍后刷新"
        message.contains("授权响应超时") -> "123 云盘授权响应超时，请稍后刷新"
        message.contains("授权失败") -> "123 云盘授权失败，请稍后重试"
        action == "folder_cover" && message.contains("封面读取") -> "读取云盘封面超时，请稍后重试"
        action == "playback_url" && message.contains("播放地址读取") -> "获取播放地址超时，请稍后重试"
        message.contains("目录读取") -> "123 云盘目录读取慢或超时，请稍后重试"
        else -> message
    }

    /** 仅缓存服务端登记的非敏感连接状态；用户 ID 用于隔离不同登录账号。 */
    private fun readCachedConnection(userId: String): DriveConnection? {
        val prefix = "connection.$userId."
        val savedAt = connectionPreferences.getLong("${prefix}saved_at", 0L)
        if (savedAt == 0L || System.currentTimeMillis() - savedAt > CONNECTION_CACHE_TTL_MS) return null
        val status = connectionPreferences.getString("${prefix}status", null) ?: return null
        return DriveConnection(
            status = status,
            accountHint = connectionPreferences.getString("${prefix}account_hint", null),
            rootFolderId = connectionPreferences.getString("${prefix}root_folder_id", null),
            rootPath = connectionPreferences.getString("${prefix}root_path", null),
            lastSyncedAt = connectionPreferences.getString("${prefix}last_synced_at", null),
        )
    }

    private fun writeCachedConnection(userId: String, connection: DriveConnection) {
        val prefix = "connection.$userId."
        connectionPreferences.edit {
            putString("${prefix}status", connection.status)
            putString("${prefix}account_hint", connection.accountHint)
            putString("${prefix}root_folder_id", connection.rootFolderId)
            putString("${prefix}root_path", connection.rootPath)
            putString("${prefix}last_synced_at", connection.lastSyncedAt)
            putLong("${prefix}saved_at", System.currentTimeMillis())
        }
    }

    /**
     * 目录页仅含供选择器显示的 ID、名称、路径与游标。缓存按登录用户隔离，
     * 不存储 123 token、文件条目、下载地址或任何请求数据。
     */
    private fun readCachedDirectoryPage(userId: String, folderId: String, cursor: String): CloudFolderPage? {
        val key = directoryPageCacheKey(userId, folderId, cursor)
        val savedAt = connectionPreferences.getLong("${key}.saved_at", 0L)
        if (savedAt == 0L || System.currentTimeMillis() - savedAt > DIRECTORY_PAGE_CACHE_TTL_MS) return null
        val serialized = connectionPreferences.getString("${key}.data", null) ?: return null
        return runCatching {
            val value = json.parseToJsonElement(serialized).jsonObject
            CloudFolderPage(
                folders = value["folders"]?.jsonArray.orEmpty().map { node ->
                    val item = node.jsonObject
                    CloudFolder(
                        id = item.string("id") ?: error("缓存目录缺少 ID"),
                        name = item.string("name") ?: "未命名目录",
                        breadcrumb = item.string("breadcrumb") ?: "123 云盘",
                        isFolder = item.boolean("is_folder"),
                    )
                },
                nextCursor = value.string("next_cursor"),
                hasMore = value.boolean("has_more"),
            )
        }.getOrNull()
    }

    private fun writeCachedDirectoryPage(userId: String, folderId: String, cursor: String, page: CloudFolderPage) {
        val key = directoryPageCacheKey(userId, folderId, cursor)
        val serialized = buildJsonObject {
            put("next_cursor", page.nextCursor?.let(::JsonPrimitive) ?: JsonNull)
            put("has_more", JsonPrimitive(page.hasMore))
            put("folders", buildJsonArray {
                page.folders.forEach { folder -> add(buildJsonObject {
                    put("id", JsonPrimitive(folder.id))
                    put("name", JsonPrimitive(folder.name))
                    put("breadcrumb", JsonPrimitive(folder.breadcrumb))
                    put("is_folder", JsonPrimitive(folder.isFolder))
                }) }
            })
        }.toString()
        connectionPreferences.edit {
            putString("${key}.data", serialized)
            putLong("${key}.saved_at", System.currentTimeMillis())
        }
    }

    private fun directoryPageCacheKey(userId: String, folderId: String, cursor: String) = "directory.$userId.$folderId.$cursor"

    /**
     * 只保留服务端签发的“目录 + 文件”归属凭据及显示信息，用于省去下一次选中同一
     * 目录时的列表请求。旧版裸文件 ID 缓存使用不同键，不会被新逻辑信任。下载地址、
     * 图片字节、token 和请求数据均不进入本地缓存。
     */
    private fun readCachedCoverLocator(userId: String, folderId: String): CloudFolderCover? {
        val key = "cover.v2.$userId.$folderId"
        val savedAt = connectionPreferences.getLong("${key}.saved_at", 0L)
        if (savedAt == 0L || System.currentTimeMillis() - savedAt > COVER_LOCATOR_CACHE_TTL_MS) return null
        val fileId = connectionPreferences.getString("${key}.file_id", null) ?: return null
        val name = connectionPreferences.getString("${key}.name", null) ?: return null
        val proof = connectionPreferences.getString("${key}.proof", null) ?: return null
        return CloudFolderCover(url = "", name = name, fileId = fileId, locatorProof = proof)
    }

    private fun writeCachedCoverLocator(userId: String, folderId: String, cover: CloudFolderCover) {
        if (cover.locatorProof.isBlank()) return
        val key = "cover.v2.$userId.$folderId"
        connectionPreferences.edit {
            putString("${key}.file_id", cover.fileId)
            putString("${key}.name", cover.name)
            putString("${key}.proof", cover.locatorProof)
            putLong("${key}.saved_at", System.currentTimeMillis())
        }
    }

    private companion object {
        const val CONNECTION_CACHE_NAME = "family_video_drive_connection"
        const val CONNECTION_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        const val DIRECTORY_PAGE_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        const val COVER_LOCATOR_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        const val VIDEO_COVERS_BUCKET = "video-covers"
        // 服务端或云盘异常时，不能让目录选择弹窗永久处于加载状态。
        const val CLOUD_REQUEST_TIMEOUT_MS = 20_000L
        const val LONG_CLOUD_REQUEST_TIMEOUT_MS = 95_000L
        // 服务端完整目录同步有 55 秒总预算；额外预留函数鉴权、响应传输与本机调度时间。
        const val SYNC_REQUEST_TIMEOUT_MS = 65_000L
        // 与 shared SupabaseModule 相同的匿名发布密钥；不是管理员密钥。
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWlrZnhlaGhjcnRya2lveHFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAzMjQxMTMsImV4cCI6MjA5NTkwMDExM30.PbYlbBiUN7CI4EFedzzEWANrcLI1gElvAjBTlGKi7Go"
    }
}
