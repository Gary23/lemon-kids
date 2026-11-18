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
                // 保留全部原始记录 ID；同字记录在界面合并展示，删除时一并清理。
                _uiState.value = HelpedCharactersUiState(contents = contents)
            }.onFailure { error ->
                _uiState.value = HelpedCharactersUiState(
                    errorMessage = error.message ?: "帮助内容加载失败，请稍后重试"
                )
            }
        }
    }

    /** 删除当前孩子这些求助记录；逐条删除，部分失败时保留未删除记录供重试。 */
    fun delete(userId: String, contentIds: List<String>) {
        val ids = contentIds.distinct().filter { it.isNotBlank() }
        if (userId.isBlank() || ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                deletingContentIds = _uiState.value.deletingContentIds + ids,
                deleteErrorMessage = null
            )
            val failedIds = mutableListOf<String>()
            var lastError: Throwable? = null
            ids.forEach { contentId ->
                runCatching { deleteRow(userId, contentId) }
                    .onSuccess { removeContents(setOf(contentId)) }
                    .onFailure {
                        failedIds += contentId
                        lastError = it
                    }
            }
            _uiState.value = _uiState.value.copy(
                deletingContentIds = _uiState.value.deletingContentIds - ids.toSet(),
                deleteErrorMessage = lastError?.let {
                    "${failedIds.size} 条帮助记录删除失败，请重试（${it.message ?: "网络异常"}）"
                }
            )
        }
    }

    suspend fun deleteAfterRecognized(userId: String, contentIds: List<String>): Result<Unit> {
        val ids = contentIds.distinct().filter { it.isNotBlank() }
        return runCatching {
            require(userId.isNotBlank() && ids.isNotEmpty())
            val failedIds = mutableListOf<String>()
            var lastError: Throwable? = null
            ids.forEach { contentId ->
                runCatching { deleteRow(userId, contentId) }
                    .onSuccess { removeContents(setOf(contentId)) }
                    .onFailure {
                        failedIds += contentId
                        lastError = it
                    }
            }
            if (failedIds.isNotEmpty()) {
                throw IllegalStateException(
                    "${failedIds.size} 条帮助记录清理失败，可重试（${lastError?.message ?: "网络异常"}）",
                    lastError
                )
            }
            _uiState.value = _uiState.value.copy(deleteErrorMessage = null)
        }
    }

    private suspend fun deleteRow(userId: String, contentId: String) {
        postgrest.from("child_literacy_character_help_requests").delete {
            filter {
                eq("id", contentId)
                eq("child_id", userId)
            }
        }
    }

    private fun removeContents(ids: Set<String>) {
        _uiState.value = _uiState.value.copy(
            contents = _uiState.value.contents.filterNot { it.id in ids }
        )
    }
}
