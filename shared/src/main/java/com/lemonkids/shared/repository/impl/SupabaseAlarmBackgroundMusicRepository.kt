package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.AlarmBackgroundMusicAsset
import com.lemonkids.shared.model.AlarmBackgroundMusicStorage
import com.lemonkids.shared.model.FamilyAlarmMusicUpload
import com.lemonkids.shared.repository.AlarmBackgroundMusicRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

@Singleton
class SupabaseAlarmBackgroundMusicRepository @Inject constructor(
    private val supabase: SupabaseClient
) : AlarmBackgroundMusicRepository {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val storage get() = supabase.pluginManager.getPlugin(Storage)

    override suspend fun getPublishedMusic(): Result<List<AlarmBackgroundMusicAsset>> = runCatching {
        val operated = postgrest.from(TABLE).select {
            filter { eq("status", "published") }
            order("sort_order", Order.ASCENDING)
        }.decodeList<AlarmBackgroundMusicAsset>().map {
            it.copy(storageBucket = AlarmBackgroundMusicStorage.OPERATED_BUCKET)
        }
        // RLS 会把家庭曲库限制为当前家长或已绑定的本家庭监控 Pad；不需要客户端传家庭 ID。
        val family = postgrest.from(FAMILY_TABLE).select {
            filter { eq("status", "published") }
            order("created_at", Order.ASCENDING)
        }.decodeList<AlarmBackgroundMusicAsset>().map {
            it.copy(storageBucket = AlarmBackgroundMusicStorage.FAMILY_BUCKET)
        }
        (operated + family).filter { it.isUsable() }
    }

    override suspend fun createDownloadUrl(music: AlarmBackgroundMusicAsset): Result<String> = runCatching {
        require(music.isUsable()) { "曲目元数据不合法" }
        if (music.storageBucket == AlarmBackgroundMusicStorage.FAMILY_BUCKET) {
            // 家庭对象的 SELECT RLS 已校验对象路径中的 family_id 与当前绑定关系。
            storage.from(AlarmBackgroundMusicStorage.FAMILY_BUCKET).createSignedUrl(music.objectPath, 10.minutes)
        } else {
            // 运营曲目仍须由 RPC 再次确认，签名 URL 只在本次下载中使用。
            val controlledPath: String = postgrest.rpc(
                function = "get_alarm_background_music_download_target",
                parameters = mapOf("p_music_id" to music.id)
            ).decodeSingle()
            require(controlledPath == music.objectPath) { "曲目对象路径与受控下载目标不一致" }
            storage.from(AlarmBackgroundMusicStorage.OPERATED_BUCKET).createSignedUrl(controlledPath, 10.minutes)
        }
    }

    override suspend fun uploadFamilyMusic(
        familyId: String,
        upload: FamilyAlarmMusicUpload
    ): Result<AlarmBackgroundMusicAsset> = runCatching {
        require(runCatching { UUID.fromString(familyId) }.isSuccess) { "家庭信息无效" }
        val name = upload.displayName.trim().take(80)
        require(name.isNotEmpty()) { "请填写音乐名称" }
        require(upload.mimeType in ALLOWED_MIME_TYPES) { "仅支持 MP3 或 OGG 音频" }
        require(upload.bytes.size in 1..AlarmBackgroundMusicAsset.MAX_SIZE_BYTES.toInt()) { "音频文件不能超过 5 MB" }
        require(upload.durationMs in 30_000..90_000) { "音频时长需为 30 至 90 秒" }
        val id = "family_${UUID.randomUUID().toString().replace("-", "")}"
        val version = "1"
        val extension = if (upload.mimeType == "audio/ogg") "ogg" else "mp3"
        val path = "custom/$familyId/$id/v$version/background.$extension"
        val sha256 = MessageDigest.getInstance("SHA-256").digest(upload.bytes)
            .joinToString("") { "%02x".format(it) }
        storage.from(AlarmBackgroundMusicStorage.FAMILY_BUCKET).upload(path = path, data = upload.bytes)
        val asset = AlarmBackgroundMusicAsset(
            id = id, name = name, version = version, objectPath = path, mimeType = upload.mimeType,
            sizeBytes = upload.bytes.size.toLong(), sha256 = sha256, durationMs = upload.durationMs,
            status = "published", storageBucket = AlarmBackgroundMusicStorage.FAMILY_BUCKET
        )
        // 不使用 Map<String, Any>：其中混有 String/Long/Int，Kotlin 序列化会尝试
        // 为 Any 寻找 serializer，导致音乐文件成功上传后元数据写入失败。
        postgrest.from(FAMILY_TABLE).insert(
            FamilyAlarmMusicInsert(
                id = asset.id,
                familyId = familyId,
                name = asset.name,
                version = asset.version,
                objectPath = asset.objectPath,
                mimeType = asset.mimeType,
                sizeBytes = asset.sizeBytes,
                sha256 = asset.sha256,
                durationMs = asset.durationMs,
                status = asset.status
            )
        )
        asset
    }

    companion object {
        const val TABLE = "alarm_background_music"
        const val FAMILY_TABLE = "alarm_family_background_music"
        private val ALLOWED_MIME_TYPES = setOf("audio/ogg", "audio/mpeg")
    }
}

/** 仅对应 alarm_family_background_music 的可写列，避免把客户端内存字段提交给 PostgREST。 */
@Serializable
private data class FamilyAlarmMusicInsert(
    @SerialName("id") val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("object_path") val objectPath: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("sha256") val sha256: String,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("status") val status: String
)
