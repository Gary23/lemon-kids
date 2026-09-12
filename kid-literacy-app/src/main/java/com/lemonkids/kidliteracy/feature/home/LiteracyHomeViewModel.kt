package com.lemonkids.kidliteracy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.model.RecognizedCharacter
import com.lemonkids.shared.repository.ChildLiteracyCharacterRepository
import com.lemonkids.shared.repository.RecognizedCharacterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

data class LiteracyHomeUiState(
    val isLoading: Boolean = true,
    val groups: List<LiteracyCharacterGroup> = emptyList(),
    /** 每次成功刷新首页时递增，用于使仅内存的评测缓存准确失效。 */
    val dataVersion: Long = 0
)

data class LiteracyCharacterGroup(
    val type: LiteracyGroupType,
    val groupNumber: Int,
    /** 已认识字分组对应的收录日期（中国时区），待认识分组为空。 */
    val recognizedDate: LocalDate? = null,
    /** 首页只展示汉字；完整认字数据用于进入字、词、句学习页。 */
    val characters: List<String>,
    /** 待认识分组进入学习页时所需的完整认字任务数据。 */
    val learningCharacters: List<ChildLiteracyCharacter> = emptyList(),
    /** 已认识分组进入学习页时所需的独立表数据。 */
    val recognizedCharacters: List<RecognizedCharacter> = emptyList(),
    /**
     * 已认识字复习时主字需要读对的次数。
     *
     * 首页按入库日期从近到远分成三组：最近日期读 3 次，随后日期读 2 次，
     * 最后日期读 1 次。待认识字仍固定沿用自身的三次规则。
     */
    val recognizedCharacterRequiredReadings: Int = 3,
    /** 当日任务完成后仍保留在原任务中的完成态。 */
    val completedCharacterIds: Set<String> = emptySet()
) {
    val isKnown: Boolean get() = type == LiteracyGroupType.KNOWN
    val canStartLearning: Boolean get() = learningCharacters.isNotEmpty() || recognizedCharacters.isNotEmpty()
}

enum class LiteracyGroupType { KNOWN, TO_LEARN }

@HiltViewModel
class LiteracyHomeViewModel @Inject constructor(
    private val characterRepository: ChildLiteracyCharacterRepository,
    private val recognizedCharacterRepository: RecognizedCharacterRepository,
    private val dailyTaskSnapshotStore: DailyLiteracyTaskSnapshotStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiteracyHomeUiState())
    val uiState: StateFlow<LiteracyHomeUiState> = _uiState.asStateFlow()

    /** 每次回到首页或点击刷新都重新查询云端；当天待认识字由服务端共享快照固定。 */
    fun load(childId: String) {
        if (childId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val recognizedResult = async { loadRecentRecognizedCharacters(childId) }
            val literacyResult = async { characterRepository.getCharacters(childId) }
            // 将旧版本已有的本地当天快照作为首次升级时的候选，避免升级当天换题；
            // 服务端已有快照时它不会覆盖服务端结果。
            val localTodayTask = dailyTaskSnapshotStore.getToday(childId)
            val todayTaskResult = async {
                characterRepository.getOrCreateTodayCharacters(
                    childId = childId,
                    preferredCharacterIds = localTodayTask?.characters.orEmpty().map { it.id }
                )
            }
            val recognized = recognizedResult.await()
            val literacy = literacyResult.await()
            val todayTasks = todayTaskResult.await()

            todayTasks.onSuccess { tasks ->
                Log.i(
                    DAILY_SNAPSHOT_LOG_TAG,
                    "当天任务快照已加载：${tasks.size} 个，其中 ${tasks.count { it.learnedAt != null }} 个已完成"
                )
            }.onFailure { error ->
                // 不阻塞离线使用，但必须保留原因，避免服务端快照失败后静默变更当天选字。
                Log.w(DAILY_SNAPSHOT_LOG_TAG, "当天任务快照加载失败，已回退本机缓存", error)
            }

            if (recognized.isSuccess || literacy.isSuccess || todayTasks.isSuccess) {
                val todayTask = if (todayTasks.isSuccess) {
                    todayTasks.getOrNull()
                        .orEmpty()
                        .takeIf { it.isNotEmpty() }
                        ?.let { dailyTaskSnapshotStore.replaceToday(childId, it) }
                } else {
                    // 数据库快照暂时不可达时，保持原有本机当天任务，避免离线时换题。
                    dailyTaskSnapshotStore.getOrCreate(
                        childId,
                        literacy.getOrNull().orEmpty().filterNot { it.isFullyLearned() }
                    )
                }
                _uiState.value = LiteracyHomeUiState(
                    isLoading = false,
                    dataVersion = _uiState.value.dataVersion + 1,
                    // 新字优先：孩子进入首页后先看到当天待认识的字，再看到复习字。
                    groups = todayTask?.toLearningGroup().orEmpty() +
                        recognized.getOrNull().orEmpty().toKnownGroups()
                )
            } else {
                // 两个数据源都不可用时，保留已展示的数据，避免请求失败清空首页。
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 取今天之前最近的三个“收录日期”，而不是固定取前若干个字。
     *
     * 同一天收录的字必须完整保留，因此分页读取到第四个日期出现（或没有更多数据）
     * 才能确定第三个日期的字已全部拿到。
     */
    private suspend fun loadRecentRecognizedCharacters(childId: String): Result<List<RecognizedCharacter>> {
        val characters = mutableListOf<RecognizedCharacter>()
        var offset = 0L

        while (true) {
            val pageResult = recognizedCharacterRepository.getRecognizedCharacters(
                childId = childId,
                offset = offset,
                limit = RECOGNIZED_CHARACTER_PAGE_SIZE,
                recognizedBefore = todayStartInChina().toString()
            )
            val page = pageResult.getOrElse { return Result.failure(it) }
            characters += page

            val dates = characters.mapNotNull(RecognizedCharacter::recognizedDateInChina).distinct()
            if (dates.size > RECENT_RECOGNIZED_DATE_LIMIT || page.size < RECOGNIZED_CHARACTER_PAGE_SIZE) {
                return Result.success(characters.filter { it.recognizedDateInChina() in dates.take(RECENT_RECOGNIZED_DATE_LIMIT) })
            }
            offset += page.size
        }
    }
}

private fun ChildLiteracyCharacter.isFullyLearned(): Boolean = learnedAt != null

private fun todayStartInChina() = LocalDate.now(CHINA_ZONE).atStartOfDay(CHINA_ZONE).toInstant()

private val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private const val DAILY_SNAPSHOT_LOG_TAG = "LiteracyDailySnapshot"
private const val RECOGNIZED_CHARACTER_PAGE_SIZE = 100L
private const val RECENT_RECOGNIZED_DATE_LIMIT = 3

private fun List<RecognizedCharacter>.toKnownGroups(): List<LiteracyCharacterGroup> =
    groupBy { it.recognizedDateInChina() }
        .entries
        .sortedByDescending { it.key }
        .take(RECENT_RECOGNIZED_DATE_LIMIT)
        .mapIndexed { index, (date, characters) ->
        LiteracyCharacterGroup(
            type = LiteracyGroupType.KNOWN,
            groupNumber = index + 1,
            recognizedDate = date,
            characters = characters.map { it.character },
            recognizedCharacters = characters,
            recognizedCharacterRequiredReadings = (3 - index).coerceAtLeast(1)
        )
    }

/** Supabase 时间戳统一换算成中国日期；异常格式不应影响其余已认识字加载。 */
private fun RecognizedCharacter.recognizedDateInChina(): LocalDate? = recognizedAt?.let { value ->
    runCatching { OffsetDateTime.parse(value).atZoneSameInstant(CHINA_ZONE).toLocalDate() }
        .recoverCatching { LocalDate.parse(value.take(10)) }
        .getOrNull()
}

private fun DailyLiteracyTaskSnapshot.toLearningGroup(): List<LiteracyCharacterGroup> =
    characters.takeIf { it.isNotEmpty() }?.let { characters ->
        listOf(
            LiteracyCharacterGroup(
                type = LiteracyGroupType.TO_LEARN,
                groupNumber = 1,
                characters = characters.map { it.character },
                learningCharacters = characters,
                completedCharacterIds = completedCharacterIds
            )
        )
    }.orEmpty()
