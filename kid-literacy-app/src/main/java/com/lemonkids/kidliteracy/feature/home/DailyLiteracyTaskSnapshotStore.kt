package com.lemonkids.kidliteracy.feature.home

import android.content.Context
import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.model.LiteracyExample
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 首页当天的识字任务。
 *
 * 任务在当天第一次取得候选字时落盘，之后即使服务端的 learned_at 变化，也继续
 * 使用这一份字、词、句和顺序。逐次朗读次数仍由 LiteracyPracticeProgressStore 管理；
 * 这里仅保留任务本身及已成功转入字库的标记，以便回到首页或重启应用后仍能展示原任务。
 */
@Singleton
class DailyLiteracyTaskSnapshotStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true }

    /** 有当天快照时始终返回该快照；没有候选字时不创建空快照。 */
    fun getOrCreate(childId: String, candidates: List<ChildLiteracyCharacter>): DailyLiteracyTaskSnapshot? {
        if (childId.isBlank()) return null
        val today = today()
        read(childId)?.takeIf { it.date == today }?.let { snapshot ->
            // 学习任务的字、词、句与顺序当天固定，但 TTS 是异步生成的。必须用服务端
            // 最新的 URL/版本刷新快照，否则早于音频生成创建的当天任务会一直走系统 TTS。
            // 首页任务数量调整为 6 个后，同时裁剪旧快照，确保更新后当日也立即遵守新上限。
            return snapshot.withFreshAudio(candidates).limitCharacters(DAILY_TASK_CHARACTER_LIMIT).also { refreshed ->
                if (refreshed != snapshot) write(childId, refreshed)
            }
        }
        if (candidates.isEmpty()) return null

        return DailyLiteracyTaskSnapshot(
            date = today,
            characters = candidates.take(DAILY_TASK_CHARACTER_LIMIT)
        ).also { write(childId, it) }
    }

    /** 标记只作用于今天已冻结的任务，避免异步完成结果污染次日任务。 */
    fun markCompleted(childId: String, literacyCharacterId: String): DailyLiteracyTaskSnapshot? {
        val snapshot = read(childId)?.takeIf { it.date == today() } ?: return null
        if (literacyCharacterId !in snapshot.characters.map { it.id }) return snapshot
        if (literacyCharacterId in snapshot.completedCharacterIds) return snapshot

        return snapshot.copy(
            completedCharacterIds = snapshot.completedCharacterIds + literacyCharacterId
        ).also { write(childId, it) }
    }

    private fun read(childId: String): DailyLiteracyTaskSnapshot? = runCatching {
        preferences.getString(key(childId), null)?.let { raw ->
            json.decodeFromString<DailyLiteracyTaskSnapshot>(raw)
        }
    }.getOrNull()

    private fun write(childId: String, snapshot: DailyLiteracyTaskSnapshot) {
        preferences.edit().putString(key(childId), json.encodeToString(snapshot)).apply()
    }

    private fun key(childId: String) = "v1:$childId"

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    private companion object {
        const val PREFERENCES_NAME = "lemonkids_daily_literacy_task_snapshot"
        const val DAILY_TASK_CHARACTER_LIMIT = 6
    }
}

@Serializable
data class DailyLiteracyTaskSnapshot(
    val date: String,
    val characters: List<ChildLiteracyCharacter>,
    val completedCharacterIds: Set<String> = emptySet()
)

private fun DailyLiteracyTaskSnapshot.limitCharacters(limit: Int): DailyLiteracyTaskSnapshot {
    val displayedCharacters = characters.take(limit)
    return copy(
        characters = displayedCharacters,
        completedCharacterIds = completedCharacterIds.intersect(displayedCharacters.map { it.id }.toSet())
    )
}

/** 仅同步异步生成的音频元数据，不改变已冻结的教学文本、排序和完成状态。 */
private fun DailyLiteracyTaskSnapshot.withFreshAudio(
    candidates: List<ChildLiteracyCharacter>
): DailyLiteracyTaskSnapshot {
    val latestById = candidates.associateBy { it.id }
    return copy(
        characters = characters.map { cached ->
            val latest = latestById[cached.id] ?: return@map cached
            cached.copy(
                characterAudioUrl = latest.characterAudioUrl,
                characterAudioVersion = latest.characterAudioVersion,
                characterAudioHash = latest.characterAudioHash,
                words = cached.words.refreshAudioFrom(latest.words),
                sentences = cached.sentences.refreshAudioFrom(latest.sentences)
            )
        }
    )
}

/** 文本仍以当天任务快照为准；同一位置且文本一致时才采纳服务端的音频版本。 */
private fun List<LiteracyExample>.refreshAudioFrom(latest: List<LiteracyExample>): List<LiteracyExample> =
    mapIndexed { index, cached ->
        latest.getOrNull(index)
            ?.takeIf { it.text == cached.text }
            ?.let {
                cached.copy(
                    audioUrl = it.audioUrl,
                    audioVersion = it.audioVersion,
                    audioHash = it.audioHash
                )
            }
            ?: cached
    }
