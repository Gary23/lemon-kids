package com.lemonkids.kidliteracy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    /** 首页只展示汉字；完整认字数据用于进入字、词、句学习页。 */
    val characters: List<String>,
    /** 待认识分组进入学习页时所需的完整认字任务数据。 */
    val learningCharacters: List<ChildLiteracyCharacter> = emptyList(),
    /** 已认识分组进入学习页时所需的独立表数据。 */
    val recognizedCharacters: List<RecognizedCharacter> = emptyList(),
    /**
     * 已认识字复习时主字需要读对的次数。
     *
     * 首页按入库日期从近到远分成三组：最近 6 个字读 3 次，随后 6 个读 2 次，
     * 最后 6 个读 1 次。待认识字仍固定沿用自身的三次规则。
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

    /** 每次回到首页都重新查询昨天及更早收录的已认识字；待认识字只在当天首次加载时生成快照。 */
    fun load(childId: String) {
        if (childId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val recognizedResult = async {
                recognizedCharacterRepository.getRecognizedCharacters(
                    childId = childId,
                    recognizedBefore = todayStartInChina().toString()
                )
            }
            val literacyResult = async { characterRepository.getCharacters(childId) }
            val recognized = recognizedResult.await()
            val literacy = literacyResult.await()

            if (recognized.isSuccess || literacy.isSuccess) {
                val todayTask = dailyTaskSnapshotStore.getOrCreate(
                    childId,
                    literacy.getOrNull().orEmpty().filterNot { it.isFullyLearned() }
                )
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
}

private fun ChildLiteracyCharacter.isFullyLearned(): Boolean = learnedAt != null

private fun todayStartInChina() = LocalDate.now(CHINA_ZONE).atStartOfDay(CHINA_ZONE).toInstant()

private val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

private fun List<RecognizedCharacter>.toKnownGroups(): List<LiteracyCharacterGroup> =
    take(18).chunked(6).mapIndexed { index, characters ->
        LiteracyCharacterGroup(
            type = LiteracyGroupType.KNOWN,
            groupNumber = index + 1,
            characters = characters.map { it.character },
            recognizedCharacters = characters,
            recognizedCharacterRequiredReadings = (3 - index).coerceAtLeast(1)
        )
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
