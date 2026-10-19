@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lemonkids.kidliteracy

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.lemonkids.kidliteracy.ui.components.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import android.provider.Settings
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import com.lemonkids.kidliteracy.ui.components.GardenActionButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidliteracy.ui.components.GardenDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.lemonkids.kidliteracy.ui.theme.Background
import com.lemonkids.kidliteracy.ui.theme.Coral
import com.lemonkids.kidliteracy.ui.theme.CoralLight
import com.lemonkids.kidliteracy.ui.theme.EvaluationErrorRed
import com.lemonkids.kidliteracy.ui.theme.Ink
import com.lemonkids.kidliteracy.ui.theme.Leaf
import com.lemonkids.kidliteracy.ui.theme.LeafLight
import com.lemonkids.kidliteracy.ui.theme.Line
import com.lemonkids.kidliteracy.ui.theme.Sky
import com.lemonkids.kidliteracy.ui.theme.SkyLight
import com.lemonkids.kidliteracy.ui.theme.Wheat
import com.lemonkids.kidliteracy.ui.theme.WheatLight
import com.lemonkids.kidliteracy.feature.library.LibraryViewModel
import com.lemonkids.kidliteracy.feature.pending.PendingCharactersViewModel
import com.lemonkids.kidliteracy.feature.pending.PendingPhoneticDetail
import com.lemonkids.kidliteracy.feature.pending.PhoneticAsset
import com.lemonkids.kidliteracy.feature.home.LiteracyHomeViewModel
import com.lemonkids.kidliteracy.feature.home.LiteracyCharacterGroup
import com.lemonkids.kidliteracy.feature.home.DailyLiteracyTaskSnapshotStore
import com.lemonkids.kidliteracy.feature.home.homeHeading
import com.lemonkids.kidliteracy.feature.home.homeDateLabel
import com.lemonkids.kidliteracy.feature.recognized.RecognizedCharactersViewModel
import com.lemonkids.kidliteracy.feature.help.HelpedContent
import com.lemonkids.kidliteracy.feature.help.HelpedCharactersViewModel
import com.lemonkids.kidliteracy.feature.parentpass.ParentPassRecord
import com.lemonkids.kidliteracy.feature.parentpass.ParentPassStarSnapshot
import com.lemonkids.kidliteracy.feature.parentpass.ParentPassStarState
import com.lemonkids.kidliteracy.feature.parentpass.ParentPassesViewModel
import com.lemonkids.kidliteracy.feature.reading.ReadingEvaluationViewModel
import com.lemonkids.kidliteracy.feature.reading.ReadingContentSource
import com.lemonkids.kidliteracy.feature.reading.ReadingTarget
import com.lemonkids.kidliteracy.feature.reading.GeneratedLiteracyTask
import com.lemonkids.kidliteracy.feature.reading.GeneratedLiteracyExample
import com.lemonkids.kidliteracy.feature.reading.LiteracyTasksPreview
import com.lemonkids.kidliteracy.feature.reading.SavedLiteracyTasks
import com.lemonkids.kidliteracy.feature.reading.TencentEvaluationCredentials
import com.lemonkids.kidliteracy.feature.reading.PreparedEvaluation
import com.lemonkids.kidliteracy.feature.reading.LiteracyPracticeProgressStore
import com.lemonkids.kidliteracy.feature.reading.LiteracyAudioPlayer
import com.lemonkids.kidliteracy.feature.reading.practiceProgressKey
import com.lemonkids.kidliteracy.feature.reading.practiceProgressSyncKey
import com.lemonkids.kidliteracy.feature.reading.requiredCorrectReadings
import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.model.RecognizedCharacter
import com.lemonkids.shared.ui.auth.AuthViewModel
import com.lemonkids.shared.ui.auth.BindingCodeScreen
import dagger.hilt.android.HiltAndroidApp
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.tencent.cloud.soe.TAIOralController
import com.tencent.cloud.soe.audio.data.TAIDataSource
import com.tencent.cloud.soe.entity.ClientException
import com.tencent.cloud.soe.entity.HttpParameterKey
import com.tencent.cloud.soe.entity.OralEvaluationRequest
import com.tencent.cloud.soe.entity.ServerException
import com.tencent.cloud.soe.entity.TAIConfig
import com.tencent.cloud.soe.listener.OralEvaluationStateListener
import com.tencent.cloud.soe.listener.TAIListener

@HiltAndroidApp
class LemonLiteracyApplication : android.app.Application()

private enum class Page { HOME, PROFILE, KNOWN, PENDING, LIBRARY, HELPED, PARENT_PASSES }

/** ISO 216 A4 纸张的宽高比：1 : √2。 */
private const val A4_PAPER_WIDTH_TO_HEIGHT = 0.70710677f

private data class Lesson(
    val title: String,
    val dateLabel: String?,
    val known: Boolean,
    val characters: List<String>,
    /** 当前列表中当天已完成全部朗读要求的字。 */
    val completedCharacterIds: Set<String> = emptySet(),
    /** 与 [characters] 一一对应，用于把完成状态准确映射到首页字格。 */
    val characterIds: List<String> = emptyList()
)

/** 首页中被点开的单个字；保留其所在分组以获得已认识字的朗读次数规则。 */
private data class SelectedStudyCharacter(
    val group: LiteracyCharacterGroup,
    val characterId: String
)

private data class LiteracyCard(
    val literacyCharacterId: String,
    val word: String,
    val contentSource: ReadingContentSource = ReadingContentSource.TASK,
    val character: LearningContent,
    val terms: List<LearningContent>,
    val sentences: List<LearningContent>
) {
    val completed: Boolean
        get() = character.learned && terms.all { it.learned } && sentences.all { it.learned }
}

private data class LearningContent(
    val target: ReadingTarget,
    val correctReadings: Int,
    val requiredReadings: Int
) {
    val text: String get() = target.displayText
    val learned: Boolean get() = correctReadings >= requiredReadings
}

private enum class RecordingState { PREPARING, READY, RECORDING, EVALUATING, FINISHED, ERROR }

private data class EvaluationSummary(
    /** 仅用于当前弹层渲染的错字位置；弹层销毁后不会被保存。 */
    val wrongCharacterIndexes: Set<Int> = emptySet(),
    val targetType: String,
    /** 仅用于展示本次错字数量；不再据此计算星级。 */
    val wrongCharacterCount: Int = wrongCharacterIndexes.size
)

private const val ACTION_LITERACY_READING_CONTENT_CLICKED =
    "com.lemonkids.kidliteracy.action.READING_CONTENT_CLICKED"
private const val EXTRA_READING_TARGET_TYPE = "reading_target_type"
private const val EXTRA_READING_CONTENT = "reading_content"
private const val EXTRA_READING_CLICKED_CHARACTER = "reading_clicked_character"
private const val EXTRA_READING_CHARACTER_INDEX = "reading_character_index"

@Composable
fun LemonLiteracyApp(authViewModel: AuthViewModel = hiltViewModel()) {
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    if (!authState.isFirstCheckComplete) {
        BindingLoadingScreen()
        if (authState.requiresSessionRecovery) {
            SessionRecoveryDialog(
                isRecovering = authState.isRecoveringSession,
                message = authState.sessionRecoveryMessage,
                onRetryRefresh = authViewModel::retrySessionRefresh,
                onRestoreWithBinding = authViewModel::restoreSavedBindingCodeFromDialog
            )
        }
        return
    }

    if (!authState.isLoggedIn) {
        BindingCodeScreen(
            // 任务码是儿童应用的共享绑定码，允许在任务、认字应用的多个设备登录。
            type = "task",
            deviceId = deviceId,
            title = "输入认字应用绑定码",
            subtitle = "请让家长在家长端生成认字应用绑定码",
            onSuccess = {}
        )
        return
    }

    LiteracyContent(
        childName = authState.currentUser?.name.orEmpty().ifBlank { "小朋友" },
        avatarUrl = authState.currentUser?.avatarUrl,
        userId = authState.currentUser?.uid.orEmpty()
    )

    // 放在应用根层且最后组合。Dialog 是独立窗口，能覆盖“智能添加识字”、朗读等
    // 所有业务弹层，避免用户在会话不可用时继续提交其他请求。
    if (authState.requiresSessionRecovery) {
        SessionRecoveryDialog(
            isRecovering = authState.isRecoveringSession,
            message = authState.sessionRecoveryMessage,
            onRetryRefresh = authViewModel::retrySessionRefresh,
            onRestoreWithBinding = authViewModel::restoreSavedBindingCodeFromDialog
        )
    }
}

@Composable
private fun SessionRecoveryDialog(
    isRecovering: Boolean,
    message: String?,
    onRetryRefresh: () -> Unit,
    onRestoreWithBinding: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "登录连接需要恢复",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "当前登录凭证未能刷新。请先重试；如果仍无法恢复，可使用本机已保存的绑定码静默重新登录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5E6A6E)
                )
                message?.let {
                    Text(it, color = EvaluationErrorRed, fontSize = 13.sp)
                }
                if (isRecovering) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Sky)
                        Text("正在恢复登录…", color = Ink, fontSize = 14.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetryRefresh,
                        enabled = !isRecovering,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重试刷新")
                    }
                    Button(
                        onClick = onRestoreWithBinding,
                        enabled = !isRecovering,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Sky)
                    ) {
                        Text("使用绑定码登录")
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = Sky)
            Text("正在准备认字小麦田…", color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiteracyContent(childName: String, avatarUrl: String?, userId: String) {
    var page by remember { mutableStateOf(Page.HOME) }
    var recordingTarget by remember { mutableStateOf<ReadingTarget?>(null) }
    var pendingRecordingTarget by remember { mutableStateOf<ReadingTarget?>(null) }
    var selectedStudyCharacter by remember { mutableStateOf<SelectedStudyCharacter?>(null) }
    var showGenerateLiteracyTasksDialog by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val homeViewModel: LiteracyHomeViewModel = hiltViewModel()
    val readingEvaluationViewModel: ReadingEvaluationViewModel = hiltViewModel()
    val homeState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val practiceProgressStore = remember(context, userId) { LiteracyPracticeProgressStore(context, userId) }
    val dailyTaskSnapshotStore = remember(context) { DailyLiteracyTaskSnapshotStore(context) }
    var practiceProgress by remember(userId) { mutableStateOf(practiceProgressStore.snapshot()) }
    var completedTaskCharacterIds by remember(userId) { mutableStateOf(emptySet<String>()) }
    var completingTaskCharacterIds by remember(userId) { mutableStateOf(emptySet<String>()) }

    fun isTaskCharacterCompleted(literacyCharacterId: String): Boolean =
        literacyCharacterId in completedTaskCharacterIds ||
            homeState.groups.any { literacyCharacterId in it.completedCharacterIds }

    fun completeCharacterIfReady(character: ChildLiteracyCharacter) {
        if (!character.isPracticeComplete(practiceProgress) ||
            isTaskCharacterCompleted(character.id) ||
            character.id in completingTaskCharacterIds
        ) return
        completingTaskCharacterIds = completingTaskCharacterIds + character.id
        scope.launch {
            readingEvaluationViewModel.completeLiteracyCharacter(
                literacyCharacterId = character.id,
                hasCharacterAudioPointRead = practiceProgressStore.hasCharacterAudioPointRead(character.id)
            )
                .onSuccess {
                    completedTaskCharacterIds = completedTaskCharacterIds + character.id
                    completingTaskCharacterIds = completingTaskCharacterIds - character.id
                    readingEvaluationViewModel.invalidateEvaluationCacheForCharacter(character.id)
                    dailyTaskSnapshotStore.markCompleted(userId, character.id)
                    practiceProgressStore.clearCharacter(character.id)
                    practiceProgress = practiceProgressStore.snapshot()
                    homeViewModel.load(userId)
                }
                .onFailure {
                    completingTaskCharacterIds = completingTaskCharacterIds - character.id
                    notice = "这一字已经读完，联网后会自动完成收录"
                }
        }
    }

    fun recordCorrectReadings(target: ReadingTarget, correctReadings: Int = 1): Int {
        // 当天快照中已经完成的字保留在学习纸上供回看，但不再重新累计本地星星
        // 或重复请求服务端完成接口。
        if (target.contentSource == ReadingContentSource.TASK && isTaskCharacterCompleted(target.literacyCharacterId)) {
            return target.requiredCorrectReadings()
        }
        val updatedCorrectReadings = practiceProgressStore.recordCorrectReadings(target, correctReadings)
        practiceProgress = practiceProgressStore.snapshot()
        // 本地写入已完成；上传只在协程中静默执行，绝不能阻塞孩子开始下一轮朗读。
        scope.launch {
            readingEvaluationViewModel.syncPracticeProgress(target, updatedCorrectReadings)
                .onSuccess { confirmedCount ->
                    practiceProgressStore.mergeRemoteProgress(
                        mapOf(target.practiceProgressSyncKey() to confirmedCount),
                        listOf(target)
                    )
                    practiceProgressStore.markProgressSynced(target.practiceProgressKey(), confirmedCount)
                    practiceProgress = practiceProgressStore.snapshot()
                }
                .onFailure { error ->
                    android.util.Log.w("LiteracyProgress", "朗读进度将在稍后重试上传", error)
                }
        }
        if (target.contentSource == ReadingContentSource.TASK) {
            homeState.groups.flatMap { it.learningCharacters }
                .firstOrNull { it.id == target.literacyCharacterId }
                ?.let(::completeCharacterIfReady)
        }
        return updatedCorrectReadings
    }

    suspend fun passCharacter(card: LiteracyCard): Result<Unit> {
        // 先持久化点击前状态；审计写入失败时不改变本地学习进度，便于家长稍后重试。
        return readingEvaluationViewModel.recordParentPass(
            literacyCharacterId = card.literacyCharacterId,
            contentSource = card.contentSource,
            starSnapshot = card.parentPassStarSnapshot()
        ).onSuccess {
            practiceProgressStore.markAllCorrectReadings(card.practiceTargets())
            practiceProgress = practiceProgressStore.snapshot()
            if (card.contentSource == ReadingContentSource.TASK) {
                homeState.groups.flatMap { it.learningCharacters }
                    .firstOrNull { it.id == card.literacyCharacterId }
                    ?.let(::completeCharacterIfReady)
            }
        }
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingRecordingTarget
        pendingRecordingTarget = null
        if (granted && target != null) {
            recordingTarget = target
        } else if (!granted) {
            notice = "需要麦克风权限才能开始朗读"
        }
    }
    // TextToSpeech 的创建是异步的。此前未等初始化完成就直接调用 speak，
    // 在 Pad 上首个或较早的点读请求会被引擎丢弃，表现为点击没有声音。
    var isTtsReady by remember(context) { mutableStateOf(false) }
    var pendingSpeech by remember(context) { mutableStateOf<String?>(null) }
    val speechAudioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
    val tts = remember(context) {
        TextToSpeech(context) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) {
                android.util.Log.e("LiteracyTts", "TextToSpeech 初始化失败，status=$status")
            }
        }
    }

    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                android.util.Log.d("LiteracyTts", "开始播放，id=$utteranceId")
            }

            override fun onDone(utteranceId: String) {
                android.util.Log.d("LiteracyTts", "播放完成，id=$utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                android.util.Log.e("LiteracyTts", "播放失败，id=$utteranceId")
            }
        })
        onDispose { tts.stop(); tts.shutdown() }
    }

    val literacyAudioPlayer = remember(context) { LiteracyAudioPlayer(context) }
    DisposableEffect(literacyAudioPlayer) {
        onDispose { literacyAudioPlayer.release() }
    }

    fun speakNow(text: String) {
        // 讯飞引擎默认使用媒体通道，录音或其他媒体音频存在时容易被压低；
        // 显式声明为短暂的语音提示，让系统按语音内容处理音频焦点。
        tts.setAudioAttributes(speechAudioAttributes)
        val languageStatus = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            android.util.Log.e("LiteracyTts", "设备不支持简体中文 TTS，status=$languageStatus")
            return
        }
        val speakStatus = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "literacy-context")
        if (speakStatus == TextToSpeech.ERROR) {
            android.util.Log.e("LiteracyTts", "朗读请求未被 TTS 接受，text=$text")
        } else {
            android.util.Log.d("LiteracyTts", "已提交朗读请求，text=$text")
        }
    }

    // 初始化期间的多次点击只保留最新一次；TTS 就绪后自动播放，避免无声点击。
    LaunchedEffect(isTtsReady, pendingSpeech) {
        if (isTtsReady) {
            pendingSpeech?.let { text ->
                pendingSpeech = null
                speakNow(text)
            }
        }
    }

    fun speakWithContext(text: String) {
        if (isTtsReady) speakNow(text) else pendingSpeech = text
    }

    fun playPointReading(target: ReadingTarget, fallbackText: String) {
        // 每次点读都会先停掉系统 TTS；MP3 失败时播放器才会单次回调重新启用 TTS。
        pendingSpeech = null
        tts.stop()
        literacyAudioPlayer.play(target.audioUrl, target.audioVersion) {
            speakWithContext(fallbackText)
        }
    }

    fun openReading(target: ReadingTarget) {
        literacyAudioPlayer.stop()
        tts.stop()
        pendingSpeech = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recordingTarget = target
        } else {
            // 首次授权在进入弹层前完成，弹层一旦出现即可直接开始朗读。
            pendingRecordingTarget = target
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(userId) {
        homeViewModel.load(userId)
    }
    LaunchedEffect(userId, homeState.dataVersion) {
        val batchId = "${homeState.dataVersion}:" + homeState.groups.joinToString(separator = "#") { group ->
            listOf(group.type.name, group.groupNumber.toString(), group.characters.joinToString("")).joinToString(":")
        }
        if (homeState.groups.isNotEmpty()) {
            readingEvaluationViewModel.beginDailyEvaluationCache(userId, batchId)
            // 首页静默预热一份可供所有项目复用的短期腾讯凭证。
            readingEvaluationViewModel.beginPageCredentials()

            val targets = homeState.groups.flatMap(LiteracyCharacterGroup::practiceTargetsForSync)
            readingEvaluationViewModel.loadPracticeProgress()
                .onSuccess { remoteProgress ->
                    practiceProgressStore.applyRemoteProgressReset(
                        remoteProgress.resets.associate { it.syncKey() to it.correctReadings },
                        targets
                    )
                    practiceProgressStore.mergeRemoteProgress(
                        remoteProgress.progress.associate { it.syncKey() to it.correctReadings },
                        targets
                    )
                    practiceProgress = practiceProgressStore.snapshot()

                    // 离线时留下的记录在首页恢复网络后静默补传；只重试当前首页仍存在的项目。
                    practiceProgressStore.queueExistingProgressForSync()
                    val targetsByProgressKey = targets.associateBy(ReadingTarget::practiceProgressKey)
                    practiceProgressStore.pendingProgress().forEach { (progressKey, count) ->
                        targetsByProgressKey[progressKey]?.let { target ->
                            scope.launch {
                                readingEvaluationViewModel.syncPracticeProgress(target, count)
                                    .onSuccess { confirmedCount ->
                                        practiceProgressStore.mergeRemoteProgress(
                                            mapOf(target.practiceProgressSyncKey() to confirmedCount),
                                            listOf(target)
                                        )
                                        practiceProgressStore.markProgressSynced(progressKey, confirmedCount)
                                        practiceProgress = practiceProgressStore.snapshot()
                                    }
                                    .onFailure { error ->
                                        android.util.Log.w("LiteracyProgress", "待上传朗读进度同步失败", error)
                                    }
                            }
                        }
                    }
                }
                .onFailure { error ->
                    // 云端读取失败不影响本地继续学习；待上传标记会留到下次首页加载。
                    android.util.Log.w("LiteracyProgress", "读取共享朗读进度失败", error)
                }
        }
    }

    val scene = when (page) {
        Page.HOME -> R.drawable.garden_a07_a10_recognition_garden
        Page.PROFILE -> R.drawable.garden_a01_profile_farm
        Page.PENDING -> R.drawable.garden_a02_pending_garden
        Page.KNOWN -> R.drawable.garden_a03_recognized_garden
        Page.LIBRARY -> R.drawable.garden_a04_library_shelves
        Page.HELPED -> R.drawable.garden_a05_helped_reading_corner
        Page.PARENT_PASSES -> R.drawable.garden_a06_passes_desktop
    }
    GardenPageScaffold(scene, sceneAlignment = if (page == Page.HOME || page == Page.PROFILE) Alignment.TopCenter else Alignment.Center) {
        Box(Modifier.fillMaxSize()) {
            when (page) {
                Page.HOME -> HomeScreen(
                    childName = childName,
                    onNavigate = { page = it },
                    groups = homeState.groups,
                    isLoading = homeState.isLoading,
                    practiceProgress = practiceProgress,
                    onRefresh = { homeViewModel.load(userId) },
                    onCharacter = { group, index ->
                        val characterId = if (group.isKnown) {
                            group.recognizedCharacters.getOrNull(index)?.id
                        } else {
                            group.learningCharacters.getOrNull(index)?.id
                        }
                        characterId?.let { selectedStudyCharacter = SelectedStudyCharacter(group, it) }
                    }
                )
                Page.PROFILE -> ProfileScreen(
                    childName = childName,
                    avatarUrl = avatarUrl,
                    onNavigate = { page = it },
                    userId = userId,
                    onKnown = { page = Page.KNOWN },
                    onPending = { page = Page.PENDING },
                    onLibrary = { page = Page.LIBRARY },
                    onHelped = { page = Page.HELPED },
                    onParentPasses = { page = Page.PARENT_PASSES },
                    onGenerateLiteracyTasks = { showGenerateLiteracyTasksDialog = true }
                )
                Page.KNOWN -> KnownScreen(
                    userId = userId,
                    onBack = { page = Page.PROFILE },
                    onArchive = { character ->
                        readingEvaluationViewModel.archiveRecognizedCharacter(character.id)
                    },
                    onArchived = { character ->
                        homeViewModel.load(userId)
                        notice = "${character.character} 已存入字库"
                    },
                    onNotice = { notice = it }
                )
                Page.PENDING -> PendingCharactersScreen(userId = userId, onBack = { page = Page.PROFILE })
                Page.LIBRARY -> LibraryScreen(userId = userId, onBack = { page = Page.PROFILE })
                Page.HELPED -> HelpedCharactersScreen(
                    userId = userId,
                    onBack = { page = Page.PROFILE },
                    onRecognized = { character ->
                        homeViewModel.load(userId)
                        notice = "$character 已加入已认识的字"
                    }
                )
                Page.PARENT_PASSES -> ParentPassesScreen(
                    userId = userId,
                    onBack = { page = Page.PROFILE },
                    onUndoApplied = { record ->
                        practiceProgressStore.restoreReadings(record.snapshotProgressEntries())
                        practiceProgress = practiceProgressStore.snapshot()
                    }
                )
            }
            if (notice != null) SuccessNotice(notice!!, onDismiss = { notice = null })
        }
    }

    recordingTarget?.let { target ->
        ReadingDialog(
            target = target,
            correctReadingCount = if (target.contentSource == ReadingContentSource.TASK) {
                if (isTaskCharacterCompleted(target.literacyCharacterId)) {
                    target.requiredCorrectReadings()
                } else {
                    practiceProgress[target.practiceProgressKey()] ?: 0
                }
            } else {
                practiceProgress[target.practiceProgressKey()] ?: 0
            },
            onDismiss = {
                literacyAudioPlayer.stop()
                tts.stop()
                pendingSpeech = null
                recordingTarget = null
            },
            onSpeak = ::playPointReading,
            onStopPlayback = {
                literacyAudioPlayer.stop()
                tts.stop()
                pendingSpeech = null
            },
            onCharacterAudioPointRead = { pointReadTarget ->
                // 只有待认识任务的主字点读影响整字完成后的去向；词、句点读不参与。
                if (pointReadTarget.contentSource == ReadingContentSource.TASK && pointReadTarget.targetType == "character") {
                    practiceProgressStore.markCharacterAudioPointRead(pointReadTarget.literacyCharacterId)
                }
            },
            onCorrectReadings = ::recordCorrectReadings
        )
    }
    selectedStudyCharacter?.let { selection ->
        val group = selection.group
        val card = if (group.isKnown) {
            group.recognizedCharacters
                .firstOrNull { it.id == selection.characterId }
                ?.toLiteracyCard(practiceProgress, group.recognizedCharacterRequiredReadings)
        } else {
            group.learningCharacters
                .firstOrNull { it.id == selection.characterId }
                ?.toLiteracyCard(
                    practiceProgress,
                    selection.characterId in (completedTaskCharacterIds + group.completedCharacterIds)
                )
        }
        card?.let {
            CharacterStudyDialog(
                card = it,
                onDismiss = {
                    literacyAudioPlayer.stop()
                    tts.stop()
                    pendingSpeech = null
                    selectedStudyCharacter = null
                },
                onSpeak = ::playPointReading,
                onStopPlayback = {
                    literacyAudioPlayer.stop()
                    tts.stop()
                    pendingSpeech = null
                },
                onCharacterAudioPointRead = { pointReadTarget ->
                    if (pointReadTarget.contentSource == ReadingContentSource.TASK && pointReadTarget.targetType == "character") {
                        practiceProgressStore.markCharacterAudioPointRead(pointReadTarget.literacyCharacterId)
                    }
                },
                onCorrectReadings = ::recordCorrectReadings,
                onParentPass = ::passCharacter
            )
        }
    }
    if (showGenerateLiteracyTasksDialog) {
        GenerateLiteracyTasksDialog(
            onDismiss = { showGenerateLiteracyTasksDialog = false },
            onPreview = { characters ->
                readingEvaluationViewModel.previewLiteracyTasks(characters)
            },
            onSave = { destination, characters, tasks ->
                val result = when (destination) {
                    LiteracySaveDestination.PENDING -> readingEvaluationViewModel.saveLiteracyTasks(characters, tasks)
                    LiteracySaveDestination.RECOGNIZED -> readingEvaluationViewModel.saveRecognizedLiteracyTasks(characters, tasks)
                }
                if (result.isSuccess) {
                    val generated = result.getOrThrow()
                    homeViewModel.load(userId)
                    val created = generated.createdCharacters.joinToString("、")
                    val knownCharacters = generated.knownCharacters.joinToString("、")
                    val skippedExisting = generated.skippedExistingCharacters.joinToString("、")
                    val skippedRecognized = generated.skippedRecognizedCharacters.joinToString("、")
                    notice = buildString {
                        if (created.isNotBlank()) {
                            append(
                                if (destination == LiteracySaveDestination.RECOGNIZED) {
                                    "已添加已认识字：$created"
                                } else {
                                    "已添加待认识字：$created"
                                }
                            )
                        }
                        if (knownCharacters.isNotBlank()) {
                            if (isNotEmpty()) append("；")
                            append("输入中已在字库：$knownCharacters")
                        }
                        if (skippedExisting.isNotBlank()) {
                            if (isNotEmpty()) append("；")
                            append("已有待认识任务：$skippedExisting")
                        }
                        if (skippedRecognized.isNotBlank()) {
                            if (isNotEmpty()) append("；")
                            append(
                                if (destination == LiteracySaveDestination.PENDING) {
                                    "已从已认识移至待认识：$skippedRecognized"
                                } else {
                                    "已置顶已认识字：$skippedRecognized"
                                }
                            )
                        }
                    }.ifBlank { "没有可添加的识字内容" }
                    Result.success(generated)
                } else {
                    Result.failure(result.exceptionOrNull() ?: IllegalStateException("保存识字任务失败，请稍后重试"))
                }
            }
        )
    }
}

@Composable
private fun LiteracyBottomNav(current: Page, onNavigate: (Page) -> Unit) {
    val unselectedColor = Color(0xFF6D7A89)
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 342.dp).fillMaxWidth(),
            shape = CircleShape, color = Background, shadowElevation = 5.dp) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Page.HOME to "认字", Page.PROFILE to "我的").forEach { (page, title) ->
                    val selected = current == page
                    Surface(Modifier.weight(1f).heightIn(min = 48.dp).semantics { this.selected = selected; role = Role.Tab }.clickable { onNavigate(page) },
                        shape = CircleShape, color = if (selected) SkyLight else Color.Transparent,
                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null) {
                        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (page == Page.HOME) Icons.Default.Home else Icons.Default.Person, null, tint = if (selected) Sky else unselectedColor)
                            Spacer(Modifier.width(8.dp))
                            Text(title, color = if (selected) Sky else unselectedColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePageHeader(childName: String, isLoading: Boolean, onRefresh: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 102.dp)) {
        val compact = maxWidth < 690.dp || LocalDensity.current.fontScale > 1.3f
        Row(
            Modifier.fillMaxWidth().padding(start = if (compact) 0.dp else 80.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GardenArt(R.drawable.garden_logo_lemon, Modifier.size(if (compact) 42.dp else 56.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = if (compact) 175.dp else 256.dp)) {
                Text("柠檬认字", color = Ink, fontSize = if (compact) 28.sp else 36.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text("$childName，和小麦一起认识新朋友", color = Color(0xFF687D9F),
                    fontSize = if (compact) 14.sp else 16.sp, maxLines = 2)
            }
            Spacer(Modifier.width(12.dp))
            Surface(onClick = onRefresh, enabled = !isLoading,
                modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp),
                shape = CircleShape, color = Color(0xFFDDF3FF).copy(alpha = .84f),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Sky)
                    Spacer(Modifier.width(4.dp))
                    Text("刷新", fontSize = 18.sp, color = Sky)
                }
            }
            if (!compact) {
                Spacer(Modifier.width(12.dp))
                GardenArt(R.drawable.garden_mascot_welcome, Modifier.size(112.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    childName: String,
    onNavigate: (Page) -> Unit,
    groups: List<LiteracyCharacterGroup>,
    isLoading: Boolean,
    practiceProgress: Map<String, Int>,
    onRefresh: () -> Unit,
    onCharacter: (LiteracyCharacterGroup, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val showScrollToTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = (maxWidth - 32.dp).coerceAtMost(815.dp)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(contentWidth).fillMaxHeight().align(Alignment.TopCenter),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { HomePageHeader(childName, isLoading, onRefresh) }
                    if (groups.isEmpty()) {
                        item { LiteracyEmptyState() }
                    } else {
                        itemsIndexed(groups.chunked(2), key = { _, row -> "${row.first().type}-${row.first().groupNumber}" }) { rowIndex, row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                row.forEachIndexed { columnIndex, group ->
                                    val knownCornerIndex = groups.take(rowIndex * 2 + columnIndex).count { it.isKnown }
                                    LessonCard(group.toLesson(practiceProgress),
                                        onCharacterClick = { index -> onCharacter(group, index) },
                                        knownCornerIndex = knownCornerIndex,
                                        modifier = Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (showScrollToTop) Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 12.dp)
                        .size(52.dp)
                        .shadow(8.dp, CircleShape)
                        .clickable { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    shape = CircleShape,
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SkyLight)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "返回顶部", tint = Sky)
                    }
                }
            }
            LiteracyBottomNav(Page.HOME, onNavigate)
        }

        if (isLoading && groups.isEmpty()) RequestLoadingOverlay()
    }
}

@Composable
private fun RequestLoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Background.copy(alpha = .94f)).clickable {},
        contentAlignment = Alignment.Center
    ) {
        GardenPanel(Modifier.padding(24.dp)) { LoadingContent() }
    }
}

@Composable
private fun LiteracyEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 42.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = .86f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 42.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🌱", fontSize = 42.sp)
            Text("暂无识字任务", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("等待家长为你添加新的汉字吧", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7C898D), textAlign = TextAlign.Center)
        }
    }
}

private fun LiteracyCharacterGroup.toLesson(practiceProgress: Map<String, Int>) = Lesson(
    title = homeHeading(),
    dateLabel = homeDateLabel(),
    known = isKnown,
    characters = characters,
    completedCharacterIds = if (isKnown) {
        recognizedCharacters
            .filter { it.isPracticeComplete(practiceProgress, recognizedCharacterRequiredReadings) }
            .map { it.id }
            .toSet()
    } else {
        completedCharacterIds
    },
    characterIds = if (isKnown) recognizedCharacters.map { it.id } else learningCharacters.map { it.id }
)

@Composable
private fun LessonCard(
    lesson: Lesson,
    onCharacterClick: (Int) -> Unit,
    knownCornerIndex: Int,
    modifier: Modifier = Modifier
) {
    val accent = if (lesson.known) Leaf else Coral
    val frameColor = if (lesson.known) Color(0xFFD9F6A8) else Color(0xFFFFBBAA)
    val cornerArt = if (!lesson.known) R.drawable.garden_card_corner_sprout else when (knownCornerIndex % 3) {
        0 -> R.drawable.garden_card_corner_white_a
        1 -> R.drawable.garden_card_corner_white_b
        else -> R.drawable.garden_card_corner_pink
    }
    val cornerWidth = if (!lesson.known) 118.dp else (120 + knownCornerIndex.coerceAtMost(2) * 3).dp
    val cornerHeight = if (!lesson.known) 50.dp else (51 + knownCornerIndex.coerceAtMost(2)).dp
    val cardShape = RoundedCornerShape(24.dp)
    val innerShape = RoundedCornerShape(20.dp)
    Surface(modifier, shape = cardShape,
        color = frameColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White), shadowElevation = 4.dp) {
        Box(Modifier.fillMaxWidth().padding(4.dp).clip(innerShape)
            .background(Brush.verticalGradient(if (lesson.known) {
                listOf(Color(0xFFF9FFE9), Color(0xFFEAFBD5))
            } else {
                listOf(Color(0xFFFFF8EE), Color(0xFFFFE5DA))
            })).border(1.dp, Color.White.copy(alpha = .9f), innerShape)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 45.dp)) {
                    Row(Modifier.fillMaxWidth().padding(end = cornerWidth - 12.dp).heightIn(min = 45.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GardenArt(if (lesson.known) R.drawable.garden_sprout_shaded else R.drawable.garden_tulip,
                            Modifier.width(40.dp).height(45.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lesson.title, fontSize = 18.sp, lineHeight = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (lesson.known) Color(0xFF126B20) else Color(0xFFDE432A))
                            lesson.dateLabel?.let { date ->
                                Text(date, fontSize = 13.sp, lineHeight = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lesson.known) Color(0xFF126B20) else Color(0xFFDE432A))
                            }
                        }
                    }
                    // 角饰不参与标题区测量；高度超过 45 dp 时向上延伸，底边始终贴住分隔线。
                    Box(Modifier.matchParentSize()) {
                        // 素材底部含透明留白；下移 5 dp，让可见花草边缘落在分隔线上。
                        GardenArt(cornerArt, Modifier.align(Alignment.BottomEnd).offset(y = 5.dp)
                            .width(cornerWidth).height(cornerHeight))
                    }
                }
                HorizontalDivider(color = accent.copy(alpha = .18f), thickness = 1.dp)
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    val layout = homeTileLayout(maxWidth.value, LocalDensity.current.fontScale)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lesson.characters.chunked(layout.columns).forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEachIndexed { colIndex, char ->
                                    val index = rowIndex * layout.columns + colIndex
                                    GardenCharacterTile(char, Modifier.width(layout.widthDp.dp).height(layout.heightDp.dp),
                                        completed = lesson.characterIds.getOrNull(index) in lesson.completedCharacterIds,
                                        onClick = { onCharacterClick(index) }, fontSize = layout.fontSp.sp,
                                        cornerRadius = 10.dp, accent = accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color, background: Color) {
    Surface(shape = RoundedCornerShape(50), color = background.copy(alpha = .95f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .35f))) { Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
}

@Composable
private fun LoadingContent() {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🌾", fontSize = 48.sp)
        Text("小麦车正在送来新的汉字…", style = MaterialTheme.typography.titleMedium, color = Ink)
        CircularProgressIndicator(color = Sky)
    }
}

@Composable
private fun EmptyContent(title: String, subtitle: String, emoji: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Background), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(vertical = 46.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 54.sp); Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleLarge, color = Ink); Spacer(Modifier.height(6.dp)); Text(subtitle, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF849094))
        }
    }
}

@Composable
private fun LearnScreen(
    group: LiteracyCharacterGroup,
    onBack: () -> Unit,
    onRecord: (ReadingTarget) -> Unit,
    practiceProgress: Map<String, Int>,
    completedTaskCharacterIds: Set<String>
) {
    val cards = if (group.isKnown) {
        group.recognizedCharacters.map {
            it.toLiteracyCard(
                practiceProgress = practiceProgress,
                characterRequiredReadings = group.recognizedCharacterRequiredReadings
            )
        }
    } else {
        group.learningCharacters.map { it.toLiteracyCard(practiceProgress, it.id in completedTaskCharacterIds) }
    }
    val evaluationViewModel: ReadingEvaluationViewModel = hiltViewModel()
    var isPreparingEvaluation by remember(group) { mutableStateOf(true) }

    // 进入认字页立即领取一份通用评测凭证；完成前用覆盖层
    // 拦截点击，避免孩子打开弹层后看到尚未准备好的灰色“开始”按钮。
    LaunchedEffect(group) {
        try {
            evaluationViewModel.beginPageCredentials()
        } finally {
            isPreparingEvaluation = false
        }
    }
    val completedCount = cards.count { it.completed }
    val progress = if (cards.isEmpty()) 0f else completedCount.toFloat() / cards.size
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Ink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${if (group.isKnown) "已认识的字" else "待认识的字"} · 第${group.groupNumber}组", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text("本组共 ${cards.size} 个汉字", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF819094))
                }
                StatusPill(if (group.isKnown) "已认识" else "学习中", if (group.isKnown) Leaf else Sky, if (group.isKnown) LeafLight else SkyLight)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("学习进度", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Line)) { Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Leaf)) }
                Spacer(Modifier.width(10.dp)); Text("$completedCount / ${cards.size}", color = Leaf, fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
                    .aspectRatio(A4_PAPER_WIDTH_TO_HEIGHT),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 5.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(cards) { card -> LearningCard(card, onRecord) }
                    }
                }
            }
        }
        }
        if (isPreparingEvaluation) EvaluationPreparationOverlay()
    }
}

@Composable
private fun EvaluationPreparationOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = .94f))
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("🎙️", fontSize = 48.sp)
            Text("正在准备口语评测…", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("准备完成后就可以开始认字", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7C898D))
            CircularProgressIndicator(color = Sky)
        }
    }
}

private fun ChildLiteracyCharacter.toLiteracyCard(
    practiceProgress: Map<String, Int>,
    completed: Boolean
) = LiteracyCard(
    literacyCharacterId = id,
    word = character,
    contentSource = ReadingContentSource.TASK,
    character = learningContent(
        ReadingTarget(id, "character", character, audioUrl = characterAudioUrl, audioVersion = characterAudioVersion),
        practiceProgress,
        completed
    ),
    terms = words.mapIndexedNotNull { index, example ->
        example.text.takeIf { it.isNotBlank() }?.let { text ->
            learningContent(ReadingTarget(id, "word", text, audioUrl = example.audioUrl, audioVersion = example.audioVersion, itemOrder = index, wordText = text), practiceProgress, completed)
        }
    },
    sentences = sentences.mapIndexedNotNull { index, example ->
        example.text.takeIf { it.isNotBlank() }?.let { text ->
            learningContent(ReadingTarget(id, "sentence", text, audioUrl = example.audioUrl, audioVersion = example.audioVersion, itemOrder = index, sentenceText = text), practiceProgress, completed)
        }
    }
)

private fun RecognizedCharacter.toLiteracyCard(
    practiceProgress: Map<String, Int>,
    characterRequiredReadings: Int = 3
) = LiteracyCard(
    literacyCharacterId = id,
    word = character,
    contentSource = ReadingContentSource.RECOGNIZED,
    character = learningContent(
        ReadingTarget(
            id,
            "character",
            character,
            audioUrl = characterAudioUrl,
            audioVersion = characterAudioVersion,
            contentSource = ReadingContentSource.RECOGNIZED,
            characterRequiredReadings = characterRequiredReadings
        ),
        practiceProgress,
        completed = false
    ),
    terms = words.mapIndexedNotNull { index, example ->
        example.text.takeIf { it.isNotBlank() }?.let { text ->
            learningContent(
                ReadingTarget(id, "word", text, audioUrl = example.audioUrl, audioVersion = example.audioVersion, itemOrder = index, wordText = text, contentSource = ReadingContentSource.RECOGNIZED),
                practiceProgress,
                completed = false
            )
        }
    },
    // 已认识字为复习模式：只练主字与词语，不展示句子，完成判定也不能包含句子星数。
    sentences = emptyList()
)

/** 已认识字复习只要求主字和词语满星，且使用其自身的一星词语规则。 */
private fun RecognizedCharacter.isPracticeComplete(
    practiceProgress: Map<String, Int>,
    characterRequiredReadings: Int = 3
): Boolean = toLiteracyCard(practiceProgress, characterRequiredReadings).completed

private fun learningContent(
    target: ReadingTarget,
    practiceProgress: Map<String, Int>,
    completed: Boolean
) = LearningContent(
    target = target,
    correctReadings = if (completed) target.requiredCorrectReadings() else practiceProgress[target.practiceProgressKey()] ?: 0,
    requiredReadings = target.requiredCorrectReadings()
)

private fun ChildLiteracyCharacter.isPracticeComplete(practiceProgress: Map<String, Int>): Boolean =
    practiceTargets().all { target ->
        (practiceProgress[target.practiceProgressKey()] ?: 0) >= target.requiredCorrectReadings()
    }

private fun ChildLiteracyCharacter.practiceTargets(): List<ReadingTarget> = buildList {
    add(ReadingTarget(id, "character", character, audioUrl = characterAudioUrl, audioVersion = characterAudioVersion))
    words.forEachIndexed { index, example ->
        example.text.takeIf { it.isNotBlank() }?.let { text ->
            add(ReadingTarget(id, "word", text, audioUrl = example.audioUrl, audioVersion = example.audioVersion, itemOrder = index, wordText = text))
        }
    }
    sentences.forEachIndexed { index, example ->
        example.text.takeIf { it.isNotBlank() }?.let { text ->
            add(ReadingTarget(id, "sentence", text, audioUrl = example.audioUrl, audioVersion = example.audioVersion, itemOrder = index, sentenceText = text))
        }
    }
}

/** 首页当前可见的所有学习项目，用于将云端进度精确映射回本地卡片。 */
private fun LiteracyCharacterGroup.practiceTargetsForSync(): List<ReadingTarget> = buildList {
    learningCharacters.forEach { addAll(it.practiceTargets()) }
    recognizedCharacters.forEach { character ->
        add(
            ReadingTarget(
                literacyCharacterId = character.id,
                targetType = "character",
                displayText = character.character,
                contentSource = ReadingContentSource.RECOGNIZED
            )
        )
        character.words.forEachIndexed { index, example ->
            example.text.takeIf { it.isNotBlank() }?.let { text ->
                add(
                    ReadingTarget(
                        literacyCharacterId = character.id,
                        targetType = "word",
                        displayText = text,
                        itemOrder = index,
                        wordText = text,
                        contentSource = ReadingContentSource.RECOGNIZED
                    )
                )
            }
        }
    }
}

/** 当前卡片中实际会发起腾讯评测的最小朗读单元：一个字、一个词或一个句子。 */
@Composable
private fun LearningCard(card: LiteracyCard, onRecord: (ReadingTarget) -> Unit) {
    val stateColor = if (card.completed) Leaf else Coral
    val stateBg = if (card.completed) LeafLight else CoralLight
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = stateBg.copy(alpha = .38f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = .48f))
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(62.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LearningSection(
                    learningContent = card.character,
                    isCharacter = true,
                    modifier = Modifier.weight(1f)
                ) {
                    onRecord(card.character.target)
                }
                Surface(
                    modifier = Modifier.width(60.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    color = stateBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = .45f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (card.completed) "已读" else "未读",
                            color = stateColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            if (card.terms.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    card.terms.forEach { term ->
                        LearningSection(
                            learningContent = term,
                            isCharacter = false,
                            isWord = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            onRecord(term.target)
                        }
                    }
                }
            }
            card.sentences.forEach { sentence ->
                LearningSection(sentence, isCharacter = false) {
                    onRecord(sentence.target)
                }
            }
        }
    }
}

@Composable
private fun LearningSection(
    learningContent: LearningContent,
    isCharacter: Boolean,
    isWord: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onRecord: () -> Unit
) {
    val hasAudio = learningContent.target.audioUrl.isNotBlank()
    // 无在线音频的项目仍可进入练习并回退系统 TTS，但用暖色弱化样式与可直接点读的项目区分。
    val accent = when {
        !hasAudio -> Color(0xFFB29A6D)
        learningContent.learned -> Leaf
        else -> Sky
    }
    val bg = when {
        !hasAudio -> Color(0xFFF7F1E5)
        learningContent.learned -> LeafLight.copy(alpha = .5f)
        else -> SkyLight.copy(alpha = .52f)
    }
    Row(
        // 学习纸点击进入对应的朗读弹层；弹层内需长按具体汉字才会播放 TTS。
        modifier = modifier
            .heightIn(min = if (isCharacter) 62.dp else 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = .35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onRecord)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = learningContent.text,
            modifier = Modifier.fillMaxWidth(),
            maxLines = if (isCharacter || isWord) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isCharacter || isWord) TextAlign.Center else TextAlign.Start,
            color = Ink,
            fontSize = when {
                isCharacter -> 38.sp
                isWord -> 17.sp
                else -> 16.sp
            },
            fontWeight = if (isCharacter) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}

/** 首页单字学习弹层。字、词、句按教学层级分区；朗读状态始终在对应行内呈现。 */
@Composable
private fun CharacterStudyDialog(
    card: LiteracyCard,
    onDismiss: () -> Unit,
    onSpeak: (ReadingTarget, String) -> Unit,
    onStopPlayback: () -> Unit,
    onCharacterAudioPointRead: (ReadingTarget) -> Unit,
    onCorrectReadings: (ReadingTarget, Int) -> Int,
    onParentPass: suspend (LiteracyCard) -> Result<Unit>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeTarget by remember(card.literacyCharacterId) { mutableStateOf<ReadingTarget?>(null) }
    var activeRecordingState by remember(card.literacyCharacterId) { mutableStateOf<RecordingState?>(null) }
    var pendingTarget by remember(card.literacyCharacterId) { mutableStateOf<ReadingTarget?>(null) }
    var isPassing by remember(card.literacyCharacterId) { mutableStateOf(false) }
    var passErrorMessage by remember(card.literacyCharacterId) { mutableStateOf<String?>(null) }
    var showPassConfirmation by remember(card.literacyCharacterId) { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activeTarget = if (granted) pendingTarget else null
        activeRecordingState = if (granted && pendingTarget != null) RecordingState.PREPARING else null
        pendingTarget = null
    }
    fun isActivePracticeLocked(): Boolean = activeTarget != null &&
        activeRecordingState != RecordingState.FINISHED && activeRecordingState != RecordingState.ERROR

    fun submitParentPass() {
        coroutineScope.launch {
            isPassing = true
            passErrorMessage = null
            onParentPass(card)
                .onFailure { error ->
                    passErrorMessage = error.message ?: "记录通过操作失败，请检查网络后重试"
                }
            isPassing = false
        }
    }

    fun start(content: LearningContent) {
        // 满星内容已完成当天的练习，不再允许重新打开朗读会话。
        if (content.learned) return
        // 仅在准备、录音、评测阶段锁定其它项；已有结果后可以直接切换到其它字词句。
        if (isActivePracticeLocked()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            activeTarget = content.target
            activeRecordingState = RecordingState.PREPARING
        } else {
            pendingTarget = content.target
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // 正在评测时也必须允许退出。ReadingDialog 被移除时会在 DisposableEffect 中取消并释放 SDK，
    // 避免一次没有回包的评测把整个字词句弹层永久锁住。
    StudyDialogFrame(card.word, onDismiss, footer = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            passErrorMessage?.let { Text(it, color = Coral, fontSize = 13.sp) }
            Button(
                onClick = {
                    if (!isPassing && !card.completed) showPassConfirmation = true
                },
                enabled = !isPassing && !card.completed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).background(Leaf, CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                shape = CircleShape
            ) {
                if (isPassing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("正在记录…")
                } else {
                    Text(if (card.completed) "已满星" else "通过", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }) {
                    StudySectionHeader("字", "先认识这个字")
                    key(card.character.target.practiceProgressKey()) {
                        StudyPracticeItem(
                            content = card.character,
                            activeTarget = activeTarget,
                            otherRecordingActive = isActivePracticeLocked(),
                            onStart = { start(card.character) },
                            onDismiss = { activeTarget = null; activeRecordingState = null },
                            onReadingStateChanged = { activeRecordingState = it },
                            onSpeak = onSpeak,
                            onStopPlayback = onStopPlayback,
                            onCharacterAudioPointRead = onCharacterAudioPointRead,
                            onCorrectReadings = onCorrectReadings
                        )
                    }
                    StudySectionHeader("词", "一词一行，逐个练习")
                    card.terms.forEach { content ->
                        key(content.target.practiceProgressKey()) {
                            StudyPracticeItem(
                                content = content,
                                activeTarget = activeTarget,
                                otherRecordingActive = isActivePracticeLocked(),
                                onStart = { start(content) },
                                onDismiss = { activeTarget = null; activeRecordingState = null },
                                onReadingStateChanged = { activeRecordingState = it },
                                onSpeak = onSpeak,
                                onStopPlayback = onStopPlayback,
                                onCharacterAudioPointRead = onCharacterAudioPointRead,
                                onCorrectReadings = onCorrectReadings
                            )
                        }
                    }
                    if (card.sentences.isNotEmpty()) {
                        StudySectionHeader("句", "读一读完整的句子")
                        card.sentences.forEach { content ->
                            key(content.target.practiceProgressKey()) {
                                StudyPracticeItem(
                                    content = content,
                                    activeTarget = activeTarget,
                                    otherRecordingActive = isActivePracticeLocked(),
                                    onStart = { start(content) },
                                    onDismiss = { activeTarget = null; activeRecordingState = null },
                                    onReadingStateChanged = { activeRecordingState = it },
                                    onSpeak = onSpeak,
                                    onStopPlayback = onStopPlayback,
                                    onCharacterAudioPointRead = onCharacterAudioPointRead,
                                    onCorrectReadings = onCorrectReadings
                                )
                            }
                        }
                    }
    }

    if (showPassConfirmation) {
        Dialog(onDismissRequest = { if (!isPassing) showPassConfirmation = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("确认通过？", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "确认后会将当前未满星的学习项补满，并保存一条通过记录。",
                        color = Color(0xFF5F6D72)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        TextButton(
                            onClick = { showPassConfirmation = false },
                            enabled = !isPassing
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                showPassConfirmation = false
                                submitParentPass()
                            },
                            enabled = !isPassing,
                            colors = ButtonDefaults.buttonColors(containerColor = Leaf)
                        ) { Text("确认通过") }
                    }
                }
            }
        }
    }

    // 词句音素参数可能较慢；弹层一出现就并发预取，孩子读主字时通常已准备完成。
    val evaluationViewModel: ReadingEvaluationViewModel = hiltViewModel()
    LaunchedEffect(card.literacyCharacterId, card.terms, card.sentences) {
        evaluationViewModel.prefetchWordAndSentenceEvaluations(
            (card.terms + card.sentences).map { it.target }
        )
    }
}

@Composable
private fun StudySectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = if (title == "字") CoralLight else LeafLight) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                GardenArt(R.drawable.garden_sprout_shaded, Modifier.size(28.dp))
                Spacer(Modifier.width(4.dp))
                Text(title, color = if (title == "字") Coral else Leaf,
                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(subtitle, color = Color(0xFF637393), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(modifier = Modifier.weight(1f), color = Line)
    }
}

@Composable
private fun StudyPracticeItem(
    content: LearningContent,
    activeTarget: ReadingTarget?,
    otherRecordingActive: Boolean,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    onReadingStateChanged: (RecordingState) -> Unit,
    onSpeak: (ReadingTarget, String) -> Unit,
    onStopPlayback: () -> Unit,
    onCharacterAudioPointRead: (ReadingTarget) -> Unit,
    onCorrectReadings: (ReadingTarget, Int) -> Int
) {
    val active = activeTarget?.practiceProgressKey() == content.target.practiceProgressKey()
    if (active) {
        ReadingDialog(
            target = content.target,
            correctReadingCount = content.correctReadings,
            onDismiss = onDismiss,
            onSpeak = onSpeak,
            onStopPlayback = onStopPlayback,
            onCharacterAudioPointRead = onCharacterAudioPointRead,
            onCorrectReadings = onCorrectReadings,
            inline = true,
            startImmediately = true,
            onReadingStateChanged = onReadingStateChanged
        )
    } else {
        StudyContentRow(
            content = content,
            otherRecordingActive = otherRecordingActive,
            onStart = onStart,
            onSpeak = onSpeak,
            onCharacterAudioPointRead = onCharacterAudioPointRead
        )
    }
}

@Composable
private fun StudyContentRow(
    content: LearningContent,
    otherRecordingActive: Boolean,
    onStart: () -> Unit,
    onSpeak: (ReadingTarget, String) -> Unit,
    onCharacterAudioPointRead: (ReadingTarget) -> Unit
) {
    val context = LocalContext.current
    val evaluationViewModel: ReadingEvaluationViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var pointReadCharacterIndex by remember(content.target) { mutableStateOf<Int?>(null) }
    var isContentPressed by remember(content.target) { mutableStateOf(false) }
    val accent = when {
        content.learned -> Leaf
        else -> Sky
    }
    StudyPracticeRow(completed = content.learned, accent = accent, contentPressed = isContentPressed,
        readingWidth = when (content.target.targetType) { "character" -> 85.dp; "word" -> 115.dp; else -> null },
        actionLabel = "开始", actionEnabled = !otherRecordingActive, onAction = onStart,
        stars = { StudyStarRail(content.correctReadings, content.requiredReadings) },
        dots = {},
        reading = {
            DialogReadingContent(target = content.target, enabled = true, studyInline = true, pointReadCharacterIndex = pointReadCharacterIndex,
                onPressChanged = { isContentPressed = it },
                    onLongPress = { character, characterIndex ->
                        val target = content.target
                        val clickedContent = target.clickedReadingContent(character, characterIndex)
                        // 词、句仅临时高亮被长按的字；长按主字沿用原有朗读与求助计数逻辑。
                        pointReadCharacterIndex = characterIndex.takeIf {
                            target.targetType == "word" || target.targetType == "sentence"
                        }
                        context.sendBroadcast(
                            Intent(ACTION_LITERACY_READING_CONTENT_CLICKED)
                                .setPackage(context.packageName)
                                .putExtra(EXTRA_READING_TARGET_TYPE, target.targetType)
                                .putExtra(EXTRA_READING_CONTENT, clickedContent)
                                .putExtra(EXTRA_READING_CLICKED_CHARACTER, character.toString())
                                .putExtra(EXTRA_READING_CHARACTER_INDEX, characterIndex)
                        )
                        onSpeak(target, clickedContent)
                        if (target.contentSource == ReadingContentSource.TASK && target.targetType == "character") {
                            onCharacterAudioPointRead(target)
                        }
                        scope.launch {
                            // 与朗读中点读一致：记录失败不能影响已经开始的本地朗读。
                            evaluationViewModel.recordHelpRequest(target, character, characterIndex)
                        }
                    }
            )
        })
}



@Composable
private fun StudyStarRail(correctReadings: Int, requiredReadings: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-2).dp)) {
        repeat(requiredReadings) { index ->
            Text(if (index < correctReadings) "★" else "☆", color = if (index < correctReadings) Color(0xFFFFC83D) else Color(0xFF86A7D5),
                fontSize = 21.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun ReadingDialog(
    target: ReadingTarget,
    correctReadingCount: Int,
    onDismiss: () -> Unit,
    onSpeak: (ReadingTarget, String) -> Unit,
    onStopPlayback: () -> Unit,
    onCharacterAudioPointRead: (ReadingTarget) -> Unit,
    onCorrectReadings: (ReadingTarget, Int) -> Int,
    inline: Boolean = false,
    startImmediately: Boolean = false,
    onReadingStateChanged: (RecordingState) -> Unit = {}
) {
    val context = LocalContext.current
    val evaluationViewModel: ReadingEvaluationViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    // 每张词卡就是一个独立的评测与计数单元；不再将多个词拼接成一次星级评分。
    val wordTargets = remember(target) { listOf(target) }
    val isWordGroup = wordTargets.size > 1
    var state by remember { mutableStateOf(RecordingState.PREPARING) }
    var message by remember { mutableStateOf("正在准备朗读…") }
    var controller by remember { mutableStateOf<TAIOralController?>(null) }
    var currentWordIndex by remember(target) { mutableIntStateOf(0) }
    val sessions = remember(target) {
        mutableStateListOf<TencentEvaluationCredentials?>().also { list ->
            repeat(wordTargets.size) { list += null }
        }
    }
    val sessionErrors = remember(target) {
        mutableStateListOf<String?>().also { list ->
            repeat(wordTargets.size) { list += null }
        }
    }
    val wordSummaries = remember(target) {
        mutableStateListOf<EvaluationSummary?>().also { list ->
            repeat(wordTargets.size) { list += null }
        }
    }
    var evaluationSummary by remember { mutableStateOf<EvaluationSummary?>(null) }
    var displayedCorrectReadingCount by remember(target) { mutableIntStateOf(correctReadingCount) }
    // 每次开始或取消都会推进编号，忽略 SDK 取消后迟到的最终回包。
    var evaluationAttemptId by remember(target) { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(state) {
        onReadingStateChanged(state)
    }

    LaunchedEffect(correctReadingCount) {
        displayedCorrectReadingCount = correctReadingCount
    }

    fun characterReadingsNeeded(): Int =
        (target.requiredCorrectReadings() - displayedCorrectReadingCount).coerceAtLeast(1)

    fun evaluationTextFor(readingTarget: ReadingTarget): String =
        if (readingTarget.targetType == "character") {
            readingTarget.displayText.repeat(characterReadingsNeeded())
        } else {
            readingTarget.displayText
        }

    fun cancelRecording() {
        evaluationAttemptId++
        controller?.cancelOralEvaluation()
        controller?.release()
        controller = null
    }
    fun discardEvaluationForManualRead() {
        cancelRecording()
        evaluationSummary = null
        wordSummaries.indices.forEach { wordSummaries[it] = null }
        currentWordIndex = 0
        state = if (sessions.firstOrNull() != null) RecordingState.READY else RecordingState.PREPARING
        message = "已停止本次朗读，点击开始可以重新录音"
    }
    fun close() {
        // stop 后 SDK 仍在回传最终评测结果；此时不能中断，否则孩子看不到本次反馈。
        if (state == RecordingState.EVALUATING) return
        cancelRecording()
        onDismiss()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "需要麦克风权限才能开始朗读"
    }

    DisposableEffect(target) { onDispose { cancelRecording() } }

    fun startSdkEvaluation(
        preparedSession: TencentEvaluationCredentials,
        readingTarget: ReadingTarget,
        wordIndex: Int,
        evaluationReference: PreparedEvaluation
    ) {
        val attemptId = ++evaluationAttemptId
        val evaluationText = evaluationTextFor(readingTarget)
        val evaluationRefText = evaluationReference.refText
        // 仅记录教学文本与参考文本，不记录短期凭证，便于定位腾讯返回的 RefText 错误。
        android.util.Log.d(
            "ReadingEvaluation",
            "start targetType=${readingTarget.targetType}, text=${readingTarget.displayText}, refText=$evaluationRefText, textMode=${evaluationReference.textMode}"
        )
        val apiParams = java.util.TreeMap<String, Any>().apply {
            put(HttpParameterKey.SERVER_ENGINE_TYPE, "16k_zh")
            put(HttpParameterKey.EVAL_MODE, 1)
            put(HttpParameterKey.TEXT_MODE, evaluationReference.textMode)
            put(HttpParameterKey.SCORE_COEFF, 1.0)
            put(HttpParameterKey.SENTENCE_INFO_ENABLED, 1)
            put(HttpParameterKey.REF_TEXT, evaluationRefText)
        }
        val listener = object : TAIListener {
            override fun onMessage(msg: String) = Unit
            override fun onVad() = Unit
            override fun onVolumeDb(volumeDb: Float) = Unit
            override fun onFinish(msg: String) {
                mainHandler.post {
                    if (attemptId != evaluationAttemptId) return@post
                    if (state != RecordingState.RECORDING && state != RecordingState.EVALUATING) return@post
                    controller?.release()
                    controller = null
                    val summary = parseTencentEvaluationSummary(msg, evaluationText, readingTarget.targetType)
                    if (summary.wrongCharacterIndexes.isNotEmpty()) {
                        val diagnostic = extractTencentDiagnostic(msg, evaluationText)
                        val rawEvaluation = prepareTencentEvaluationDiagnostic(msg)
                        scope.launch {
                            evaluationViewModel.recordFailedDiagnostic(
                                readingTarget, java.util.UUID.randomUUID().toString(),
                                summary.wrongCharacterIndexes, evaluationReference.textMode,
                                evaluationText.length, diagnostic.first, diagnostic.second,
                                rawEvaluation.first, rawEvaluation.second
                            )
                        }
                    }
                    wordSummaries[wordIndex] = summary
                    state = RecordingState.FINISHED
                    val correctReadingsThisAttempt = if (readingTarget.targetType == "character") {
                        (evaluationText.count { it.isChineseCharacter() } - summary.wrongCharacterCount)
                            .coerceAtLeast(0)
                    } else if (summary.wrongCharacterCount == 0) {
                        1
                    } else {
                        0
                    }
                    if (correctReadingsThisAttempt > 0) {
                        displayedCorrectReadingCount = onCorrectReadings(readingTarget, correctReadingsThisAttempt)
                    }
                    if (isWordGroup && wordIndex == wordTargets.lastIndex) {
                        evaluationSummary = EvaluationSummary(
                            targetType = target.targetType,
                            wrongCharacterCount = wordSummaries.filterNotNull().sumOf { it.wrongCharacterCount }
                        )
                        message = "三个词都读完了，看看这次的成绩吧！"
                    } else if (isWordGroup) {
                        message = "“${readingTarget.displayText}”完成，点击下一词继续"
                    } else {
                        evaluationSummary = summary
                        message = if (inline && (correctReadingsThisAttempt > 0 || summary.wrongCharacterIndexes.isNotEmpty())) {
                            ""
                        } else if (correctReadingsThisAttempt > 0) {
                            if (displayedCorrectReadingCount >= target.requiredCorrectReadings()) {
                                "太棒啦，这一项读完了！"
                            } else if (target.targetType == "character") {
                                "读对啦！"
                            } else {
                                "读对啦！再读 ${target.requiredCorrectReadings() - displayedCorrectReadingCount} 次就完成。"
                            }
                        } else if (summary.wrongCharacterIndexes.isNotEmpty()) {
                            ""
                        } else {
                            "这次评测未识别出有效朗读，请重试"
                        }
                    }
                }
            }
            override fun onError(request: OralEvaluationRequest?, clientException: ClientException?, serverException: ServerException?, response: String?) {
                mainHandler.post {
                    if (attemptId != evaluationAttemptId) return@post
                    controller?.release()
                    controller = null
                    state = RecordingState.ERROR
                    message = "评测连接失败，请重新开始"
                }
            }
        }
        val recordListener = object : OralEvaluationStateListener {
            override fun onStartRecord(request: OralEvaluationRequest?) = Unit
            override fun onStopRecord(request: OralEvaluationRequest?) = Unit
            override fun onAudioData(audioData: ShortArray?, readBufferLength: Int) = Unit
        }
        controller = TAIOralController(
            TAIConfig.Builder()
                .appID(preparedSession.appId)
                .secretID(preparedSession.secretId)
                .secretKey(preparedSession.secretKey)
                .token(preparedSession.token)
                .apiParams(apiParams)
                .enableVAD(false)
                .dataSource(TAIDataSource(false))
                .build()
        ).also {
            state = RecordingState.RECORDING
            message = "正在录音，请朗读…"
            it.startOralEvaluation(listener, recordListener)
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        onStopPlayback()
        evaluationSummary = null
        wordSummaries[currentWordIndex] = null
        // 每次开始前检查剩余有效期；距过期不足 5 分钟时才刷新，其他情况不发网络请求。
        state = RecordingState.PREPARING
        message = "正在准备朗读…"
        scope.launch {
            val readingTarget = wordTargets[currentWordIndex]
            val credentials = evaluationViewModel.credentialsForEvaluation().getOrElse { error ->
                state = RecordingState.ERROR
                message = error.message ?: "评测准备失败，请重新开始"
                return@launch
            }
            sessions[currentWordIndex] = credentials
            val referenceResult = if (readingTarget.targetType == "character") {
                // 主字是 TEXT_MODE=0，参考文本只与本轮尚需次数有关，无须等待云函数。
                Result.success(evaluationViewModel.prepareCharacterEvaluation(readingTarget, characterReadingsNeeded()))
            } else {
                // 词句优先使用弹层打开时的当日内存预取；缓存未命中才正常请求。
                evaluationViewModel.prepareCachedEvaluation(readingTarget)
            }
            referenceResult
                .onSuccess { reference ->
                    startSdkEvaluation(credentials, readingTarget, currentWordIndex, reference)
                }
                .onFailure { error ->
                    state = RecordingState.ERROR
                    message = error.message ?: "正在准备发音，请稍后再试"
                }
        }
    }

    fun moveToNextWord() {
        val nextIndex = currentWordIndex + 1
        if (nextIndex > wordTargets.lastIndex) return
        currentWordIndex = nextIndex
        evaluationSummary = null
        when {
            sessions[nextIndex] != null -> {
                state = RecordingState.READY
                message = "轮到“${wordTargets[nextIndex].displayText}”了，点击开始就可以朗读"
            }
            sessionErrors[nextIndex] != null -> {
                state = RecordingState.ERROR
                message = sessionErrors[nextIndex] ?: "评测准备失败，请关闭后重试"
            }
            else -> {
                state = RecordingState.PREPARING
                message = "正在准备“${wordTargets[nextIndex].displayText}”的评测…"
            }
        }
    }

    fun restartWordGroup() {
        wordSummaries.indices.forEach { wordSummaries[it] = null }
        currentWordIndex = 0
        evaluationSummary = null
        state = if (sessions.firstOrNull() != null) RecordingState.READY else RecordingState.PREPARING
        message = if (state == RecordingState.READY) "从“${wordTargets.first().displayText}”开始，再读一遍吧" else "正在准备朗读…"
    }

    LaunchedEffect(target) {
        state = RecordingState.PREPARING
        message = if (isWordGroup) "正在准备 3 个词的评测…" else "正在准备朗读…"
        // 同一份页面凭证可供各词共用；并发请求会合并为一次领证调用。
        wordTargets.forEachIndexed { index, wordTarget ->
            launch {
                evaluationViewModel.credentialsForEvaluation()
                    .onSuccess { preparedSession ->
                        sessions[index] = preparedSession
                        if (index == currentWordIndex) {
                            state = RecordingState.READY
                            message = if (isWordGroup) {
                                "先读“${wordTarget.displayText}”，点击开始就可以朗读"
                            } else {
                                "准备好了，点击开始就可以朗读"
                            }
                            if (startImmediately) startRecording()
                        }
                    }
                    .onFailure { error ->
                        sessionErrors[index] = error.message ?: "评测准备失败，请关闭后重试"
                        if (index == currentWordIndex) {
                            state = RecordingState.ERROR
                            message = sessionErrors[index] ?: "评测准备失败，请关闭后重试"
                        }
                    }
            }
        }
    }

    val accent = when (state) {
        RecordingState.RECORDING, RecordingState.EVALUATING -> Coral
        RecordingState.FINISHED -> Leaf
        else -> Sky
    }
    val light = when (state) {
        RecordingState.RECORDING, RecordingState.EVALUATING -> CoralLight
        RecordingState.FINISHED -> LeafLight
        else -> SkyLight
    }
    val isWaitingForResult = state == RecordingState.EVALUATING
    val hasWrongCharacters = evaluationSummary?.wrongCharacterIndexes?.isNotEmpty() == true
    val hasCompletedCurrentTarget = displayedCorrectReadingCount >= target.requiredCorrectReadings()
    // 只在当前弹窗生命期内保留点读高亮；关闭后自然还原，绝不写入学习状态。
    var pointReadCharacterIndex by remember(target) { mutableStateOf<Int?>(null) }
    // 手指按住内容时突出其所在的朗读区域，松手后立即还原。
    var isReadingContentPressed by remember(target) { mutableStateOf(false) }
    fun handleLongPress(character: Char, characterIndex: Int) {
        val clickedContent = target.clickedReadingContent(character, characterIndex)
        discardEvaluationForManualRead()
        // 词、句点读时仅临时高亮被长按的字，不能与错读红色混用。
        pointReadCharacterIndex = characterIndex.takeIf {
            target.targetType == "word" || target.targetType == "sentence"
        }
        context.sendBroadcast(
            Intent(ACTION_LITERACY_READING_CONTENT_CLICKED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_READING_TARGET_TYPE, target.targetType)
                .putExtra(EXTRA_READING_CONTENT, clickedContent)
                .putExtra(EXTRA_READING_CLICKED_CHARACTER, character.toString())
                .putExtra(EXTRA_READING_CHARACTER_INDEX, characterIndex)
        )
        onSpeak(target, clickedContent)
        if (target.contentSource == ReadingContentSource.TASK && target.targetType == "character") {
            onCharacterAudioPointRead(target)
        }
        message = "正在播放正确读音"
        scope.launch {
            evaluationViewModel.recordHelpRequest(target, character, characterIndex)
            // 记录异常不影响已经开始的点读；下次长按仍会继续尝试记录。
        }
    }
    fun performAction() {
        if (state == RecordingState.RECORDING) {
            state = RecordingState.EVALUATING
            message = "正在生成评测结果…"
            controller?.stopOralEvaluation()
            // 腾讯 SDK 在无有效语音时偶发不回调；超时后恢复为可重试状态，不能锁死其它练习项。
            val stoppedAttemptId = evaluationAttemptId
            mainHandler.postDelayed({
                if (stoppedAttemptId == evaluationAttemptId && state == RecordingState.EVALUATING) {
                    android.util.Log.w("ReadingEvaluation", "evaluation result timeout after manual finish")
                    controller?.cancelOralEvaluation()
                    controller?.release()
                    controller = null
                    state = RecordingState.ERROR
                    message = "评测结果未返回，请重新开始"
                }
            }, 15_000)
        } else if (state == RecordingState.FINISHED && hasCompletedCurrentTarget) {
            close()
        } else if (state == RecordingState.FINISHED && isWordGroup && currentWordIndex < wordTargets.lastIndex) {
            moveToNextWord()
        } else if (state == RecordingState.FINISHED && isWordGroup) {
            restartWordGroup()
        } else {
            startRecording()
        }
    }
    val actionLabel = when {
        state == RecordingState.RECORDING -> "读完"
        state == RecordingState.FINISHED && hasCompletedCurrentTarget -> "完成"
        state == RecordingState.FINISHED && isWordGroup && currentWordIndex < wordTargets.lastIndex -> "下一词"
        state == RecordingState.FINISHED && isWordGroup -> "再读一遍"
        state == RecordingState.FINISHED && hasWrongCharacters -> "重试"
        state == RecordingState.FINISHED -> "开始"
        state == RecordingState.ERROR -> "重试"
        else -> "开始"
    }
    val actionEnabled = sessions.getOrNull(currentWordIndex) != null &&
        (state == RecordingState.READY || state == RecordingState.RECORDING || state == RecordingState.FINISHED || state == RecordingState.ERROR)
    ReadingDialogContainer(inline = inline, onDismissRequest = ::close) {
        if (inline) {
            InlineReadingPracticeRow(
                target = target,
                correctReadingCount = displayedCorrectReadingCount,
                requiredReadingCount = target.requiredCorrectReadings(),
                state = state,
                message = message,
                accent = accent,
                actionLabel = actionLabel,
                actionEnabled = actionEnabled,
                wrongCharacterIndexes = evaluationSummary?.wrongCharacterIndexes.orEmpty(),
                pointReadCharacterIndex = pointReadCharacterIndex,
                contentPressed = isReadingContentPressed,
                onPressChanged = { isReadingContentPressed = it },
                onLongPress = ::handleLongPress,
                onAction = ::performAction
            )
        } else Card(
            modifier = Modifier.widthIn(max = 500.dp).padding(24.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Background)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = ::close, enabled = true, colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF748185))) { Icon(Icons.Filled.Close, if (isWaitingForResult) "正在生成结果，请稍候" else "关闭") }
                }
                Surface(shape = CircleShape, color = light, modifier = Modifier.size(82.dp)) { Box(contentAlignment = Alignment.Center) { Text(if (state == RecordingState.FINISHED) "🎯" else "🎙️", fontSize = 42.sp) } }
                Text(when (state) {
                    RecordingState.RECORDING -> "正在听你读…"
                    RecordingState.EVALUATING -> "正在生成评测结果…"
                    else -> if (isWordGroup) "词语朗读 ${currentWordIndex + 1}/${wordTargets.size}" else "朗读练习"
                }, style = MaterialTheme.typography.titleLarge, color = accent)
                PracticeProgressRating(
                    correctReadingCount = displayedCorrectReadingCount,
                    requiredReadingCount = target.requiredCorrectReadings(),
                    evaluationSummary = evaluationSummary,
                    hideRemainingReadingHint = target.targetType == "character"
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isReadingContentPressed) Sky.copy(alpha = .34f) else light,
                    border = if (isReadingContentPressed) {
                        androidx.compose.foundation.BorderStroke(2.dp, Sky)
                    } else {
                        null
                    }
                ) {
                    DialogReadingContent(
                        target = target,
                        // 评测结果生成中也允许点读：点击会取消评测并丢弃迟到回包。
                        enabled = true,
                        activeWordIndex = if (isWordGroup) currentWordIndex else null,
                        wordSummaries = if (isWordGroup) wordSummaries else emptyList(),
                        wrongCharacterIndexes = if (isWordGroup) emptySet() else evaluationSummary?.wrongCharacterIndexes.orEmpty(),
                        pointReadCharacterIndex = pointReadCharacterIndex,
                        onPressChanged = { isReadingContentPressed = it },
                        onLongPress = ::handleLongPress
                    )
                }
                if (state == RecordingState.RECORDING || isWaitingForResult) RecordingAnimation(accent)
                Button(
                    onClick = ::performAction,
                    enabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** 首页学习弹层的行内朗读区：内容、五点动画和操作按钮始终保持在同一行。 */
@Composable
private fun InlineReadingPracticeRow(
    target: ReadingTarget,
    correctReadingCount: Int,
    requiredReadingCount: Int,
    state: RecordingState,
    message: String,
    accent: Color,
    actionLabel: String,
    actionEnabled: Boolean,
    wrongCharacterIndexes: Set<Int>,
    pointReadCharacterIndex: Int?,
    contentPressed: Boolean,
    onPressChanged: (Boolean) -> Unit,
    onLongPress: (Char, Int) -> Unit,
    onAction: () -> Unit
) {
    val completed = correctReadingCount >= requiredReadingCount
    val panelAccent = when {
        completed -> Leaf
        state == RecordingState.PREPARING || state == RecordingState.RECORDING || state == RecordingState.EVALUATING -> Sky
        wrongCharacterIndexes.isNotEmpty() -> StudyErrorBorder
        else -> Sky
    }
    StudyPracticeRow(completed = completed, accent = panelAccent,
        contentPressed = contentPressed, actionLabel = actionLabel, actionEnabled = actionEnabled,
        message = message, isError = state == RecordingState.ERROR || wrongCharacterIndexes.isNotEmpty(),
        showHintBelowDots = state == RecordingState.PREPARING || state == RecordingState.RECORDING || state == RecordingState.EVALUATING,
        readingWidth = when (target.targetType) { "character" -> 85.dp; "word" -> 115.dp; else -> null },
        onAction = onAction,
        stars = { StudyStarRail(correctReadingCount, requiredReadingCount) },
        dots = { PracticeRecordingDots(active = state == RecordingState.PREPARING || state == RecordingState.RECORDING || state == RecordingState.EVALUATING) },
        reading = {
            DialogReadingContent(target = target, enabled = true, studyInline = true, wrongCharacterIndexes = wrongCharacterIndexes,
                pointReadCharacterIndex = pointReadCharacterIndex, onPressChanged = onPressChanged, onLongPress = onLongPress)
        })
}

@Composable
private fun PracticeRecordingDots(active: Boolean) {
    if (active) RecordingAnimation(Sky, large = true)
}

@Composable
private fun ReadingDialogContainer(
    inline: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (inline) {
        content()
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            content()
        }
    }
}

@Composable
private fun RecordingAnimation(color: Color, large: Boolean = false) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "recording")
    // 圆点始终占用相同空间，只在自己的图层内缩放，避免把下方按钮来回顶动。
    val dotSize = if (large) 24.dp else 12.dp
    Row(
        modifier = Modifier.width(if (large) 152.dp else 80.dp).height(if (large) 28.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (large) 8.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val scale by transition.animateFloat(
                initialValue = .65f,
                targetValue = 1.15f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(620),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(
                        offsetMillis = index * 110,
                        offsetType = androidx.compose.animation.core.StartOffsetType.FastForward
                    )
                ), label = "recording-dot-$index"
            )
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(dotSize).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            ) {}
        }
    }
}

@Composable
private fun PracticeProgressRating(
    correctReadingCount: Int,
    requiredReadingCount: Int,
    evaluationSummary: EvaluationSummary?,
    hideRemainingReadingHint: Boolean = false
) {
    val stars = "★".repeat(correctReadingCount) + "☆".repeat(requiredReadingCount - correctReadingCount)
    Text(stars, color = Wheat, fontSize = 32.sp, letterSpacing = 3.sp)
    Text(
        when (evaluationSummary?.wrongCharacterCount) {
            null -> if (hideRemainingReadingHint) "请连续朗读 ${requiredReadingCount} 次" else "读对 $requiredReadingCount 次，就能点亮全部星星"
            0 -> if (correctReadingCount >= requiredReadingCount) {
                "全部星星点亮啦！"
            } else if (hideRemainingReadingHint) {
                "读对啦！"
            } else {
                "读对啦！还差 ${requiredReadingCount - correctReadingCount} 次"
            }
            else -> if (hideRemainingReadingHint) "再试一次吧。" else "读错 ${evaluationSummary.wrongCharacterCount} 个字，这次不点亮星星"
        },
        color = Color(0xFF7F8B8D),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun DialogReadingContent(
    target: ReadingTarget,
    enabled: Boolean,
    studyInline: Boolean = false,
    activeWordIndex: Int? = null,
    wordSummaries: List<EvaluationSummary?> = emptyList(),
    wrongCharacterIndexes: Set<Int> = emptySet(),
    pointReadCharacterIndex: Int? = null,
    onPressChanged: (Boolean) -> Unit = {},
    onLongPress: (Char, Int) -> Unit
) {
    // 单个词与字、句一样按逐字结果标红；仅旧版多词组模式才使用分词卡片布局。
    if (target.targetType != "word" || activeWordIndex == null) {
        DialogCharacterText(
            target = target.displayText,
            targetType = target.targetType,
            enabled = enabled,
            studyInline = studyInline,
            wrongCharacterIndexes = wrongCharacterIndexes,
            pointReadCharacterIndexes = pointReadCharacterIndex?.let(::setOf).orEmpty(),
            onPressChanged = onPressChanged,
            onLongPress = onLongPress
        )
        return
    }

    val words = target.displayText.wordSegments()
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        words.forEachIndexed { index, (word, startIndex) ->
            val summary = wordSummaries.getOrNull(index)
            val isActive = activeWordIndex == index
            val cardColor = when {
                isActive -> CoralLight
                summary?.wrongCharacterCount == 0 -> LeafLight
                summary != null -> CoralLight.copy(alpha = .7f)
                else -> Color.White.copy(alpha = .75f)
            }
            val cardBorder = when {
                isActive -> Coral
                summary?.wrongCharacterCount == 0 -> Leaf
                summary != null -> Coral.copy(alpha = .7f)
                else -> Sky.copy(alpha = .25f)
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = cardColor,
                border = androidx.compose.foundation.BorderStroke(if (isActive) 2.dp else 1.dp, cardBorder)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isActive) {
                        Text("正在朗读", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    } else if (summary != null) {
                        Text(
                            if (summary.wrongCharacterCount == 0) "读对了" else "再试试",
                            color = if (summary.wrongCharacterCount == 0) Leaf else Coral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    DialogCharacterText(
                        target = word,
                        targetType = "word",
                        enabled = enabled,
                        wrongCharacterIndexes = summary?.wrongCharacterIndexes.orEmpty(),
                        pointReadCharacterIndexes = pointReadCharacterIndex
                            ?.takeIf { it in startIndex until startIndex + word.length }
                            ?.let { setOf(it - startIndex) }
                            .orEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 5.dp),
                        onPressChanged = onPressChanged,
                        onLongPress = { character, characterIndex -> onLongPress(character, startIndex + characterIndex) }
                    )
                }
            }
        }
    }
}

private fun String.wordSegments(): List<Pair<String, Int>> {
    var searchStart = 0
    return split(Regex("[，,、\\s]+"))
        .filter { it.isNotBlank() }
        .map { word ->
            val index = indexOf(word, startIndex = searchStart).also { searchStart = it + word.length }
            word to index
        }
}

/** 长按词组中的任意字时，对外广播并兜底朗读它所属的完整词。 */
private fun ReadingTarget.clickedReadingContent(character: Char, characterIndex: Int): String = when (targetType) {
    "character" -> character.toString()
    "word" -> displayText.wordSegments()
        .firstOrNull { (word, startIndex) -> characterIndex in startIndex until startIndex + word.length }
        ?.first
        ?: displayText
    else -> displayText
}

@Composable
internal fun DialogCharacterText(
    target: String,
    targetType: String? = null,
    enabled: Boolean = true,
    studyInline: Boolean = false,
    wrongCharacterIndexes: Set<Int> = emptySet(),
    pointReadCharacterIndexes: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
    onPressChanged: (Boolean) -> Unit = {},
    onLongPress: (Char, Int) -> Unit
) {
    val isSentence = targetType == "sentence"
    val text = buildAnnotatedString {
        target.forEachIndexed { index, character ->
            val color = if (character.isChineseCharacter() && index in wrongCharacterIndexes)
                (if (studyInline) StudyErrorText else EvaluationErrorRed) else Ink
            // 点读高亮独立于错读样式：即使该字此前读错，也不改变错误红的语义。
            val background = when {
                index in pointReadCharacterIndexes -> Sky.copy(alpha = .42f)
                else -> Color.Transparent
            }
            withStyle(SpanStyle(color = color, background = background)) { append(character) }
        }
    }
    var layoutResult by remember(target) { mutableStateOf<TextLayoutResult?>(null) }
    fun characterIndexAt(position: Offset): Int? {
        val currentLayout = layoutResult ?: return null
        val rawCharacterIndex = currentLayout.getOffsetForPosition(position)
        // TextLayoutResult 在汉字右半侧常返回“该字之后”的插入位置。
        // 例如单字“令”右半侧会返回 1；按实际字形边界反查后仍应命中 0。
        return listOf(rawCharacterIndex, rawCharacterIndex - 1)
            .firstOrNull { index ->
                target.getOrNull(index)?.isChineseCharacter() == true &&
                    currentLayout.getBoundingBox(index).contains(position)
            }
            ?: rawCharacterIndex
    }
    // 句子将文本块置于内容区中央，但固定一个略小于内容区的阅读宽度，
    // 让换行后的每一行从同一条左边线开始，读起来更自然。
    Box(
        modifier = modifier.fillMaxWidth().padding(if (studyInline) 5.dp else 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth(if (isSentence) .92f else 1f)
                // 短按不播放，长按时再根据文字布局换算出精确的汉字位置。
                // 首次打开弹层时 TextLayoutResult 由下一帧才回写；将其作为键可确保
                // 手势协程在布局就绪后重新绑定，不会因首次长按读到空布局而被静默忽略。
                .pointerInput(target, enabled, layoutResult) {
                    detectTapGestures(
                        onPress = { position ->
                            if (enabled && characterIndexAt(position)?.let(target::getOrNull)?.isChineseCharacter() == true) {
                                onPressChanged(true)
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    onPressChanged(false)
                                }
                            }
                        },
                        onLongPress = { position ->
                            if (!enabled) return@detectTapGestures
                            val currentLayout = layoutResult
                            if (currentLayout == null) {
                                android.util.Log.w("LiteracyPointRead", "长按未处理：文字布局尚未就绪，text=$target")
                                return@detectTapGestures
                            }
                            val characterIndex = characterIndexAt(position) ?: return@detectTapGestures
                            target.getOrNull(characterIndex)
                                ?.takeIf { it.isChineseCharacter() }
                                ?.let { character ->
                                    android.util.Log.d(
                                        "LiteracyPointRead",
                                        "已捕获长按，text=$target，character=$character，index=$characterIndex"
                                    )
                                    onLongPress(character, characterIndex)
                                }
                                ?: android.util.Log.d(
                                    "LiteracyPointRead",
                                    "长按未命中汉字，text=$target，index=$characterIndex"
                                )
                        }
                    )
                },
            style = MaterialTheme.typography.bodyLarge.copy(
                textAlign = if (isSentence) TextAlign.Start else TextAlign.Center,
                // 朗读弹层是孩子跟读时的主视觉：词、句需要明显大于普通正文，
                // 句子保留较小一级以便较长内容在平板上自然换行。
                fontSize = when {
                    studyInline && targetType == "word" -> 32.sp
                    studyInline && targetType == "sentence" -> 22.sp
                    studyInline -> 44.sp
                    targetType == "word" -> 40.sp
                    targetType == "sentence" -> 34.sp
                    else -> if (target.count { it.isChineseCharacter() } == 1) 68.sp else 34.sp
                },
                letterSpacing = when (targetType) {
                    "word", "sentence" -> 1.5.sp
                    else -> 0.sp
                },
                fontWeight = if (studyInline) FontWeight.Bold else FontWeight.Normal,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default
            ),
            onTextLayout = { layoutResult = it }
        )
    }
}

private fun Char.isChineseCharacter(): Boolean = this in '\u4E00'..'\u9FFF'

/**
 * SDK 回调的内容只保留在当前弹层。当前 TEXT_MODE=0 的实际回调中，错读时 MatchTag
 * 仍可能为 0，不能作为正误依据；优先按逐字 PronAccuracy 判定，缺少分数时才回退 MatchTag。
 */
private fun parseTencentEvaluationSummary(
    response: String,
    expectedText: String,
    targetType: String
): EvaluationSummary {
    val root = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(response) }.getOrNull()
    val words = root?.firstTencentWords().orEmpty()
    val expectedCharacterIndexes = expectedText.mapIndexedNotNull { index, character ->
        index.takeIf { character.isChineseCharacter() }
    }
    val wrongCharacterIndexes = if (words.isEmpty()) {
        // 回调本身没有有效逐词结果时不给出“读对”的假反馈。
        expectedCharacterIndexes.toSet()
    } else {
        words.toWrongCharacterIndexes(expectedText, expectedCharacterIndexes)
    }
    return EvaluationSummary(
        wrongCharacterIndexes = wrongCharacterIndexes,
        targetType = targetType
    )
}

/** 保留腾讯已知评分字段；Word 是腾讯返回字段，不是实际语音转写。 */
private fun extractTencentDiagnostic(response: String, expectedText: String): Pair<JsonArray, List<String>> {
    val words = runCatching { Json.parseToJsonElement(response).firstTencentWords().orEmpty() }.getOrDefault(emptyList())
    val fields = listOf("Word", "PronAccuracy", "MatchTag", "PronFluency", "PronCompletion", "TotalScore")
    val safeWords = words.take(80).map { word ->
        JsonObject(fields.mapNotNull { field ->
            word.stringValue(field, field.replaceFirstChar { it.lowercase() })
                ?.take(120)?.let { field to JsonPrimitive(it) }
        }.toMap())
    }
    val reasons = buildSet {
        if (words.isEmpty()) add("missing_words")
        words.forEach { word ->
            if (word.stringValue("PronAccuracy", "pronAccuracy")?.toDoubleOrNull()
                    ?.toPercentScore()?.let { it < 60.0 } == true) add("low_pron_accuracy")
            if (word.stringValue("PronAccuracy", "pronAccuracy")?.toDoubleOrNull() == null &&
                word.stringValue("MatchTag", "matchTag")?.toIntOrNull()?.let { it != 0 } == true)
                add("match_tag_fallback")
        }
        if (words.sumOf { it.stringValue("Word", "word").orEmpty().count(Char::isChineseCharacter) } <
            expectedText.count(Char::isChineseCharacter) && words.isNotEmpty()) add("unreturned_expected_characters")
        if (words.any { word ->
                val returned = word.stringValue("Word", "word").orEmpty().filter(Char::isChineseCharacter)
                returned.isNotEmpty() && !expectedText.contains(returned)
            }) add("mapping_uncertain")
    }
    return JsonArray(safeWords) to reasons.toList()
}

/** 保留腾讯回调的评测结构；凭证和音频等非评测内容不得写入诊断日志。 */
private fun prepareTencentEvaluationDiagnostic(response: String): Pair<JsonObject?, String> {
    val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull()
        ?: return null to "invalid_json"
    var redacted = false
    val forbidden = Regex("(?i)(secret|token|credential|authorization|password|audio|voice|base64|payload|private.?key|access.?key)")
    fun clean(value: JsonElement, depth: Int): JsonElement {
        if (depth > 24) return JsonPrimitive("[depth limit]").also { redacted = true }
        return when (value) {
            is JsonObject -> JsonObject(value.mapNotNull { (key, child) ->
                if (forbidden.containsMatchIn(key)) { redacted = true; null }
                else key to clean(child, depth + 1)
            }.toMap())
            is JsonArray -> JsonArray(value.map { clean(it, depth + 1) })
            else -> value
        }
    }
    val safe = clean(root, 0) as JsonObject
    if (safe.toString().toByteArray(Charsets.UTF_8).size > 64 * 1024) return null to "too_large"
    return safe to if (redacted) "redacted" else "complete"
}

/**
 * 腾讯会按字或词返回 Words。优先按返回文字在教学文本中的位置映射；识别字与参考字
 * 不一致时，回退为顺序映射，才能把“把甲读成乙”正确标红到教学文本中的甲。
 */
private fun List<JsonObject>.toWrongCharacterIndexes(
    expectedText: String,
    expectedCharacterIndexes: List<Int>
): Set<Int> {
    val expectedCharacters = expectedCharacterIndexes.joinToString("") { expectedText[it].toString() }
    var expectedCursor = 0
    return buildSet {
        this@toWrongCharacterIndexes.forEach { resultWord ->
            val returnedCharacters = resultWord.stringValue("Word", "word")
                .orEmpty()
                .filter { it.isChineseCharacter() }
            if (returnedCharacters.isEmpty()) return@forEach

            val matchedStart = expectedCharacters.indexOf(returnedCharacters, startIndex = expectedCursor)
            val characterStart = if (matchedStart >= 0) matchedStart else expectedCursor
            val mappedIndexes = expectedCharacterIndexes.drop(characterStart).take(returnedCharacters.length)
            expectedCursor = (characterStart + returnedCharacters.length).coerceAtMost(expectedCharacterIndexes.size)
            if (resultWord.isPronunciationWrong()) addAll(mappedIndexes)
        }
        // 对重复字朗读，“花花”不能通过参考文本“花花花”的第三次。腾讯未返回的
        // 参考字同样视为未读对；这也会让词、句漏读时维持原有的“不加星”行为。
        addAll(expectedCharacterIndexes.drop(expectedCursor))
    }
}

private fun JsonElement.firstTencentWords(): List<JsonObject>? = when (this) {
    is JsonObject -> {
        (this["Words"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.takeIf { it.isNotEmpty() }
            ?: values.firstNotNullOfOrNull { it.firstTencentWords() }
    }
    is JsonArray -> firstNotNullOfOrNull { it.firstTencentWords() }
    is JsonPrimitive -> contentOrNull
        ?.takeIf { it.trimStart().startsWith("{") || it.trimStart().startsWith("[") }
        ?.let { raw -> runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(raw) }.getOrNull() }
        ?.firstTencentWords()
    else -> null
}

private fun JsonObject.isPronunciationWrong(): Boolean {
    val word = stringValue("Word", "word")
    val characterCount = word?.count { it.isChineseCharacter() } ?: 0
    // TEXT_MODE=1 的结果可能在正式文本前包含 Word="*" 的额外音段。这些音段不映射
    // 到朗读目标中的任何汉字，不能被错误地按一个错字扣星。
    if (characterCount == 0) return false
    val matchTag = stringValue("MatchTag", "matchTag")?.toIntOrNull()
    val pronunciationAccuracy = stringValue("PronAccuracy", "pronAccuracy")?.toDoubleOrNull()
    val isWrong = when {
        // SDK 会返回 0~1 或 0~100 两种量纲，统一为百分制后以 60 分作为读对阈值。
        // TEXT_MODE=0 仍会有声学波动，不能用过低阈值掩盖；后续应切到携带指定拼音的
        // TEXT_MODE=1，从根本提高单字评测的稳定性。
        pronunciationAccuracy != null -> pronunciationAccuracy.toPercentScore() < 60.0
        matchTag != null -> matchTag != 0
        else -> false
    }
    return isWrong
}

private fun Double.toPercentScore(): Double = if (this <= 1.0) this * 100 else this

private fun JsonObject.stringValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    this[key]?.jsonPrimitive?.contentOrNull
}

@Composable
private fun ProfileScreen(
    childName: String, avatarUrl: String?, userId: String, onNavigate: (Page) -> Unit,
    onKnown: () -> Unit, onPending: () -> Unit, onLibrary: () -> Unit, onHelped: () -> Unit,
    onParentPasses: () -> Unit, onGenerateLiteracyTasks: () -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val libraryState by libraryViewModel.uiState.collectAsState()
    LaunchedEffect(userId) { libraryViewModel.load(userId) }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            contentAlignment = Alignment.TopCenter) {
            val contentWidth = maxWidth.coerceAtMost(720.dp)
            val columns = gardenMenuColumns(contentWidth.value, LocalDensity.current.fontScale)
            val menus = listOf(
                GardenMenu("已认识的字", "看看我已经掌握了哪些字", R.drawable.garden_entry_book, Coral,
                    Color(0xFFFFF3ED), Color(0xFFFFEEE7), Color(0xFFFFCBBD), onKnown),
                GardenMenu("查看待认识的字", "这些字正等着学习", R.drawable.garden_entry_pencil, Leaf,
                    Color(0xFFFAFFEF), Color(0xFFF1FFDE), Color(0xFFD8F7A3), onPending),
                GardenMenu("字库", "收集我的汉字", R.drawable.garden_entry_books, Sky,
                    Color(0xFFF0FBFF), Color(0xFFE7F6FF), Color(0xFFBBE6FF), onLibrary),
                GardenMenu("帮助过的内容", "我请求朗读过的词和句", R.drawable.garden_entry_megaphone, Color(0xFF996000),
                    Color(0xFFFFFAE9), Color(0xFFFFF7DF), Color(0xFFFFE89D), onHelped),
                GardenMenu("通过记录", "查看学习通过记录", R.drawable.garden_entry_clipboard, Color(0xFF6330B6),
                    Color(0xFFF7F2FF), Color(0xFFF0EBFF), Color(0xFFDECDFF), onParentPasses),
                GardenMenu("智能添加识字", "输入汉字，生成词和句", R.drawable.garden_entry_add_blank, Leaf,
                    Color(0xFFF3FFF3), Color(0xFFE9FFE9), Color(0xFFBDF5BD), onGenerateLiteracyTasks)
            )
            LazyColumn(Modifier.width(contentWidth).fillMaxHeight(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    if (columns == 2) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProfileIdentityCard(childName, avatarUrl, Modifier.width(350.dp))
                            if (libraryState.hasLocalData) {
                                ProfileMetricCard(libraryState.characters.size.toString(), Modifier.width(138.dp))
                            }
                            GardenArt(R.drawable.garden_mascot_welcome, Modifier.size(116.dp))
                        }
                    } else {
                        ProfileIdentityCard(childName, avatarUrl, Modifier.fillMaxWidth())
                    }
                }
                if (libraryState.hasLocalData && columns == 1) item {
                    ProfileMetricCard(libraryState.characters.size.toString(), Modifier.fillMaxWidth())
                }
                items(menus.chunked(columns)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { menu -> ProfileMenuCard(menu, Modifier.weight(1f)) }
                    }
                }
            }
        }
        LiteracyBottomNav(Page.PROFILE, onNavigate)
    }
}

@Composable
private fun ProfileIdentityCard(childName: String, avatarUrl: String?, modifier: Modifier) {
    ProfileDecoratedCard(modifier.heightIn(min = 108.dp), Color(0xFFFFFCF6), Color(0xFFFFFDF5), Color(0xFFFFCCAC)) {
        Image(painterResource(R.drawable.profile_floral_corner), null,
            Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 5.dp)
                .width(148.dp).height(31.dp), contentScale = ContentScale.FillBounds)
        Image(painterResource(R.drawable.profile_floral_corner), null,
            Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 5.dp)
                .width(106.dp).height(31.dp).graphicsLayer { scaleX = -1f },
            contentScale = ContentScale.FillBounds)
        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = avatarUrl, contentDescription = "$childName 的头像",
                placeholder = painterResource(R.drawable.garden_avatar_child), error = painterResource(R.drawable.garden_avatar_child),
                fallback = painterResource(R.drawable.garden_avatar_child), contentScale = ContentScale.Crop,
                modifier = Modifier.size(84.dp).clip(CircleShape).border(3.dp, Wheat, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(childName, modifier = Modifier.weight(1f, fill = false),
                        color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    GardenArt(R.drawable.garden_study_rays, Modifier.size(28.dp))
                }
                Text("今天也在认真认识汉字呀！", color = Color(0xFF687D9F), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProfileMetricCard(value: String, modifier: Modifier) {
    Surface(modifier.heightIn(min = 108.dp), shape = RoundedCornerShape(22.dp), color = LeafLight.copy(alpha = .97f),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White), shadowElevation = 4.dp) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(value, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Leaf)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                GardenArt(R.drawable.garden_sprout_shaded, Modifier.size(25.dp))
                Text("累计认识汉字", color = Leaf, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GenerateLiteracyTasksDialog(
    onDismiss: () -> Unit,
    onPreview: suspend (String) -> Result<LiteracyTasksPreview>,
    onSave: suspend (LiteracySaveDestination, String, List<GeneratedLiteracyTask>) -> Result<SavedLiteracyTasks>,
    helpedCharacter: String? = null
) {
    var characters by remember { mutableStateOf(helpedCharacter.orEmpty()) }
    var preview by remember { mutableStateOf<LiteracyTasksPreview?>(null) }
    var editableTasks by remember { mutableStateOf<List<EditableLiteracyTask>>(emptyList()) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val inputResult = parseLiteracyCharacterInput(characters)
    val normalizedCharacters = inputResult.characters
    val hasTasksToSave = editableTasks.isNotEmpty()
    LaunchedEffect(helpedCharacter) {
        if (helpedCharacter == null) return@LaunchedEffect
        isWorking = true
        onPreview(helpedCharacter).onSuccess { loaded ->
            preview = loaded
            editableTasks = loaded.tasks.map { task ->
                EditableLiteracyTask(task.character,
                    task.words.joinToString("、") { it.text }, task.sentence.text)
            }
        }.onFailure { errorMessage = it.message ?: "加载识字内容失败，请稍后重试" }
        isWorking = false
    }
    val saveEditedTasks: (LiteracySaveDestination) -> Unit = { destination ->
        coroutineScope.launch {
            val tasks = editableTasks.map {
                GeneratedLiteracyTask(
                    character = it.character,
                    words = it.wordsText.split(Regex("[、，,\\s]+")).filter(String::isNotBlank).map { text ->
                        GeneratedLiteracyExample(text = text)
                    },
                    sentence = GeneratedLiteracyExample(text = it.sentence.trim())
                )
            }
            validateEditedLiteracyTasks(tasks)?.let {
                errorMessage = it
                return@launch
            }
            isWorking = true
            val result = onSave(destination, if (helpedCharacter != null) helpedCharacter else normalizedCharacters, tasks)
            if (result.isSuccess) onDismiss()
            else errorMessage = result.exceptionOrNull()?.message ?: "保存识字任务失败，请稍后重试"
            isWorking = false
        }
    }

    fun loadPreview() {
        coroutineScope.launch {
            val currentInput = if (helpedCharacter != null) LiteracyCharacterInput(helpedCharacter)
                else parseLiteracyCharacterInput(characters)
            currentInput.error?.let { errorMessage = it; return@launch }
            isWorking = true
            errorMessage = null
            onPreview(currentInput.characters).onSuccess { loaded ->
                preview = loaded
                editableTasks = loaded.tasks.map { EditableLiteracyTask(it.character, it.words.joinToString("、") { word -> word.text }, it.sentence.text) }
            }.onFailure { errorMessage = it.message ?: "生成识字内容失败，请稍后重试" }
            isWorking = false
        }
    }
    GardenDialogFrame(
        title = if (helpedCharacter != null) "添加到已认识的字" else "智能添加识字",
        subtitle = if (preview == null && helpedCharacter == null) "输入汉字，自动生成学习内容" else "字不可修改；词和句子可按需要修改",
        onDismiss = onDismiss, closeEnabled = !isWorking,
        maxDialogWidth = 650.dp, maxDialogHeight = 450.dp
    ) {
        item {
            GardenPanel(tint = SkyLight) {
                if (helpedCharacter != null) Text("目标字：$helpedCharacter", color = Ink, style = MaterialTheme.typography.titleLarge)
                else OutlinedTextField(value = characters, onValueChange = { characters = it; errorMessage = null },
                    enabled = !isWorking && preview == null, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("要添加的汉字") }, placeholder = { Text("例如：春夏秋冬") },
                    supportingText = { Text("支持空格、顿号或逗号分隔；去重后一次最多 12 个字") },
                    isError = characters.isNotBlank() && inputResult.error != null, shape = RoundedCornerShape(20.dp))
                Text("每个字生成词语和句子。词语优先使用字库里的字加本次输入字；句子允许少量字库外生字，不限制字数。", color = Ink)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                preview?.knownCharacters?.takeIf { it.isNotEmpty() }?.let {
                    GardenMetricStrip("以下汉字已在字库中，仍已生成内容，可按需要删除：${it.joinToString("、")}")
                }
                preview?.skippedExistingCharacters?.takeIf { it.isNotEmpty() }?.let {
                    GardenMetricStrip("已存在待认识任务，不会覆盖：${it.joinToString("、")}")
                }
                preview?.skippedRecognizedCharacters?.takeIf { it.isNotEmpty() }?.let {
                    GardenMetricStrip("以下汉字已在已认识中：添加到待认识会转入待认识；添加到已认识会置顶：${it.joinToString("、")}")
                }
            }
        }
        items(editableTasks, key = { it.character }) { task ->
            EditableLiteracyTaskCard(task,
                isKnownCharacter = preview?.knownCharacters?.contains(task.character) == true,
                isRecognizedCharacter = preview?.skippedRecognizedCharacters?.contains(task.character) == true,
                onWordsChange = { words -> editableTasks = editableTasks.map { if (it.character == task.character) it.copy(wordsText = words) else it }; errorMessage = null },
                onSentenceChange = { sentence -> editableTasks = editableTasks.map { if (it.character == task.character) it.copy(sentence = sentence) else it }; errorMessage = null },
                onDelete = { editableTasks = editableTasks.filter { it.character != task.character }; errorMessage = null },
                canDelete = helpedCharacter == null)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                errorMessage?.let { Text(it, color = EvaluationErrorRed) }
                if (preview != null && !hasTasksToSave) Text("暂无可添加的字词句，可重新生成。", color = Ink)
                if (isWorking) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Sky); Spacer(Modifier.width(12.dp)); Text("正在处理…", color = Ink)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (helpedCharacter == null || preview == null) Button(onClick = ::loadPreview, enabled = !isWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = Sky)) {
                        Text(if (helpedCharacter != null) "重试加载" else if (preview == null) "生成内容" else "重新生成内容")
                    }
                    if (preview != null) {
                        if (helpedCharacter == null) Button(onClick = { saveEditedTasks(LiteracySaveDestination.PENDING) },
                            enabled = !isWorking && hasTasksToSave, colors = ButtonDefaults.buttonColors(containerColor = Wheat, contentColor = Ink)) { Text("添加到待认识") }
                        Button(onClick = { saveEditedTasks(LiteracySaveDestination.RECOGNIZED) }, enabled = !isWorking && hasTasksToSave,
                            colors = ButtonDefaults.buttonColors(containerColor = Leaf)) { Text(if (helpedCharacter != null) "确认并添加到已认识" else "添加到已认识") }
                    }
                }
            }
        }
    }
}

private enum class LiteracySaveDestination { PENDING, RECOGNIZED }

private data class EditableLiteracyTask(
    val character: String,
    val wordsText: String,
    val sentence: String
)

@Composable
private fun EditableLiteracyTaskCard(
    task: EditableLiteracyTask,
    isKnownCharacter: Boolean,
    isRecognizedCharacter: Boolean,
    onWordsChange: (String) -> Unit,
    onSentenceChange: (String) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean = true
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Background, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = task.character,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                label = { Text("字（不可编辑）") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Line,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            if (isKnownCharacter) {
                Surface(shape = RoundedCornerShape(12.dp), color = WheatLight) {
                    Text(
                        "该字已在字库中",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = Ink
                    )
                }
            }
            if (isRecognizedCharacter) {
                Surface(shape = RoundedCornerShape(12.dp), color = WheatLight) {
                    Text(
                        "该字已在已认识的字中",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = Ink
                    )
                }
            }
            OutlinedTextField(
                value = task.wordsText,
                onValueChange = onWordsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("词语（可编辑）") },
                supportingText = { Text("用顿号、逗号或空格分隔，可保留 1～3 个词") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Sky,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            OutlinedTextField(
                value = task.sentence,
                onValueChange = onSentenceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("句子（可编辑）") },
                supportingText = { Text("不限制句子字数") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Sky,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (canDelete) TextButton(onClick = onDelete) {
                    Text("删除这组字词句", color = Coral)
                }
            }
        }
    }
}

/** 词数、词长和句长不作本地限制；词句归属到某个识字任务时必须包含该目标字。 */
private fun validateEditedLiteracyTasks(tasks: List<GeneratedLiteracyTask>): String? {
    tasks.forEach { task ->
        val invalidWord = task.words.firstOrNull { !it.text.contains(task.character) }
        if (invalidWord != null) {
            return "“${task.character}”的词语“${invalidWord.text}”必须包含该字"
        }
        if (!task.sentence.text.contains(task.character)) {
            return "“${task.character}”的句子必须包含该字"
        }
    }
    return null
}

@Composable
private fun HelpedCharactersScreen(
    userId: String,
    onBack: () -> Unit,
    onRecognized: (String) -> Unit,
    readingViewModel: ReadingEvaluationViewModel = hiltViewModel(),
    viewModel: HelpedCharactersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var selectedHelp by remember { mutableStateOf<HelpedContent?>(null) }
    var checkingHelpId by remember { mutableStateOf<String?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var alreadyRecognizedCharacter by remember { mutableStateOf<String?>(null) }
    var savedHelpId by remember { mutableStateOf<String?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val errorMessage = runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri))
                        .bufferedWriter(Charsets.UTF_8)
                        .use { it.write(content) }
                }
            }.exceptionOrNull()?.message
            exportMessage = errorMessage?.let { "保存下载文件失败：$it" } ?: "已下载 TXT 文档"
        }
    }
    LaunchedEffect(userId) { viewModel.load(userId) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.fillMaxHeight().widthIn(max = 720.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            BackHeader("帮助过的内容", "这些词和句请求朗读过", onBack) {
                Button(onClick = {
                    pendingExportContent = state.contents.toHelpedContentsTxt()
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    createDocumentLauncher.launch("帮助过的内容_$timestamp.txt")
                }, enabled = state.contents.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Leaf)) {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("下载 TXT", fontSize = 14.sp)
                }
            }
        }
        item {
            Column {
                exportMessage?.let { Text(it, color = Ink) }
                state.deleteErrorMessage?.let { Text(it, color = EvaluationErrorRed) }
                checkError?.let { Text(it, color = EvaluationErrorRed) }
            }
        }
        when {
            state.isLoading -> item { LoadingContent() }
            state.errorMessage != null -> item {
                GardenPanel {
                    Text(state.errorMessage!!, color = EvaluationErrorRed)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { viewModel.load(userId) }) { Text("重新加载") }
                    }
                }
            }
            state.contents.isEmpty() -> item { EmptyContent("还没有请求过帮助", "长按学习内容可以听正确读音", "📖") }
            else -> {
                    items(state.contents, key = { it.id }) { item ->
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = WheatLight)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    GardenArt(R.drawable.garden_entry_megaphone, Modifier.size(58.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(item.highlightedTargetText(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                                        if (item.highlightedCharacterIndex() == null) Text("旧记录无法定位目标字，可保留或删除", color = EvaluationErrorRed)
                                        Text(if (item.targetType == "sentence") "请求朗读过的句子" else "请求朗读过的词", fontSize = 16.sp, color = Color(0xFF7D898C))
                                    }
                                }
                                val targetCharacter = item.requestedCharacter?.takeIf { character ->
                                    character.length == 1 && character[0].isChineseCharacter() &&
                                        item.highlightedCharacterIndex() != null
                                }
                                FlowRow(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                                    onClick = {
                                        if (checkingHelpId != null) return@Button
                                        checkError = null
                                        checkingHelpId = item.id
                                        coroutineScope.launch {
                                            readingViewModel.checkHelpedCharacter(item.id)
                                                .onSuccess { recognized ->
                                                    if (recognized) alreadyRecognizedCharacter = targetCharacter
                                                    else selectedHelp = item
                                                }
                                                .onFailure { error ->
                                                    checkError = error.message ?: "检查已认识状态失败，请重试"
                                                }
                                            checkingHelpId = null
                                        }
                                    },
                                    enabled = targetCharacter != null && checkingHelpId == null && item.id !in state.deletingContentIds
                                ) {
                                    if (checkingHelpId == item.id) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Leaf, strokeWidth = 2.dp)
                                    } else Text("加入已认识")
                                }
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                                    onClick = { viewModel.delete(userId, item.id) },
                                    enabled = item.id !in state.deletingContentIds
                                ) {
                                    if (item.id in state.deletingContentIds) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Coral, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(3.dp))
                                        Text("删除")
                                    }
                                }
                                }
                            }
                        }
                    }
            }
        }
    }
    }
    alreadyRecognizedCharacter?.let { character ->
        Dialog(onDismissRequest = { alreadyRecognizedCharacter = null }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Background)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("无需重复添加", style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Text("“$character”已在已认识的字中，无需重复添加。", color = Ink)
                    Button(
                        onClick = { alreadyRecognizedCharacter = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Leaf)
                    ) { Text("知道了") }
                }
            }
        }
    }
    selectedHelp?.let { help ->
        val character = help.requestedCharacter.orEmpty()
        GenerateLiteracyTasksDialog(
            onDismiss = { selectedHelp = null },
            onPreview = { readingViewModel.previewHelpedCharacter(help.id) },
            onSave = { _, _, tasks ->
                val savedResult = if (savedHelpId == help.id) Result.success(SavedLiteracyTasks(
                    createdCharacters = listOf(character),
                    knownCharacters = emptyList(),
                    skippedExistingCharacters = emptyList(),
                    skippedRecognizedCharacters = emptyList()
                )) else readingViewModel.saveHelpedCharacter(help.id, character, tasks)
                savedResult.onSuccess { savedHelpId = help.id }
                    .onFailure { error ->
                        if (error.message?.contains("已在已认识的字中，无需重复添加") == true) {
                            selectedHelp = null
                            alreadyRecognizedCharacter = character
                        }
                    }.mapCatching { saved ->
                    viewModel.deleteAfterRecognized(userId, help.id).getOrElse { error ->
                        throw IllegalStateException("已加入已认识，帮助记录删除失败，可重试：${error.message.orEmpty()}", error)
                    }
                    savedHelpId = null
                    onRecognized(character)
                    saved
                }
            },
            helpedCharacter = character
        )
    }
}

private fun List<HelpedContent>.toHelpedContentsTxt(): String {
    // 与列表逐项保持一致：红色高亮的字只是词、句里的定位信息，不单独导出。
    return joinToString(separator = "\n") { it.targetText }
}

/** 仅在点击“通过”前调用，确保审计记录保留的是补星之前的真实状态。 */
private fun LiteracyCard.parentPassStarSnapshot() = ParentPassStarSnapshot(
    character = character.toParentPassStarState(),
    words = terms.map(LearningContent::toParentPassStarState),
    sentences = sentences.map(LearningContent::toParentPassStarState)
)

private fun LiteracyCard.practiceTargets(): List<ReadingTarget> =
    listOf(character.target) + terms.map { it.target } + sentences.map { it.target }

private fun LearningContent.toParentPassStarState() = ParentPassStarState(
    text = text,
    earned = correctReadings,
    required = requiredReadings
)

@Composable
private fun ParentPassesScreen(
    userId: String,
    onBack: () -> Unit,
    onUndoApplied: (ParentPassRecord) -> Unit,
    viewModel: ParentPassesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val evaluationViewModel: ReadingEvaluationViewModel = hiltViewModel()
    val coroutineScope = rememberCoroutineScope()
    var recordPendingDeletion by remember { mutableStateOf<ParentPassRecord?>(null) }
    var recordPendingUndo by remember { mutableStateOf<ParentPassRecord?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    var deletingRecordId by remember { mutableStateOf<String?>(null) }
    var undoingRecordId by remember { mutableStateOf<String?>(null) }
    var isClearing by remember { mutableStateOf(false) }
    var operationErrorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId) { viewModel.load(userId) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.fillMaxHeight().widthIn(max = 760.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BackHeader("通过记录", "家长手动确认时保留的星级快照", onBack) }
        item {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("共 ${state.records.size} 条记录", color = Ink, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { showClearAllConfirmation = true },
                        enabled = state.records.isNotEmpty() && deletingRecordId == null && undoingRecordId == null && !isClearing,
                        colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text(if (isClearing) "正在清空…" else "清空全部") }
                }
                operationErrorMessage?.let { Text(it, color = EvaluationErrorRed) }
            }
        }
        when {
            state.isLoading -> item { LoadingContent() }
            state.errorMessage != null -> item {
                GardenPanel {
                    Text(state.errorMessage!!, color = EvaluationErrorRed)
                    Button(onClick = { viewModel.load(userId) }) { Text("重新加载") }
                }
            }
            state.records.isEmpty() -> item { EmptyContent("还没有家长通过记录", "通过时会保留当时的真实星数", "🌱") }
            else -> {
                items(state.records, key = { it.id }) { record ->
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = LeafLight)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GardenCharacterTile(record.character, Modifier.size(72.dp), fontSize = 46.sp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        if (record.contentSource == ReadingContentSource.RECOGNIZED.wireValue) "已认识字复习" else "待认识字学习",
                                        fontSize = 16.sp, color = Color(0xFF5B7B68)
                                    )
                                    Text(record.passedAtInChina(), fontSize = 14.sp, color = Color(0xFF5B7B68))
                                }
                                if (record.undoneAt == null) {
                                    Button(onClick = { recordPendingUndo = record },
                                        colors = ButtonDefaults.buttonColors(containerColor = Wheat, contentColor = Ink),
                                        enabled = deletingRecordId == null && undoingRecordId == null && !isClearing,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                                        Text("撤销通过", fontSize = 14.sp)
                                    }
                                }
                                IconButton(
                                    onClick = { recordPendingDeletion = record },
                                    enabled = deletingRecordId == null && undoingRecordId == null && !isClearing
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除“${record.character}”的通过记录",
                                        tint = Coral
                                    )
                                }
                            }
                            ParentPassSnapshotPanel(record.starSnapshot)
                            record.undoneAt?.let { Text("已撤销：${record.undoneAtInChina()}", fontSize = 14.sp, color = Color(0xFF5B7B68)) }
                        }
                    }
                }
            }
        }
    }
    }
    recordPendingDeletion?.let { record ->
        Dialog(onDismissRequest = { if (deletingRecordId == null && undoingRecordId == null) recordPendingDeletion = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("删除这条通过记录？", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("删除后无法恢复。", color = Color(0xFF5F6D72))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        TextButton(
                            onClick = { recordPendingDeletion = null },
                            enabled = deletingRecordId == null && undoingRecordId == null
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                deletingRecordId = record.id
                                operationErrorMessage = null
                                coroutineScope.launch {
                                    evaluationViewModel.deleteParentPass(record.id)
                                        .onSuccess { viewModel.removeRecord(record.id) }
                                        .onFailure { error ->
                                            operationErrorMessage = error.message ?: "删除通过记录失败，请稍后重试"
                                        }
                                    deletingRecordId = null
                                    recordPendingDeletion = null
                                }
                            },
                            enabled = deletingRecordId == null && undoingRecordId == null,
                            colors = ButtonDefaults.buttonColors(containerColor = Coral)
                        ) { Text("确认删除") }
                    }
                }
            }
        }
    }

    recordPendingUndo?.let { record ->
        Dialog(onDismissRequest = { if (undoingRecordId == null) recordPendingUndo = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("撤销这次通过？", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("星星会恢复到通过前的状态，并同步到同一绑定码的其它 Pad。", color = Color(0xFF5F6D72))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        TextButton(
                            onClick = { recordPendingUndo = null },
                            enabled = undoingRecordId == null
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                undoingRecordId = record.id
                                operationErrorMessage = null
                                coroutineScope.launch {
                                    evaluationViewModel.undoParentPass(record.id)
                                        .onSuccess {
                                            onUndoApplied(record)
                                            viewModel.load(userId)
                                        }
                                        .onFailure { error ->
                                            operationErrorMessage = error.message ?: "撤销通过失败，请稍后重试"
                                        }
                                    undoingRecordId = null
                                    recordPendingUndo = null
                                }
                            },
                            enabled = undoingRecordId == null,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB66B27))
                        ) { Text("确认撤销") }
                    }
                }
            }
        }
    }

    if (showClearAllConfirmation) {
        Dialog(onDismissRequest = { if (!isClearing) showClearAllConfirmation = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("清空全部通过记录？", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("将删除当前列表的全部 ${state.records.size} 条通过记录，删除后无法恢复。", color = Color(0xFF5F6D72))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        TextButton(
                            onClick = { showClearAllConfirmation = false },
                            enabled = !isClearing
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                isClearing = true
                                operationErrorMessage = null
                                coroutineScope.launch {
                                    evaluationViewModel.clearParentPassRecords()
                                        .onSuccess { viewModel.clearRecords() }
                                        .onFailure { error ->
                                            operationErrorMessage = error.message ?: "清空通过记录失败，请稍后重试"
                                        }
                                    isClearing = false
                                    showClearAllConfirmation = false
                                }
                            },
                            enabled = !isClearing,
                            colors = ButtonDefaults.buttonColors(containerColor = Coral)
                        ) { Text("确认清空") }
                    }
                }
            }
        }
    }
}

private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")
private val parentPassTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu年MM月dd日 HH:mm", Locale.CHINA)

/**
 * PostgREST 通常返回 ISO-8601 时间，但部分响应会使用空格分隔日期和时间。
 * 两种格式都按服务端时间的实际偏移换算为北京时间，避免把原始 UTC 字符串直接展示给家长。
 */
private fun ParentPassRecord.passedAtInChina(): String = passedAt.toInstantOrNull()
    ?.atZone(chinaZone)
    ?.format(parentPassTimeFormatter)
    ?: passedAt

private fun ParentPassRecord.undoneAtInChina(): String = undoneAt?.toInstantOrNull()
    ?.atZone(chinaZone)
    ?.format(parentPassTimeFormatter)
    ?: undoneAt.orEmpty()

/** 审计快照带有文本和固定顺序，可在当前 Pad 立即恢复本地当天进度。 */
private fun ParentPassRecord.snapshotProgressEntries(): List<Pair<ReadingTarget, Int>> = buildList {
    fun addEntry(type: String, order: Int, state: ParentPassStarState) {
        val target = ReadingTarget(
            literacyCharacterId = literacyCharacterId,
            targetType = type,
            displayText = state.text,
            itemOrder = order,
            sentenceText = state.text.takeIf { type == "sentence" },
            wordText = state.text.takeIf { type == "word" },
            contentSource = if (contentSource == ReadingContentSource.RECOGNIZED.wireValue) {
                ReadingContentSource.RECOGNIZED
            } else ReadingContentSource.TASK,
            characterRequiredReadings = state.required.takeIf { type == "character" }
        )
        add(target to state.earned)
    }
    addEntry("character", 0, starSnapshot.character)
    starSnapshot.words.forEachIndexed { index, state -> addEntry("word", index, state) }
    starSnapshot.sentences.forEachIndexed { index, state -> addEntry("sentence", index, state) }
}

private fun String.toInstantOrNull(): Instant? {
    val normalized = trim().replace(' ', 'T')
    return runCatching { Instant.parse(normalized) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(normalized).toInstant() }.getOrNull()
}

private fun HelpedContent.highlightedCharacterIndex(): Int? {
    val character = requestedCharacter?.takeIf { it.length == 1 && it[0].isChineseCharacter() } ?: return null
    characterIndex?.takeIf { targetText.getOrNull(it)?.toString() == character }?.let { return it }
    // 旧版词组求助保存的是整组索引；只在单次出现时才能安全定位到当前词。
    return targetText.indexOf(character).takeIf { it >= 0 && targetText.lastIndexOf(character) == it }
}

private fun HelpedContent.highlightedTargetText() = buildAnnotatedString {
    append(targetText)
    val highlightStart = highlightedCharacterIndex() ?: return@buildAnnotatedString
    addStyle(
        style = SpanStyle(color = EvaluationErrorRed, fontWeight = FontWeight.ExtraBold),
        start = highlightStart,
        end = highlightStart + 1
    )
}

private data class GardenMenu(
    val title: String, val subtitle: String, val art: Int, val color: Color,
    val topColor: Color, val bottomColor: Color, val borderColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun ProfileDecoratedCard(
    modifier: Modifier, topColor: Color, bottomColor: Color, borderColor: Color,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.shadow(4.dp, shape).clip(shape)
        .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
        .border(3.dp, Color.White, shape)) {
        Box(Modifier.matchParentSize().padding(4.dp)
            .border(3.dp, borderColor, RoundedCornerShape(14.dp)))
        content()
    }
}

@Composable
private fun ProfileMenuCard(menu: GardenMenu, modifier: Modifier) {
    ProfileDecoratedCard(modifier.heightIn(min = 84.dp).clickable(onClick = menu.onClick),
        menu.topColor, menu.bottomColor, menu.borderColor) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GardenArt(menu.art, Modifier.width(82.dp).height(66.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(menu.title, color = menu.color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(menu.subtitle, color = Color(0xFF687D9F), fontSize = 14.sp)
            }
            Box(Modifier.size(34.dp).background(menu.borderColor, CircleShape),
                contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    drawLine(menu.color, Offset(size.width * .40f, size.height * .28f),
                        Offset(size.width * .64f, size.height * .50f), stroke, cap = StrokeCap.Round)
                    drawLine(menu.color, Offset(size.width * .64f, size.height * .50f),
                        Offset(size.width * .40f, size.height * .72f), stroke, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun KnownScreen(
    userId: String,
    onBack: () -> Unit,
    onArchive: suspend (RecognizedCharacter) -> Result<Unit>,
    onArchived: (RecognizedCharacter) -> Unit,
    onNotice: (String) -> Unit,
    viewModel: RecognizedCharactersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var archivingCharacterIds by remember { mutableStateOf(emptySet<String>()) }
    var archiveConfirmationCharacter by remember { mutableStateOf<RecognizedCharacter?>(null) }
    LaunchedEffect(userId) { viewModel.load(userId) }
    LaunchedEffect(state.topNotice) {
        state.topNotice?.let {
            onNotice(it)
            viewModel.consumeTopNotice()
        }
    }

    if (state.isLoadingPhonetics) {
        Dialog(onDismissRequest = viewModel::dismissPhonetics) {
            GardenPanel(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Sky, strokeWidth = 2.dp)
                    Text("正在加载发音标注…", color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    state.phoneticDetail?.let { detail ->
        // 与“待认识的字”页使用同一个弹层：正文只读，数字拼音可逐字修正。
        PendingPhoneticDetailDialog(
            detail = detail,
            savingAssetId = state.savingAssetId,
            errorMessage = state.phoneticErrorMessage,
            onDismiss = viewModel::dismissPhonetics,
            onSave = viewModel::savePhoneticAsset
        )
    }
    state.phoneticErrorMessage?.takeIf { state.phoneticDetail == null && !state.isLoadingPhonetics }?.let { message ->
        Dialog(onDismissRequest = viewModel::dismissPhonetics) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("无法加载发音标注", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text(message, color = EvaluationErrorRed)
                    Button(onClick = viewModel::dismissPhonetics, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
                }
            }
        }
    }
    archiveConfirmationCharacter?.let { character ->
        Dialog(onDismissRequest = { archiveConfirmationCharacter = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("确认存入字库？", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "“${character.character}”存库后会从已认识的字列表移除，关联的学习内容和教学音频也会清理。",
                        color = Color(0xFF5F6D72)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { archiveConfirmationCharacter = null }) { Text("取消") }
                        Button(
                            onClick = {
                                if (character.id.isBlank() || character.id in archivingCharacterIds) {
                                    archiveConfirmationCharacter = null
                                    return@Button
                                }
                                archiveConfirmationCharacter = null
                                archivingCharacterIds = archivingCharacterIds + character.id
                                coroutineScope.launch {
                                    onArchive(character)
                                        .onSuccess {
                                            viewModel.removeCharacter(character.id)
                                            onArchived(character)
                                        }
                                        .onFailure { error ->
                                            onNotice(error.message ?: "存入字库失败，请稍后重试")
                                        }
                                    archivingCharacterIds = archivingCharacterIds - character.id
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Leaf)
                        ) { Text("确认存库") }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val contentWidth = maxWidth.coerceAtMost(790.dp)
    val columns = if (contentWidth / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 720.dp) 3
        else if (contentWidth >= 470.dp) 2 else 1
    LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.width(contentWidth).fillMaxHeight(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            BackHeader("已认识的字", "置顶后，从今天起重新学习3天", onBack, count = state.characters.size)
        }
        if (state.isLoading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingContent() }
        else if (state.characters.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyContent("还没有已认识的字", state.errorMessage ?: "完成一个认字任务后，它会出现在这里", "🌱")
            if (state.errorMessage != null) Button(onClick = { viewModel.load(userId) }) { Text("重新加载") }
        }
        else items(state.characters, key = { it.id.ifBlank { it.character } }) { character ->
            RecognizedCharacterCard(character,
                isArchiving = character.id in archivingCharacterIds,
                isTopping = state.toppingCharacterId == character.id,
                onDetails = { viewModel.showPhonetics(character) },
                onTop = { viewModel.topCharacter(character) },
                onArchive = {
                    if (character.id.isNotBlank() && character.id !in archivingCharacterIds) archiveConfirmationCharacter = character
                })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("每个汉字，都是一份成长", color = Ink, modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center)
        }
    }
    }
    if (state.isLoading) RequestLoadingOverlay()
}

@Composable
private fun BackHeader(title: String, subtitle: String, onBack: () -> Unit,
    count: Int? = null, action: @Composable () -> Unit = {}) {
    GardenPageHeader(title, subtitle, onBack,
        mascot = when (title) {
            "已认识的字" -> R.drawable.garden_mascot_red_book
            "字库", "帮助过的内容" -> R.drawable.garden_mascot_blue_book
            else -> R.drawable.garden_mascot_welcome
        }, count = count, compactActionRightAligned = title == "帮助过的内容", action = action)
}

@Composable
private fun RecognizedCharacterCard(
    character: RecognizedCharacter,
    isArchiving: Boolean,
    isTopping: Boolean,
    onDetails: () -> Unit,
    onTop: () -> Unit,
    onArchive: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = LeafLight,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White), shadowElevation = 3.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GardenCharacterTile(character.character, Modifier.size(64.dp), fontSize = 42.sp)
                Text("收录于\n${character.recognizedAt?.take(10) ?: "未知日期"}", color = Color(0xFF687D9F), fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onDetails, enabled = !isArchiving && !isTopping, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Sky), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("查看", fontSize = 14.sp) }
                Button(onClick = onTop, enabled = !isArchiving && !isTopping, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Wheat, contentColor = Ink), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    if (isTopping) CircularProgressIndicator(Modifier.size(18.dp), color = Ink) else Text("置顶", fontSize = 14.sp)
                }
                Button(onClick = onArchive, enabled = !isArchiving && !isTopping, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Leaf), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    if (isArchiving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White) else Text("存库", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailCard(card: LiteracyCard) {
    Column(Modifier.border(1.dp, Line, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(LeafLight.copy(alpha = .25f)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row { Text("字", color = Leaf, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(card.word, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 31.sp, color = Ink, fontWeight = FontWeight.ExtraBold) }
        HorizontalDivider(color = Line); Text("词  ${card.terms.joinToString(" · ") { it.text }}", fontSize = 12.sp, color = Ink); HorizontalDivider(color = Line); Text("句  ${card.sentences.joinToString(" · ") { it.text }}", fontSize = 12.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PendingCharactersScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: PendingCharactersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) { viewModel.load(userId) }

    if (state.isLoadingPhonetics) {
        Dialog(onDismissRequest = viewModel::dismissPhonetics) {
            GardenPanel(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Sky, strokeWidth = 2.dp)
                    Text("正在加载发音标注…", color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    state.phoneticDetail?.let { detail ->
        PendingPhoneticDetailDialog(
            detail = detail,
            savingAssetId = state.savingAssetId,
            errorMessage = state.phoneticErrorMessage,
            onDismiss = viewModel::dismissPhonetics,
            onSave = viewModel::savePhoneticAsset
        )
    }
    state.phoneticErrorMessage?.takeIf { state.phoneticDetail == null && !state.isLoadingPhonetics }?.let { message ->
        Dialog(onDismissRequest = viewModel::dismissPhonetics) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("无法加载发音标注", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text(message, color = EvaluationErrorRed)
                    Button(onClick = viewModel::dismissPhonetics, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val contentWidth = maxWidth.coerceAtMost(656.dp)
        val fontScale = LocalDensity.current.fontScale
        val sidePadding = if (contentWidth >= 600.dp && fontScale <= 1.1f) 50.dp else 20.dp
        val columns = if (sidePadding == 50.dp) 6 else gardenGridColumns((contentWidth - sidePadding * 2).value, fontScale)
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.width(contentWidth).fillMaxHeight(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = sidePadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BackHeader("待认识的字", "这些汉字正等着和你见面", onBack, count = state.characters.size)
            }
            when {
                state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingContent() }
                state.errorMessage != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    GardenPanel {
                        EmptyContent("暂时无法加载", state.errorMessage.orEmpty(), "📖")
                        Button(onClick = { viewModel.load(userId) }) { Text("重新加载") }
                    }
                }
                state.characters.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyContent("暂时没有待认识的字", "等待家长添加新的识字任务。", "📖")
                }
                else -> {
                    items(state.characters, key = { it.id }) { character ->
                        GardenCharacterTile(character.character, Modifier.fillMaxWidth().aspectRatio(1.04f),
                            onClick = { viewModel.showPhonetics(character) }, fontSize = 48.sp)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GardenMetricStrip("已显示全部待认识的字", LeafLight)
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingPhoneticDetailDialog(
    detail: PendingPhoneticDetail,
    savingAssetId: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (PhoneticAsset, List<String?>) -> Unit
) {
    GardenDialogFrame("${detail.character.character} 的发音标注", "词句内容只读；可逐字修正数字拼音", onDismiss, tint = LeafLight) {
        item { errorMessage?.let { Text(it, color = EvaluationErrorRed) } }
        item {
            if (detail.assets.isEmpty()) EmptyContent("暂时没有发音标注", "词句保存后会自动准备，请稍后再试。", "🔤")
            else BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = gardenPhoneticColumns(maxWidth.value, LocalDensity.current.fontScale)
                val words = detail.assets.filter { it.itemType == "word" }
                val sentences = detail.assets.filter { it.itemType != "word" }
                val section: @Composable (String, List<PhoneticAsset>, Color, Modifier) -> Unit = { title, assets, tint, modifier ->
                    if (assets.isNotEmpty()) GardenPanel(modifier, tint) {
                        Text(title, color = Ink, style = MaterialTheme.typography.titleLarge)
                        assets.forEach { asset ->
                            androidx.compose.runtime.key(asset.id) {
                                PendingPhoneticAssetCard(asset, isSaving = savingAssetId == asset.id, onSave = { onSave(asset, it) })
                            }
                        }
                    }
                }
                if (columns == 2) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    section("词语", words, WheatLight, Modifier.weight(1f))
                    section("句子", sentences, SkyLight, Modifier.weight(1f))
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    section("词语", words, WheatLight, Modifier.fillMaxWidth())
                    section("句子", sentences, SkyLight, Modifier.fillMaxWidth())
                }
            }
        }
        item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Leaf)) { Text("关闭") } }
    }
}

@Composable
private fun PendingPhoneticAssetCard(
    asset: PhoneticAsset,
    isSaving: Boolean,
    onSave: (List<String?>) -> Unit
) {
    val characters = remember(asset.text) { asset.text.filter { it in '\u4E00'..'\u9FFF' }.map(Char::toString) }
    val initialTokens = remember(asset.id, asset.tokens, characters) {
        if (asset.tokens.size == characters.size) asset.tokens else List(characters.size) { "" }
    }
    val drafts = remember(asset.id, initialTokens) { mutableStateListOf<String?>().also { it.addAll(initialTokens) } }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFFEF9),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (asset.itemType == "word") "词语" else "句子", color = Leaf, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                Text(asset.text, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    when (asset.status) {
                        "ready" -> "已就绪"
                        "failed" -> "生成失败"
                        else -> "正在准备"
                    },
                    color = if (asset.status == "failed") EvaluationErrorRed else Color(0xFF748184),
                    fontSize = 16.sp
                )
            }
            asset.lastError?.let { Text("自动生成：$it", color = EvaluationErrorRed, fontSize = 16.sp) }
            if (characters.isEmpty()) {
                Text("此内容没有可编辑的汉字", color = EvaluationErrorRed, fontSize = 16.sp)
            } else {
                characters.forEachIndexed { index, character ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = WheatLight, modifier = Modifier.size(56.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))) {
                            Box(contentAlignment = Alignment.Center) { Text(character, color = Ink, fontSize = 32.sp) }
                        }
                        if (drafts[index] == null) {
                            Text("腾讯不支持指定轻声", color = Color(0xFF748184), fontSize = 16.sp, modifier = Modifier.weight(1f))
                        } else {
                            OutlinedTextField(
                                value = drafts[index].orEmpty(),
                                onValueChange = { drafts[index] = it.lowercase(Locale.ROOT).trim() },
                                enabled = !isSaving,
                                singleLine = true,
                                label = { Text("数字拼音") },
                                placeholder = { Text("例如 zhang3") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Button(
                    onClick = { onSave(drafts.toList()) },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Sky),
                    shape = CircleShape
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("保存发音标注")
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    var exportStart by remember { mutableStateOf("") }
    var exportEnd by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()
    val exportStartNumber = exportStart.toIntOrNull()
    val exportEndNumber = exportEnd.toIntOrNull()
    val isExportStartInvalid = exportStart.isNotBlank() && (exportStartNumber == null || exportStartNumber < 1)
    val isExportEndInvalid = exportEnd.isNotBlank() && (
        exportEndNumber == null ||
            exportEndNumber < 1 ||
            (exportStartNumber != null && exportStartNumber >= 1 && exportEndNumber <= exportStartNumber)
        )
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = state.pendingExportContent
        viewModel.consumePendingExport()
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val errorMessage = runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri))
                        .bufferedWriter(Charsets.UTF_8)
                        .use { it.write(content) }
                }
            }.exceptionOrNull()?.message
            viewModel.reportExportResult(errorMessage?.let { "保存导出文件失败：$it" })
        }
    }
    LaunchedEffect(userId) { viewModel.load(userId) }
    LaunchedEffect(state.pendingExportContent) {
        if (state.pendingExportContent != null) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            createDocumentLauncher.launch("字库_$timestamp.txt")
        }
    }
    val visible = state.characters.filter { it.character.contains(query.trim()) }
    if (state.isRefreshing) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Background)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = Sky)
                    Text("正在更新字库…", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("正在查询全部汉字并保存到本地", color = Color(0xFF849094), fontSize = 13.sp)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val contentWidth = maxWidth.coerceAtMost(632.dp)
        val columns = gardenGridColumns((contentWidth - 40.dp).value, LocalDensity.current.fontScale)
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.width(contentWidth).fillMaxHeight(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {

        BackHeader("字库", "把认识的汉字收进我的小书架", onBack)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
        GardenPanel {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = WheatLight) { Text("本地字库  ${state.characters.size} 个字", color = Ink, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) }
            Button(
                onClick = { viewModel.refresh(userId) },
                enabled = !state.isLoading && !state.isRefreshing,
                colors = ButtonDefaults.buttonColors(containerColor = Sky),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.AutoStories, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("更新字库")
            }
            Button(
                onClick = { viewModel.prepareExport(exportStart, exportEnd) },
                enabled = !state.isLoading && !state.isRefreshing && !state.isPreparingExport && state.pendingExportContent == null,
                colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("导出字库")
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = exportStart,
                onValueChange = { value -> if (value.all(Char::isDigit)) exportStart = value },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("开始序号") },
                placeholder = { Text("例如 1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isExportStartInvalid,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Leaf,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            Text("至", color = Color(0xFF718084), fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = exportEnd,
                onValueChange = { value -> if (value.all(Char::isDigit)) exportEnd = value },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("结束序号") },
                placeholder = { Text("例如 100") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isExportEndInvalid,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Leaf,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }
        Text(
            "输入从 1 开始的整数序号；结束序号必须大于开始序号。",
            color = Color(0xFF879296),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
        state.refreshMessage?.let { message ->
            Text(text = message, color = Coral, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        state.exportMessage?.let { message ->
            Text(
                text = message,
                color = if (message == "字库已导出到本地") Leaf else Coral,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索汉字") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = Sky) }, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sky, unfocusedBorderColor = Line, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
        Spacer(Modifier.height(4.dp))
        }
        }
        when {
            state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingContent() }
            state.characters.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                if (state.hasLocalData) EmptyContent("字库还没有汉字", "点击更新字库后会保存最新数据。", "📚")
                else EmptyContent("本地暂无字库数据", "请点击“更新字库”下载并保存汉字。", "📚")
            }
            visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyContent("没有找到这个字", "换一个汉字试试看吧", "🔎")
            }
            else -> {
                items(visible, key = { it.character }) { character ->
                    GardenCharacterTile(character.character, Modifier.fillMaxWidth().aspectRatio(1.04f), fontSize = 48.sp)
                }
                if (query.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                    GardenMetricStrip("已显示本地保存的全部字库", LeafLight)
                }
            }
        }
        }
    }
}

@Composable
private fun SuccessNotice(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(26.dp), contentAlignment = Alignment.TopCenter) {
        Surface(shape = RoundedCornerShape(18.dp), color = Leaf, shadowElevation = 8.dp, modifier = Modifier.clickable(onClick = onDismiss)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(message, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ParentPassSnapshotPanel(snapshot: ParentPassStarSnapshot) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("通过前", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        val entries = listOf("字" to snapshot.character) + snapshot.words.map { "词" to it } + snapshot.sentences.map { "句" to it }
        entries.forEach { (label, entry) ->
            Text("$label·${entry.text} ${entry.earned}/${entry.required} " +
                buildString { repeat(entry.required) { append(if (it < entry.earned) "★" else "☆") } },
                color = Color(0xFF996000), fontSize = 13.sp)
        }
    }
}
