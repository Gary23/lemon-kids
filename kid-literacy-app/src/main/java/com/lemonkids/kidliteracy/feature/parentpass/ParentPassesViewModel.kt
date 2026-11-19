package com.lemonkids.kidliteracy.feature.parentpass

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

/** 点击“通过”前的星级快照；文本随快照保存，便于家长回看当时具体哪一项未满星。 */
@Serializable
data class ParentPassStarState(
    val text: String,
    val earned: Int,
    val required: Int
)

@Serializable
data class ParentPassStarSnapshot(
    val character: ParentPassStarState,
    val words: List<ParentPassStarState> = emptyList(),
    val sentences: List<ParentPassStarState> = emptyList()
)

@Serializable
data class ParentPassRecord(
    val id: String,
    @SerialName("character") val character: String,
    @SerialName("literacy_character_id") val literacyCharacterId: String,
    @SerialName("content_source") val contentSource: String,
    @SerialName("star_snapshot") val starSnapshot: ParentPassStarSnapshot,
    @SerialName("passed_at") val passedAt: String,
    @SerialName("undone_at") val undoneAt: String? = null
)

data class ParentPassesUiState(
    val isLoading: Boolean = false,
    val records: List<ParentPassRecord> = emptyList(),
    val errorMessage: String? = null
)

/** “我的 → 通过记录”只读展示服务端审计记录；写入始终由评测云函数完成。 */
@HiltViewModel
class ParentPassesViewModel @Inject constructor(
    private val supabase: SupabaseClient
) : ViewModel() {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val _uiState = MutableStateFlow(ParentPassesUiState(isLoading = true))
    val uiState: StateFlow<ParentPassesUiState> = _uiState.asStateFlow()

    fun load(childId: String) {
        if (childId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = ParentPassesUiState(isLoading = true)
            runCatching {
                postgrest.from("literacy_parent_pass_records").select {
                    filter { eq("child_id", childId) }
                    order("passed_at", Order.DESCENDING)
                }.decodeList<ParentPassRecord>()
            }.onSuccess { records ->
                _uiState.value = ParentPassesUiState(records = records)
            }.onFailure { error ->
                _uiState.value = ParentPassesUiState(
                    errorMessage = error.message ?: "通过记录加载失败，请稍后重试"
                )
            }
        }
    }

    /** 删除成功后立即移除本地项，避免额外请求造成列表闪动。 */
    fun removeRecord(recordId: String) {
        _uiState.value = _uiState.value.copy(records = _uiState.value.records.filterNot { it.id == recordId })
    }

    fun clearRecords() {
        _uiState.value = _uiState.value.copy(records = emptyList())
    }
}
