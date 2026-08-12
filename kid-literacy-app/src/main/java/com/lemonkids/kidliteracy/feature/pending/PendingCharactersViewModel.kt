package com.lemonkids.kidliteracy.feature.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.repository.ChildLiteracyCharacterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingCharactersUiState(
    val isLoading: Boolean = true,
    val characters: List<String> = emptyList(),
    val errorMessage: String? = null
)

/** 全量读取仍待学习的汉字；页面只使用主字，不暴露词语和句子内容。 */
@HiltViewModel
class PendingCharactersViewModel @Inject constructor(
    private val characterRepository: ChildLiteracyCharacterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingCharactersUiState())
    val uiState: StateFlow<PendingCharactersUiState> = _uiState.asStateFlow()

    fun load(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = PendingCharactersUiState(isLoading = true)
            characterRepository.getCharacters(userId)
                .onSuccess { characters ->
                    _uiState.value = PendingCharactersUiState(
                        isLoading = false,
                        characters = characters
                            .filterNot(ChildLiteracyCharacter::isFullyLearned)
                            .map(ChildLiteracyCharacter::character)
                            .filter(String::isNotBlank)
                            .distinct()
                    )
                }
                .onFailure { error ->
                    _uiState.value = PendingCharactersUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "待认识的字加载失败，请稍后重试"
                    )
                }
        }
    }
}

private fun ChildLiteracyCharacter.isFullyLearned(): Boolean = learnedAt != null
