package com.lemonkids.kidliteracy.feature.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.auth.SessionRecoveryCoordinator
import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.repository.ChildLiteracyCharacterRepository
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

data class PendingCharacter(val id: String, val character: String)

data class PhoneticAsset(
    val id: String,
    val itemType: String,
    val itemIndex: Int,
    val text: String,
    val tokens: List<String?>,
    val status: String,
    val lastError: String?
)

data class PendingPhoneticDetail(
    val character: PendingCharacter,
    val assets: List<PhoneticAsset>
)

data class PendingCharactersUiState(
    val isLoading: Boolean = true,
    val characters: List<PendingCharacter> = emptyList(),
    val errorMessage: String? = null,
    val isLoadingPhonetics: Boolean = false,
    val phoneticDetail: PendingPhoneticDetail? = null,
    val phoneticErrorMessage: String? = null,
    val savingAssetId: String? = null
)

/** 全量读取仍待学习的汉字；详情中的词句只读，允许家长修正腾讯数字拼音。 */
@HiltViewModel
class PendingCharactersViewModel @Inject constructor(
    private val characterRepository: ChildLiteracyCharacterRepository,
    private val supabase: SupabaseClient,
    private val sessionRecoveryCoordinator: SessionRecoveryCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingCharactersUiState())
    val uiState: StateFlow<PendingCharactersUiState> = _uiState.asStateFlow()
    private val auth get() = supabase.pluginManager.getPlugin(Auth)
    private val json = Json { ignoreUnknownKeys = true }

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
                            .map { PendingCharacter(it.id, it.character) }
                            .filter { it.id.isNotBlank() && it.character.isNotBlank() }
                            .distinctBy(PendingCharacter::character)
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

    fun showPhonetics(character: PendingCharacter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingPhonetics = true,
                phoneticDetail = null,
                phoneticErrorMessage = null
            )
            loadPhoneticAssets(character)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPhonetics = false,
                        phoneticDetail = detail
                    )
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

    private suspend fun loadPhoneticAssets(character: PendingCharacter): Result<PendingPhoneticDetail> = runCatching {
        val response = request(
            """{"action":"get_phonetic_assets","literacyCharacterId":"${character.id.jsonEscape()}","contentSource":"task"}"""
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
        PendingPhoneticDetail(character.copy(character = response["character"]?.jsonPrimitive?.contentOrNull ?: character.character), assets)
    }

    private suspend fun saveAsset(assetId: String, tokens: List<String?>): Result<Unit> = runCatching {
        val serializedTokens = tokens.joinToString(prefix = "[", postfix = "]") { token ->
            token?.let { "\"${it.jsonEscape()}\"" } ?: "null"
        }
        request("""{"action":"save_phonetic_asset","assetId":"${assetId.jsonEscape()}","phonemeTokens":$serializedTokens}""")
        Unit
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

private fun ChildLiteracyCharacter.isFullyLearned(): Boolean = learnedAt != null

private fun String.jsonEscape(): String = buildString {
    this@jsonEscape.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
