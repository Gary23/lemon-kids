package com.lemonkids.kidliteracy.feature.recognized

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.RecognizedCharacter
import com.lemonkids.shared.repository.RecognizedCharacterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecognizedCharactersUiState(
    val isLoading: Boolean = true,
    val characters: List<RecognizedCharacter> = emptyList(),
    val errorMessage: String? = null
)

/** “已认识的字”页直接读取 recognized_characters，不使用演示学习卡数据。 */
@HiltViewModel
class RecognizedCharactersViewModel @Inject constructor(
    private val recognizedCharacterRepository: RecognizedCharacterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecognizedCharactersUiState())
    val uiState: StateFlow<RecognizedCharactersUiState> = _uiState.asStateFlow()

    private val pageSize = 100L

    fun load(childId: String) {
        if (childId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val allCharacters = mutableListOf<RecognizedCharacter>()
            var offset = 0L
            var error: Throwable? = null

            do {
                val page = recognizedCharacterRepository
                    .getRecognizedCharacters(childId, offset, pageSize)
                    .onSuccess {
                        allCharacters += it
                        offset += it.size
                    }
                    .onFailure { error = it }
                    .getOrNull()
            } while (error == null && page?.size == pageSize.toInt())

            _uiState.value = if (error == null) {
                RecognizedCharactersUiState(
                    isLoading = false,
                    // 数据库已有唯一约束；客户端仍做一次保护，避免列表 key 重复。
                    characters = allCharacters.distinctBy { it.id.ifBlank { it.character } }
                )
            } else {
                _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error?.message ?: "加载已认识的字失败，请稍后重试"
                )
            }
        }
    }

    /** 存库成功后立即从当前页移除，避免等待下一次进入页面才刷新。 */
    fun removeCharacter(recognizedCharacterId: String) {
        _uiState.value = _uiState.value.copy(
            characters = _uiState.value.characters.filterNot { it.id == recognizedCharacterId }
        )
    }
}
