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
import javax.inject.Inject

data class LiteracyHomeUiState(
    val isLoading: Boolean = true,
    val groups: List<LiteracyCharacterGroup> = emptyList()
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

    /**
     * 每次回到首页都重新查询已认识字；待认识字则只在当天首次加载时生成快照。
     * 当天刚完成的任务仍在快照中展示，所以在当天不同时显示其对应的已认识记录；
     * 次日快照过期后，这些字才会进入已认识区。
     */
    fun load(childId: String) {
        if (childId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val recognizedResult = async { recognizedCharacterRepository.getRecognizedCharacters(childId) }
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
                    groups = recognized
                        .getOrNull()
                        .orEmpty()
                        .excludeTodayTaskCharacters(todayTask)
                        .toKnownGroups() +
                        todayTask?.toLearningGroup().orEmpty()
                )
            } else {
                // 两个数据源都不可用时，保留已展示的数据，避免请求失败清空首页。
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

private fun ChildLiteracyCharacter.isFullyLearned(): Boolean = learnedAt != null

/**
 * `complete_literacy_character` 会立即创建已认识记录，但当天任务要以本地快照为准。
 * 仅按来源任务 ID 排除，避免把手工添加或历史已认识的同字误隐藏。
 */
private fun List<RecognizedCharacter>.excludeTodayTaskCharacters(
    todayTask: DailyLiteracyTaskSnapshot?
): List<RecognizedCharacter> {
    val todayTaskIds = todayTask?.characters?.map { it.id }?.toSet().orEmpty()
    return filterNot { it.sourceLiteracyCharacterId in todayTaskIds }
}

private fun List<RecognizedCharacter>.toKnownGroups(): List<LiteracyCharacterGroup> =
    take(24).chunked(8).mapIndexed { index, characters ->
        LiteracyCharacterGroup(
            type = LiteracyGroupType.KNOWN,
            groupNumber = index + 1,
            characters = characters.map { it.character },
            recognizedCharacters = characters
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
