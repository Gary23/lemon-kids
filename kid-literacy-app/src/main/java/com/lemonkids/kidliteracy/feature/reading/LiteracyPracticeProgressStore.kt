package com.lemonkids.kidliteracy.feature.reading

import android.content.Context
import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 认字练习的本地进度。
 *
 * 星星和“已读/未读”先在本地即时生效；联网后由调用方静默同步到云端。
 * 每次启动应用都会清理昨天及更早的进度；未学习任务整体完成并写入已认识字表后，
 * 其当天的进度仍会由首页快照保持完成展示。
 */
class LiteracyPracticeProgressStore(context: Context, private val childId: String) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun snapshot(): Map<String, Int> {
        clearExpiredEntries()
        return preferences.all
            .asSequence()
            .filter { (key, value) ->
                key.startsWith(entryPrefix()) &&
                    !key.startsWith(pendingEntryPrefix()) &&
                    value is Int
            }
            .associate { (key, value) -> decodeKey(key.removePrefix(entryPrefix())) to (value as Int) }
    }

    /**
     * 记录本轮读对的次数；达到上限后不再累加。
     *
     * 字的单轮朗读可包含多个重复字，例如“花花”，因此一次评测最多会增加多颗星；
     * 词和句仍传入 1，保持原有计数方式。
     */
    fun recordCorrectReadings(target: ReadingTarget, correctReadings: Int = 1): Int {
        clearExpiredEntries()
        val progressKey = target.practiceProgressKey()
        val nextCount = ((snapshot()[progressKey] ?: 0) + correctReadings.coerceAtLeast(0))
            .coerceAtMost(target.requiredCorrectReadings())
        preferences.edit()
            .putInt(entryPrefix() + encodeKey(progressKey), nextCount)
            // 上传失败时保留这一项。下次首页加载后会自动重试，不能让离线朗读丢失。
            .putInt(pendingEntryPrefix() + encodeKey(progressKey), nextCount)
            .apply()
        return nextCount
    }

    /** 将同一绑定码下其他设备已经上传的当天进度合并到本地，取较大值避免回退星数。 */
    fun mergeRemoteProgress(remoteProgress: Map<String, Int>, targets: Collection<ReadingTarget>) {
        if (remoteProgress.isEmpty() || targets.isEmpty()) return
        clearExpiredEntries()
        val local = snapshot()
        val editor = preferences.edit()
        targets.forEach { target ->
            val remoteCount = remoteProgress[target.practiceProgressSyncKey()] ?: return@forEach
            val progressKey = target.practiceProgressKey()
            val merged = maxOf(local[progressKey] ?: 0, remoteCount.coerceAtMost(target.requiredCorrectReadings()))
            if (merged > (local[progressKey] ?: 0)) {
                // 云端已确认的数据不能重新标记为待上传。
                editor.putInt(entryPrefix() + encodeKey(progressKey), merged)
            }
        }
        editor.apply()
    }

    /** 仅返回本设备尚未成功上传的当天进度。 */
    fun pendingProgress(): Map<String, Int> {
        clearExpiredEntries()
        return preferences.all
            .asSequence()
            .filter { (key, value) -> key.startsWith(pendingEntryPrefix()) && value is Int }
            .associate { (key, value) ->
                decodeKey(key.removePrefix(pendingEntryPrefix())) to (value as Int)
            }
    }

    /**
     * 兼容本次云同步上线前已经仅存于本机的当天星星：首次进入首页时全部排入静默队列。
     * 标记按天保存，次日的全新记录会在写入时自然进入队列。
     */
    fun queueExistingProgressForSync() {
        if (preferences.getBoolean(initialSyncQueuedKey(), false)) return
        val editor = preferences.edit()
        snapshot().forEach { (progressKey, count) ->
            val pendingKey = pendingEntryPrefix() + encodeKey(progressKey)
            if (!preferences.contains(pendingKey)) editor.putInt(pendingKey, count)
        }
        editor.putBoolean(initialSyncQueuedKey(), true).apply()
    }

    /** 成功上传后清除不晚于本次确认值的待同步标记，避免竞态覆盖后续朗读。 */
    fun markProgressSynced(progressKey: String, confirmedCount: Int) {
        val key = pendingEntryPrefix() + encodeKey(progressKey)
        if ((preferences.getInt(key, 0)) <= confirmedCount) {
            preferences.edit().remove(key).apply()
        }
    }

    /**
     * 仅记录待认识主字的点读行为。它和朗读星级一起按天保存，以便应用重启后仍能
     * 在整字完成时正确决定收录到“已认识的字”还是直接写入字库。
     */
    fun markCharacterAudioPointRead(literacyCharacterId: String) {
        clearExpiredEntries()
        preferences.edit().putBoolean(characterPointReadKey(literacyCharacterId), true).apply()
    }

    fun hasCharacterAudioPointRead(literacyCharacterId: String): Boolean {
        clearExpiredEntries()
        return preferences.getBoolean(characterPointReadKey(literacyCharacterId), false)
    }

    fun clearCharacter(literacyCharacterId: String) {
        val editor = preferences.edit()
        editor.remove(characterPointReadKey(literacyCharacterId))
        snapshot().keys
            .filter { key -> key.split(KEY_SEPARATOR).getOrNull(1) == literacyCharacterId }
            .forEach { key -> editor.remove(entryPrefix() + encodeKey(key)) }
        pendingProgress().keys
            .filter { key -> key.split(KEY_SEPARATOR).getOrNull(1) == literacyCharacterId }
            .forEach { key -> editor.remove(pendingEntryPrefix() + encodeKey(key)) }
        editor.apply()
    }

    private fun clearExpiredEntries() {
        val currentPrefix = entryPrefix()
        val childPrefix = "v2:$childId:"
        val editor = preferences.edit()
        preferences.all.keys
            .filter { key -> key.startsWith(childPrefix) && !key.startsWith(currentPrefix) }
            .forEach(editor::remove)
        // v1 进度没有日期，不能满足隔天自动清理；升级后直接废弃。
        preferences.all.keys
            .filter { key -> key.startsWith("v1:$childId:") }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun entryPrefix() = "v2:$childId:${today()}:"

    private fun pendingEntryPrefix() = entryPrefix() + PENDING_PROGRESS_PREFIX

    private fun initialSyncQueuedKey() = entryPrefix() + INITIAL_SYNC_QUEUED_KEY

    private fun characterPointReadKey(literacyCharacterId: String) =
        entryPrefix() + CHARACTER_POINT_READ_PREFIX + encodeKey(literacyCharacterId)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    private fun encodeKey(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decodeKey(value: String): String =
        String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)

    private companion object {
        const val PREFERENCES_NAME = "lemonkids_literacy_practice_progress"
        const val KEY_SEPARATOR = "\u001F"
        const val CHARACTER_POINT_READ_PREFIX = "character-point-read:"
        const val PENDING_PROGRESS_PREFIX = "pending-progress:"
        const val INITIAL_SYNC_QUEUED_KEY = "initial-sync-queued"
    }
}

/**
 * 待认识任务：主字三次、每个词两次、句子一次。
 * 已认识字复习时主字按入库日期的分组读三、二或一次，每个词一次；复习页不展示句子。
 */
fun ReadingTarget.requiredCorrectReadings(): Int = when {
    contentSource == ReadingContentSource.RECOGNIZED &&
        targetType == "character" &&
        characterRequiredReadings != null -> characterRequiredReadings
    contentSource == ReadingContentSource.RECOGNIZED && targetType == "word" -> 1
    targetType == "sentence" -> 1
    contentSource == ReadingContentSource.TASK && targetType == "word" -> 2
    else -> 3
}

/** 键中包含孩子任务、内容类型、顺序与文本，避免相同文本的不同学习项相互覆盖。 */
fun ReadingTarget.practiceProgressKey(): String = listOf(
    contentSource.wireValue,
    literacyCharacterId,
    targetType,
    itemOrder.toString(),
    wordText ?: sentenceText ?: displayText
).joinToString("\u001F")

/** 云端唯一定位不含教学文本，避免服务端音频或文案刷新导致同一学习项产生两份进度。 */
fun ReadingTarget.practiceProgressSyncKey(): String = listOf(
    contentSource.wireValue,
    literacyCharacterId,
    targetType,
    itemOrder.toString()
).joinToString("\u001F")
