package com.lemonkids.kidliteracy.feature.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class HelpedContent(
    val id: String,
    @SerialName("target_text") val targetText: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("requested_character") val requestedCharacter: String? = null,
    @SerialName("character_index") val characterIndex: Int? = null,
    @SerialName("created_at") val requestedAt: String
)

data class HelpedCharactersUiState(
    val isLoading: Boolean = false,
    val contents: List<HelpedContent> = emptyList(),
    val errorMessage: String? = null,
    val deletingContentIds: Set<String> = emptySet(),
    val deleteErrorMessage: String? = null
)

@HiltViewModel
class HelpedCharactersViewModel @Inject constructor(
    private val supabase: SupabaseClient
) : ViewModel() {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val _uiState = MutableStateFlow(HelpedCharactersUiState())
    val uiState: StateFlow<HelpedCharactersUiState> = _uiState.asStateFlow()

    fun load(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = HelpedCharactersUiState(isLoading = true)
            runCatching {
                postgrest.from("child_literacy_character_help_requests").select {
                    filter { eq("child_id", userId) }
                    order("created_at", Order.DESCENDING)
                }.decodeList<HelpedContent>()
            }.onSuccess { contents ->
                _uiState.value = HelpedCharactersUiState(
                    // 唯一约束兜底；历史数据也在界面上防御性去重。
                    contents = contents.distinctBy {
                        listOf(it.targetType, it.targetText, it.requestedCharacter, it.characterIndex)
                    }
                )
            }.onFailure { error ->
                _uiState.value = HelpedCharactersUiState(
                    errorMessage = error.message ?: "帮助内容加载失败，请稍后重试"
                )
            }
        }
    }

    /** 仅删除当前孩子自己的这条求助记录；成功后立即更新列表。 */
    fun delete(userId: String, contentId: String) {
        if (userId.isBlank() || contentId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                deletingContentIds = _uiState.value.deletingContentIds + contentId,
                deleteErrorMessage = null
            )
            runCatching {
                postgrest.from("child_literacy_character_help_requests").delete {
                    filter {
                        eq("id", contentId)
                        eq("child_id", userId)
                    }
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    contents = _uiState.value.contents.filterNot { it.id == contentId },
                    deletingContentIds = _uiState.value.deletingContentIds - contentId
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    deletingContentIds = _uiState.value.deletingContentIds - contentId,
                    deleteErrorMessage = error.message ?: "删除失败，请稍后重试"
                )
            }
        }
    }
}
