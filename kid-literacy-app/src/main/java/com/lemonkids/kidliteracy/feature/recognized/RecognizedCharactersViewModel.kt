package com.lemonkids.kidliteracy.feature.recognized

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.kidliteracy.feature.pending.PendingCharacter
import com.lemonkids.kidliteracy.feature.pending.PendingPhoneticDetail
import com.lemonkids.kidliteracy.feature.pending.PhoneticAsset
import com.lemonkids.shared.auth.SessionRecoveryCoordinator
import com.lemonkids.shared.model.RecognizedCharacter
import com.lemonkids.shared.repository.RecognizedCharacterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class RecognizedCharactersUiState(
    val isLoading: Boolean = true,
    val characters: List<RecognizedCharacter> = emptyList(),
    val errorMessage: String? = null,
    val isLoadingPhonetics: Boolean = false,
    val phoneticDetail: PendingPhoneticDetail? = null,
    val phoneticErrorMessage: String? = null,
    val savingAssetId: String? = null,
    val toppingCharacterId: String? = null,
    val topNotice: String? = null
)

/** “已认识的字”页直接读取 recognized_characters，并复用待认识页的音素编辑弹窗。 */
@HiltViewModel
class RecognizedCharactersViewModel @Inject constructor(
    private val recognizedCharacterRepository: RecognizedCharacterRepository,
    private val supabase: SupabaseClient,
    private val sessionRecoveryCoordinator: SessionRecoveryCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecognizedCharactersUiState())
    val uiState: StateFlow<RecognizedCharactersUiState> = _uiState.asStateFlow()
    private val auth get() = supabase.pluginManager.getPlugin(Auth)
    private val json = Json { ignoreUnknownKeys = true }
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
                _uiState.value.copy(
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

    /** 加载“已认识”来源的词句音素，界面直接复用待认识页的编辑弹窗。 */
    fun showPhonetics(character: RecognizedCharacter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingPhonetics = true,
                phoneticDetail = null,
                phoneticErrorMessage = null
            )
            loadPhoneticAssets(character)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(isLoadingPhonetics = false, phoneticDetail = detail)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPhonetics = false,
                        phoneticErrorMessage = error.message ?: "音素资产加载失败，请稍后重试"
                    )
                }
        }
    }

    fun dismissPhonetics() {
        _uiState.value = _uiState.value.copy(
            isLoadingPhonetics = false,
            phoneticDetail = null,
            phoneticErrorMessage = null,
            savingAssetId = null
        )
    }

    fun savePhoneticAsset(asset: PhoneticAsset, tokens: List<String?>) {
        val validationError = validateTokens(asset.text, tokens)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(phoneticErrorMessage = validationError)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingAssetId = asset.id, phoneticErrorMessage = null)
            saveAsset(asset.id, tokens)
                .onSuccess {
                    val detail = _uiState.value.phoneticDetail
                    _uiState.value = _uiState.value.copy(
                        savingAssetId = null,
                        phoneticDetail = detail?.copy(assets = detail.assets.map {
                            if (it.id == asset.id) it.copy(tokens = tokens, status = "ready", lastError = null) else it
                        })
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        savingAssetId = null,
                        phoneticErrorMessage = error.message ?: "音素保存失败，请稍后重试"
                    )
                }
        }
    }

    /** 将收录时间改为当前时刻，使该字重新进入首页三天学习周期。 */
    fun topCharacter(character: RecognizedCharacter) {
        if (character.id.isBlank() || _uiState.value.toppingCharacterId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(toppingCharacterId = character.id, topNotice = null)
            topRecognizedCharacter(character.id)
                .onSuccess { recognizedAt ->
                    _uiState.value = _uiState.value.copy(
                        toppingCharacterId = null,
                        characters = _uiState.value.characters.map {
                            if (it.id == character.id) it.copy(recognizedAt = recognizedAt) else it
                        },
                        topNotice = "${character.character} 已置顶，今天起重新学习 3 天"
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        toppingCharacterId = null,
                        topNotice = error.message ?: "置顶失败，请稍后重试"
                    )
                }
        }
    }

    fun consumeTopNotice() {
        _uiState.value = _uiState.value.copy(topNotice = null)
    }

    private suspend fun loadPhoneticAssets(character: RecognizedCharacter): Result<PendingPhoneticDetail> = runCatching {
        val response = request(
            """{"action":"get_phonetic_assets","literacyCharacterId":"${character.id.jsonEscape()}","contentSource":"recognized"}"""
        )
        val assets = response["assets"]?.jsonArray.orEmpty().map { item ->
            val asset = item.jsonObject
            PhoneticAsset(
                id = asset.requiredString("id"),
                itemType = asset.requiredString("item_type"),
                itemIndex = asset["item_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                text = asset.requiredString("item_text"),
                tokens = asset["phoneme_tokens"]?.jsonArray?.map { token ->
                    (token as? JsonPrimitive)?.contentOrNull
                }.orEmpty(),
                status = asset.requiredString("status"),
                lastError = asset["last_error"]?.jsonPrimitive?.contentOrNull
            )
        }.sortedWith(compareBy<PhoneticAsset> { if (it.itemType == "word") 0 else 1 }.thenBy { it.itemIndex })
        PendingPhoneticDetail(
            PendingCharacter(character.id, response["character"]?.jsonPrimitive?.contentOrNull ?: character.character),
            assets
        )
    }

    private suspend fun saveAsset(assetId: String, tokens: List<String?>): Result<Unit> = runCatching {
        val serializedTokens = tokens.joinToString(prefix = "[", postfix = "]") { token ->
            token?.let { "\"${it.jsonEscape()}\"" } ?: "null"
        }
        request("""{"action":"save_phonetic_asset","assetId":"${assetId.jsonEscape()}","phonemeTokens":$serializedTokens}""")
        Unit
    }

    private suspend fun topRecognizedCharacter(recognizedCharacterId: String): Result<String> = runCatching {
        val response = request(
            """{"action":"top_recognized_character","recognizedCharacterId":"${recognizedCharacterId.jsonEscape()}"}"""
        )
        response["topped"]?.jsonObject?.get("recognizedAt")?.jsonPrimitive?.content
            ?: error("云函数未返回新的收录时间")
    }

    private fun validateTokens(text: String, tokens: List<String?>): String? {
        val characters = text.filter { it in '\u4E00'..'\u9FFF' }
        if (tokens.size != characters.length) return "音素数量与“$text”中的汉字数量不一致"
        tokens.forEachIndexed { index, token ->
            if (token != null && !PHONEME_PATTERN.matches(token.trim())) {
                return "第 ${index + 1} 个汉字“${characters[index]}”的拼音必须是字母加 1 至 4 调号，例如 zhang3"
            }
        }
        return null
    }

    private suspend fun request(body: String): JsonObject = withContext(Dispatchers.IO) {
        val accessToken = auth.currentSessionOrNull()?.accessToken ?: run {
            sessionRecoveryCoordinator.requireRecovery()
            error("登录凭证需要恢复")
        }
        val connection = (URL(FUNCTION_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        val result = json.parseToJsonElement(responseText).jsonObject
        if (status !in 200..299) error(result["error"]?.jsonPrimitive?.content ?: "云函数请求失败（$status）")
        result
    }

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.content ?: error("云函数缺少 $name")

    private companion object {
        const val FUNCTION_URL = "https://1255826305-i7udpf1i9o.ap-beijing.tencentscf.com"
        val PHONEME_PATTERN = Regex("^[a-zv]+[1-4]$")
    }
}

private fun String.jsonEscape(): String = buildString {
    this@jsonEscape.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '\"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
