package com.lemonkids.kidliteracy.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.KnownCharacter
import com.lemonkids.shared.repository.KnownCharacterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = false,
    val isPreparingExport: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLocalData: Boolean = false,
    val characters: List<KnownCharacter> = emptyList(),
    val refreshMessage: String? = null,
    val exportMessage: String? = null,
    /** 非空时由界面打开系统保存文件窗口，内容只包含汉字及分隔符。 */
    val pendingExportContent: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val knownCharacterRepository: KnownCharacterRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var loadedUserId: String? = null
    private val pageSize = 100L
    private val prefs by lazy {
        context.getSharedPreferences("lemonkids_library_cache", Context.MODE_PRIVATE)
    }

    /** 页面仅读取本地缓存；首次使用或缓存被清除时不自动访问网络。 */
    fun load(userId: String) {
        if (userId.isBlank() || loadedUserId == userId) return
        loadedUserId = userId
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true)
            val cachedCharacters = withContext(Dispatchers.IO) { readCache(userId) }
            _uiState.value = LibraryUiState(
                characters = cachedCharacters ?: emptyList(),
                hasLocalData = cachedCharacters != null
            )
        }
    }

    /** 每次点击都从数据库分页读取全部数据，并覆盖本地缓存。 */
    fun refresh(userId: String) {
        val current = _uiState.value
        if (userId.isBlank() || current.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = current.copy(isRefreshing = true, refreshMessage = null)
            val allCharacters = mutableListOf<KnownCharacter>()
            var offset = 0L
            var failure: Throwable? = null

            do {
                val result = knownCharacterRepository.getKnownCharacters(userId, offset, pageSize)
                result.onSuccess { page ->
                    allCharacters += page
                    offset += page.size
                }.onFailure { failure = it }
                val page = result.getOrNull()
            } while (failure == null && page != null && page.size == pageSize.toInt())

            _uiState.value = if (failure != null) {
                current.copy(
                    isRefreshing = false,
                    refreshMessage = failure?.message ?: "字库更新失败，请稍后重试"
                )
            } else {
                // 防御性去重：即使分页边界或上游数据异常返回重复记录，界面也不会出现重复 key。
                val uniqueCharacters = allCharacters.distinctBy { it.character }
                withContext(Dispatchers.IO) { writeCache(userId, uniqueCharacters) }
                current.copy(
                    isRefreshing = false,
                    hasLocalData = true,
                    characters = uniqueCharacters,
                    refreshMessage = "字库已更新，共 ${uniqueCharacters.size} 个字"
                )
            }
        }
    }

    /**
     * 导出始终使用本地缓存，避免额外的数据库访问。
     *
     * 序号从 1 开始且包含首尾；characters 的顺序就是最近一次查询并在页面展示的顺序。
     */
    fun prepareExport(startText: String, endText: String) {
        val current = _uiState.value
        if (current.isPreparingExport) return
        if (current.characters.isEmpty()) {
            _uiState.value = current.copy(exportMessage = "本地没有字库数据可以导出")
            return
        }
        val start = startText.toIntOrNull()
        val end = endText.toIntOrNull()
        when {
            start == null || end == null -> {
                _uiState.value = current.copy(exportMessage = "请输入两个正整数序号")
                return
            }
            start < 1 || end < 1 -> {
                _uiState.value = current.copy(exportMessage = "导出序号必须从 1 开始")
                return
            }
            end <= start -> {
                _uiState.value = current.copy(exportMessage = "结束序号必须大于开始序号")
                return
            }
            start > current.characters.size -> {
                _uiState.value = current.copy(exportMessage = "开始序号超出字库范围（共 ${current.characters.size} 个字）")
                return
            }
        }
        // 结束序号超过现有字数时，导出至最后一个字，避免遗漏用户可下载的数据。
        val actualEnd = end.coerceAtMost(current.characters.size)
        _uiState.value = current.copy(
            isPreparingExport = false,
            exportMessage = null,
            pendingExportContent = current.characters
                .subList(start - 1, actualEnd)
                .joinToString(separator = "、") { it.character }
        )
    }

    fun consumePendingExport() {
        _uiState.value = _uiState.value.copy(pendingExportContent = null)
    }

    fun reportExportResult(errorMessage: String? = null) {
        _uiState.value = _uiState.value.copy(
            exportMessage = errorMessage ?: "字库已导出到本地"
        )
    }

    private fun readCache(userId: String): List<KnownCharacter>? {
        val cacheKey = "characters_$userId"
        val raw = prefs.getString(cacheKey, null) ?: return null
        return runCatching {
            Json.decodeFromString<List<KnownCharacter>>(raw).distinctBy { it.character }
        }
            .getOrElse {
                prefs.edit().remove(cacheKey).apply()
                null
            }
    }

    private fun writeCache(userId: String, characters: List<KnownCharacter>) {
        prefs.edit()
            .putString("characters_$userId", Json.encodeToString(characters))
            .apply()
    }
}
