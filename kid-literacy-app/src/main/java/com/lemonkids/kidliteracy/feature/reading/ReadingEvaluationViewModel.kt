package com.lemonkids.kidliteracy.feature.reading

import androidx.lifecycle.ViewModel
import android.util.Log
import com.lemonkids.shared.auth.SessionRecoveryCoordinator
import com.lemonkids.shared.model.ChildLiteracyCharacter
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

enum class ReadingContentSource(val wireValue: String) {
    TASK("task"),
    RECOGNIZED("recognized")
}

data class ReadingTarget(
    val literacyCharacterId: String,
    val targetType: String,
    val displayText: String,
    /** 当前点读对象自己的预生成音频；为空时后续播放器会回退系统 TTS。 */
    val audioUrl: String = "",
    /** 与 URL 一起作为缓存失效键，避免升级后播放旧音频。 */
    val audioVersion: String? = null,
    /** 当前字、词或句在该认字任务中的固定顺序，仅用于本地练习次数隔离。 */
    val itemOrder: Int = 0,
    val sentenceText: String? = null,
    // 词组朗读时指定本轮要评测的一个词；云端会校验它确实属于该识字任务。
    val wordText: String? = null,
    /** 内容来自认字任务或独立的已认识字表。 */
    val contentSource: ReadingContentSource = ReadingContentSource.TASK,
    /**
     * 已认识字按入库日期分组后的主字朗读次数；词语仍使用既定的一次规则。
     * null 表示使用默认规则，避免影响待认识字和其他已有调用方。
     */
    val characterRequiredReadings: Int? = null
)

/** 一份凭证可在有效期内用于多个字、词、句的腾讯实时评测。 */
data class TencentEvaluationCredentials(
    val appId: Int,
    val secretId: String,
    val secretKey: String,
    val token: String,
    /** 腾讯临时凭证的过期时间（Unix 秒），用于避免复用即将失效的预热结果。 */
    val expiresAtEpochSeconds: Long
)

data class HelpPronunciation(
    val character: String,
    val contextText: String
)

data class GeneratedLiteracyTask(
    val character: String,
    val words: List<GeneratedLiteracyExample>,
    val sentence: GeneratedLiteracyExample
)

/** 智能生成和人工确认阶段使用的词、句及其逐字拼音。 */
data class GeneratedLiteracyExample(val text: String)

/** 由服务端按已授权内容和已就绪音素资产组装的腾讯评测参数。 */
data class PreparedEvaluation(val refText: String, val textMode: Int)

data class LiteracyTasksPreview(
    val tasks: List<GeneratedLiteracyTask>,
    val knownCharacters: List<String>,
    val skippedExistingCharacters: List<String>
)

data class SavedLiteracyTasks(
    val createdCharacters: List<String>,
    val knownCharacters: List<String>,
    val skippedExistingCharacters: List<String>
)

@HiltViewModel
class ReadingEvaluationViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionRecoveryCoordinator: SessionRecoveryCoordinator
) : ViewModel() {
    private val auth get() = supabase.pluginManager.getPlugin(Auth)
    private val json = Json { ignoreUnknownKeys = true }
    private val credentialMutex = Mutex()
    private var credentialCache: TencentEvaluationCredentials? = null
    private var pendingCredential: CompletableDeferred<Result<TencentEvaluationCredentials>>? = null

    /**
     * 进入认字页时只领取一份短期凭证。字、词、句的参考文本来自当前页面已加载的
     * 教学数据；真正录音时仍各自向腾讯发起独立评测。
     */
    suspend fun beginPageCredentials(): Result<TencentEvaluationCredentials> {
        credentialMutex.withLock { credentialCache = null }
        return credentialsForEvaluation()
    }

    /** 剩余有效期不足 5 分钟即刷新，即从签发起最多约 25 分钟复用旧凭证。 */
    suspend fun credentialsForEvaluation(): Result<TencentEvaluationCredentials> {
        val now = System.currentTimeMillis() / 1_000
        var requestOwner = false
        val deferred = credentialMutex.withLock {
            credentialCache?.takeIf { it.expiresAtEpochSeconds > now + CREDENTIAL_REFRESH_WINDOW_SECONDS }
                ?.let { return Result.success(it) }
            credentialCache = null
            pendingCredential ?: CompletableDeferred<Result<TencentEvaluationCredentials>>().also {
                pendingCredential = it
                requestOwner = true
            }
        }

        if (!requestOwner) return deferred.await()

        val result = issueCredentials()
        credentialMutex.withLock {
            if (result.isSuccess) credentialCache = result.getOrThrow()
            pendingCredential = null
        }
        deferred.complete(result)
        return result
    }

    private suspend fun issueCredentials(): Result<TencentEvaluationCredentials> = runCatching {
        val response = request(
            """{"action":"issue_credentials"}"""
        ).jsonObject
        val credentials = response.requiredObject("credentials")
        TencentEvaluationCredentials(
            appId = response.requiredString("appId").toInt(),
            secretId = credentials.requiredString("secretId"),
            secretKey = credentials.requiredString("secretKey"),
            token = credentials.requiredString("token"),
            expiresAtEpochSeconds = credentials.requiredString("expiredTime").toLong()
        ).also { Log.d("ReadingEvaluation", "已领取认字页通用评测凭证") }
    }

    suspend fun recordHelpRequest(target: ReadingTarget, character: Char, characterIndex: Int): Result<HelpPronunciation> = runCatching {
        val response = request(
            """{"action":"record_help_request","literacyCharacterId":"${target.literacyCharacterId.jsonEscape()}","targetType":"${target.targetType.jsonEscape()}","contentSource":"${target.contentSource.wireValue}","character":"${character.toString().jsonEscape()}","characterIndex":$characterIndex${target.sentenceText?.let { ",\"sentenceText\":\"${it.jsonEscape()}\"" }.orEmpty()}${target.wordText?.let { ",\"wordText\":\"${it.jsonEscape()}\"" }.orEmpty()}}"""
        )
        val help = response.requiredObject("help")
        HelpPronunciation(
            character = help.requiredString("character"),
            contextText = help.requiredString("contextText")
        )
    }

    /**
     * 当前认字任务的所有本地练习次数达标后，按是否点读过主字决定收录位置。
     * 云函数负责确认任务归属、去重及写入 learned_at，客户端不直接写 Supabase。
     */
    suspend fun completeLiteracyCharacter(
        literacyCharacterId: String,
        hasCharacterAudioPointRead: Boolean
    ): Result<Unit> = runCatching {
        request(
            """{"action":"complete_literacy_character","literacyCharacterId":"${literacyCharacterId.jsonEscape()}","hasCharacterAudioPointRead":$hasCharacterAudioPointRead}"""
        )
        Unit
    }

    /**
     * 将一条“已认识的字”转入字库。云函数在写入字库成功后才删除该条字、词、句数据。
     */
    suspend fun archiveRecognizedCharacter(recognizedCharacterId: String): Result<Unit> = runCatching {
        request(
            """{"action":"archive_recognized_character","recognizedCharacterId":"${recognizedCharacterId.jsonEscape()}"}"""
        )
        Unit
    }

    suspend fun prepareEvaluation(target: ReadingTarget, repeatCount: Int = 1): Result<PreparedEvaluation> = runCatching {
        val response = request(
            """{"action":"prepare_evaluation","literacyCharacterId":"${target.literacyCharacterId.jsonEscape()}","targetType":"${target.targetType.jsonEscape()}","contentSource":"${target.contentSource.wireValue}","repeatCount":$repeatCount${target.sentenceText?.let { ",\"sentenceText\":\"${it.jsonEscape()}\"" }.orEmpty()}${target.wordText?.let { ",\"wordText\":\"${it.jsonEscape()}\"" }.orEmpty()}}"""
        ).requiredObject("evaluation")
        PreparedEvaluation(response.requiredString("refText"), response.requiredString("textMode").toInt())
    }

    /** 只生成可编辑预览，不会向 Supabase 写入任何待认识任务。 */
    suspend fun previewLiteracyTasks(characters: String): Result<LiteracyTasksPreview> = runCatching {
        val response = request(
            """{"action":"preview_literacy_tasks","characters":"${characters.jsonEscape()}"}""",
            readTimeoutMillis = LITERACY_GENERATION_READ_TIMEOUT_MILLIS
        )
        val preview = response.requiredObject("preview")
        val tasks = preview["tasks"]
            ?.jsonArray
            ?.map { item ->
                val task = item.jsonObject
                GeneratedLiteracyTask(
                    character = task.requiredString("character"),
                    words = task["words"]?.jsonArray?.mapNotNull { item ->
                        item.jsonObjectOrNull()?.toGeneratedLiteracyExample()
                    }.orEmpty(),
                    sentence = task.requiredObject("sentence").toGeneratedLiteracyExample()
                )
            }
            .orEmpty()
        val knownCharacters = preview["knownCharacters"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        val skippedExistingCharacters = preview["skippedExistingCharacters"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        LiteracyTasksPreview(tasks, knownCharacters, skippedExistingCharacters)
    }

    /** 家长确认或修改预览后，服务端校验内容及最新字库，再写入待认识任务。 */
    suspend fun saveLiteracyTasks(
        characters: String,
        tasks: List<GeneratedLiteracyTask>
    ): Result<SavedLiteracyTasks> = runCatching {
        val serializedTasks = tasks.joinToString(prefix = "[", postfix = "]") { task ->
            val words = task.words.joinToString(prefix = "[", postfix = "]") { word ->
                word.toRequestJson()
            }
            """{"character":"${task.character.jsonEscape()}","words":$words,"sentence":${task.sentence.toRequestJson()}}"""
        }
        val response = request(
            """{"action":"save_literacy_tasks","characters":"${characters.jsonEscape()}","items":$serializedTasks}"""
        )
        val generated = response.requiredObject("generated")
        val createdCharacters = generated["created"]
            ?.jsonArray
            ?.mapNotNull { it.jsonObject["character"]?.jsonPrimitive?.content }
            .orEmpty()
        val knownCharacters = generated["knownCharacters"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        val skippedExistingCharacters = generated["skippedExistingCharacters"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        SavedLiteracyTasks(createdCharacters, knownCharacters, skippedExistingCharacters)
    }

    private suspend fun request(
        body: String,
        readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS
    ): JsonObject = withContext(Dispatchers.IO) {
        val accessToken = auth.currentSessionOrNull()?.accessToken
            ?: run {
                sessionRecoveryCoordinator.requireRecovery()
                error("登录凭证需要恢复")
            }
        val connection = (URL(FUNCTION_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = readTimeoutMillis
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

    private fun JsonObject.requiredObject(name: String) = this[name]?.jsonObject ?: error("云函数缺少 $name")
    private fun JsonObject.requiredString(name: String) = this[name]?.jsonPrimitive?.content ?: error("云函数缺少 $name")

    private companion object {
        // 函数 URL 不是密钥；腾讯密钥仅由云函数下发短期 STS 凭证。
        const val FUNCTION_URL = "https://1255826305-i7udpf1i9o.ap-beijing.tencentscf.com"
        const val CREDENTIAL_REFRESH_WINDOW_SECONDS = 5 * 60L
        const val DEFAULT_READ_TIMEOUT_MILLIS = 15_000
        // SCF 的执行上限为 90 秒；额外保留 10 秒给网络传输和响应回传。
        const val LITERACY_GENERATION_READ_TIMEOUT_MILLIS = 100_000
    }
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject.toGeneratedLiteracyExample() = GeneratedLiteracyExample(
    text = this["text"]?.jsonPrimitive?.content ?: error("生成内容缺少 text")
)

private fun GeneratedLiteracyExample.toRequestJson(): String {
    return """{"text":"${text.jsonEscape()}"}"""
}

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
