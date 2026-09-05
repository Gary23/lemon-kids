package com.lemonkids.familyvideo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class VideoCategory(val id: String = "", @SerialName("family_id") val familyId: String = "", val name: String = "", @SerialName("sort_order") val sortOrder: Int = 0, @SerialName("is_builtin") val isBuiltin: Boolean = false)
@Serializable data class VideoCollection(val id: String = "", @SerialName("family_id") val familyId: String = "", @SerialName("drive_folder_id") val driveFolderId: String = "", val name: String = "", @SerialName("cover_url") val coverUrl: String? = null, @SerialName("category_id") val categoryId: String? = null, @SerialName("sync_status") val syncStatus: String = "ready")
@Serializable data class VideoMedia(val id: String = "", @SerialName("collection_id") val collectionId: String = "", @SerialName("drive_file_id") val driveFileId: String = "", val name: String = "", @SerialName("duration_seconds") val durationSeconds: Long? = null, @SerialName("sort_order") val sortOrder: Int = 0, @Transient val playbackUrl: String? = null)
@Serializable data class VideoPlaybackRecord(@SerialName("media_id") val mediaId: String = "", @SerialName("progress_seconds") val progressSeconds: Long = 0, @SerialName("duration_seconds") val durationSeconds: Long = 0, @SerialName("is_completed") val isCompleted: Boolean = false)

data class FamilyVideoLibrary(val categories: List<VideoCategory>, val collections: List<VideoCollection>, val media: List<VideoMedia>, val playback: List<VideoPlaybackRecord>) {
    fun mediaFor(collectionId: String) = media.filter { it.collectionId == collectionId }.sortedBy { it.sortOrder }
    fun progressFor(mediaId: String) = playback.firstOrNull { it.mediaId == mediaId }
}

/** 云盘供应商边界：OAuth 令牌必须在受保护服务端持有，客户端只接收短期结果。 */
interface CloudDriveProvider {
    suspend fun beginAuthorization(): Result<Unit>
    suspend fun selectSyncRoot(): Result<CloudFolder>
    suspend fun sync(rootFolderId: String): Result<SyncSummary>
    suspend fun freshPlaybackUrl(fileId: String): Result<String>
}
data class CloudFolder(val id: String, val name: String, val breadcrumb: String)
data class SyncSummary(val added: Int, val updated: Int, val unavailable: Int)

interface FamilyVideoRepository {
    suspend fun loadLibrary(familyId: String): Result<FamilyVideoLibrary>
    suspend fun updatePlayback(record: VideoPlaybackRecord): Result<Unit>
}

@Singleton
class SupabaseFamilyVideoRepository @Inject constructor(private val supabase: SupabaseClient) : FamilyVideoRepository {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    override suspend fun loadLibrary(familyId: String): Result<FamilyVideoLibrary> = runCatching {
        val categories = postgrest.from("video_categories").select { filter { eq("family_id", familyId) }; order("sort_order", Order.ASCENDING) }.decodeList<VideoCategory>()
        val collections = postgrest.from("video_collections").select { filter { eq("family_id", familyId) }; order("name", Order.ASCENDING) }.decodeList<VideoCollection>()
        val ids = collections.map { it.id }.toSet()
        // RLS 已限制为当前家长所在家庭；这里再按本次目录集合筛选，避免依赖路径型筛选参数。
        val media = if (ids.isEmpty()) emptyList() else postgrest.from("video_media").select { order("sort_order", Order.ASCENDING) }.decodeList<VideoMedia>().filter { it.collectionId in ids }
        val mediaIds = media.map { it.id }.toSet()
        val playback = if (mediaIds.isEmpty()) emptyList() else postgrest.from("video_playback_records").select { }.decodeList<VideoPlaybackRecord>().filter { it.mediaId in mediaIds }
        FamilyVideoLibrary(categories, collections, media, playback)
    }
    override suspend fun updatePlayback(record: VideoPlaybackRecord): Result<Unit> = runCatching {
        postgrest.from("video_playback_records").upsert(record)
    }
}
