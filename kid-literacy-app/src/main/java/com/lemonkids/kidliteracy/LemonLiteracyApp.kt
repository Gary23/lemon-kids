@file:OptIn(ExperimentalMaterial3Api::class)

package com.lemonkids.kidliteracy

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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import com.lemonkids.kidliteracy.feature.recognized.RecognizedCharactersViewModel
import com.lemonkids.kidliteracy.feature.help.HelpedContent
import com.lemonkids.kidliteracy.feature.help.HelpedCharactersViewModel
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
import com.lemonkids.kidliteracy.feature.reading.requiredCorrectReadings
import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.model.RecognizedCharacter
import com.lemonkids.shared.ui.auth.AuthViewModel
import com.lemonkids.shared.ui.auth.BindingCodeScreen
import dagger.hilt.android.HiltAndroidApp
import java.text.SimpleDateFormat
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

private enum class Page { HOME, PROFILE, KNOWN, PENDING, LIBRARY, HELPED }

/** ISO 216 A4 纸张的宽高比：1 : √2。 */
private const val A4_PAPER_WIDTH_TO_HEIGHT = 0.70710677f

private data class Lesson(
    val title: String,
    val date: String,
    val progress: String,
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
                onRestoreWithBinding = authViewModel::restoreLiteracyBindingFromDialog
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
            onRestoreWithBinding = authViewModel::restoreLiteracyBindingFromDialog
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
                modifier = Modifier.padding(24.dp),
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
        if (target.contentSource == ReadingContentSource.TASK) {
            homeState.groups.flatMap { it.learningCharacters }
                .firstOrNull { it.id == target.literacyCharacterId }
                ?.let(::completeCharacterIfReady)
        }
        return updatedCorrectReadings
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
        }
    }

    val showNav = page == Page.HOME || page == Page.PROFILE
    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showNav) LiteracyBottomNav(page) { page = it }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                Page.HOME -> HomeScreen(
                    childName = childName,
                    groups = homeState.groups,
                    isLoading = homeState.isLoading,
                    practiceProgress = practiceProgress,
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
                    userId = userId,
                    onKnown = { page = Page.KNOWN },
                    onPending = { page = Page.PENDING },
                    onLibrary = { page = Page.LIBRARY },
                    onHelped = { page = Page.HELPED },
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
                Page.HELPED -> HelpedCharactersScreen(userId = userId, onBack = { page = Page.PROFILE })
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
                onCorrectReadings = ::recordCorrectReadings
            )
        }
    }
    if (showGenerateLiteracyTasksDialog) {
        GenerateLiteracyTasksDialog(
            onDismiss = { showGenerateLiteracyTasksDialog = false },
            onPreview = { characters ->
                readingEvaluationViewModel.previewLiteracyTasks(characters)
            },
            onSave = { characters, tasks ->
                val result = readingEvaluationViewModel.saveLiteracyTasks(characters, tasks)
                if (result.isSuccess) {
                    val generated = result.getOrThrow()
                    homeViewModel.load(userId)
                    val created = generated.createdCharacters.joinToString("、")
                    val knownCharacters = generated.knownCharacters.joinToString("、")
                    val skippedExisting = generated.skippedExistingCharacters.joinToString("、")
                    notice = buildString {
                        if (created.isNotBlank()) append("已添加待认识字：$created")
                        if (knownCharacters.isNotBlank()) {
                            if (isNotEmpty()) append("；")
                            append("输入中已在字库：$knownCharacters")
                        }
                        if (skippedExisting.isNotBlank()) {
                            if (isNotEmpty()) append("；")
                            append("已有待认识任务：$skippedExisting")
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
    val tabs = listOf(
        Triple(Page.HOME, "首页", Icons.Filled.Home),
        Triple(Page.PROFILE, "我的", Icons.Filled.Person)
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).shadow(10.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp), color = Color.White
    ) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
            tabs.forEach { (page, title, icon) ->
                val selected = current == page
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(page) },
                    icon = { Icon(icon, title, Modifier.size(25.dp)) },
                    label = { Text(title, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Sky, selectedTextColor = Sky, indicatorColor = SkyLight,
                        unselectedIconColor = Color(0xFF99A1A5), unselectedTextColor = Color(0xFF99A1A5)
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    childName: String,
    groups: List<LiteracyCharacterGroup>,
    isLoading: Boolean,
    practiceProgress: Map<String, Int>,
    onCharacter: (LiteracyCharacterGroup, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("柠檬认字", style = MaterialTheme.typography.headlineLarge, color = Ink)
                        Text("$childName，和小麦一起认识今天的新朋友", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7C898D))
                    }
                    Surface(shape = CircleShape, color = WheatLight, modifier = Modifier.size(58.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("🌾", fontSize = 30.sp) }
                    }
                }
            }
            item {
                Text("识字任务", style = MaterialTheme.typography.titleLarge, color = Ink)
            }
            if (groups.isEmpty()) {
                item { LiteracyEmptyState() }
            } else {
                items(groups, key = { "${it.type}-${it.groupNumber}" }) { group ->
                    LessonCard(
                        lesson = group.toLesson(practiceProgress),
                        onCharacterClick = { index -> onCharacter(group, index) }
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 28.dp)
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

        if (isLoading) RequestLoadingOverlay()
    }
}

@Composable
private fun RequestLoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Background.copy(alpha = .94f)),
        contentAlignment = Alignment.Center
    ) {
        LoadingContent()
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
    title = "${if (isKnown) "已认识的字" else "待认识的字"} · 第${groupNumber}组",
    date = "",
    progress = "",
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
private fun LessonCard(lesson: Lesson, onCharacterClick: (Int) -> Unit) {
    val background = if (lesson.known) LeafLight else CoralLight
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Box {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                }
                // 字格永远按一行 8 个计算。任务不足 8 个时保留右侧空白，避免少量字被拉宽。
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val characterCellSize = (maxWidth - 8.dp * 7) / 8
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lesson.characters.forEachIndexed { index, char ->
                            val isCompleted = lesson.characterIds.getOrNull(index) in lesson.completedCharacterIds
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCompleted) LeafLight else Color.White.copy(alpha = .74f),
                                border = if (isCompleted) {
                                    androidx.compose.foundation.BorderStroke(1.dp, Leaf.copy(alpha = .65f))
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .size(characterCellSize)
                                    .clickable { onCharacterClick(index) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        char,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isCompleted) Leaf else Ink
                                    )
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
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(28.dp)) {
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
                IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White)) { Icon(Icons.Filled.ArrowBack, "返回", tint = Ink) }
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
    onCorrectReadings: (ReadingTarget, Int) -> Int
) {
    val context = LocalContext.current
    var activeTarget by remember(card.literacyCharacterId) { mutableStateOf<ReadingTarget?>(null) }
    var activeRecordingState by remember(card.literacyCharacterId) { mutableStateOf<RecordingState?>(null) }
    var pendingTarget by remember(card.literacyCharacterId) { mutableStateOf<ReadingTarget?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activeTarget = if (granted) pendingTarget else null
        activeRecordingState = if (granted && pendingTarget != null) RecordingState.PREPARING else null
        pendingTarget = null
    }
    fun isActivePracticeLocked(): Boolean = activeTarget != null &&
        activeRecordingState != RecordingState.FINISHED && activeRecordingState != RecordingState.ERROR

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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(.9f).fillMaxHeight(.9f),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${card.word}的朗读练习", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text("长按汉字可听正确读音", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7C898D))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭学习弹层", tint = Color(0xFF748185))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Line)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { StudySectionHeader("字", "先认识这个字") }
                    item(key = card.character.target.practiceProgressKey()) {
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
                    item { StudySectionHeader("词", "一词一行，逐个练习") }
                    items(card.terms, key = { it.target.practiceProgressKey() }) { content ->
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
                    if (card.sentences.isNotEmpty()) {
                        item { StudySectionHeader("句", "读一读完整的句子") }
                        items(card.sentences, key = { it.target.practiceProgressKey() }) { content ->
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
        Surface(shape = RoundedCornerShape(9.dp), color = WheatLight) {
            Text(title, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Ink, fontWeight = FontWeight.ExtraBold)
        }
        Text(subtitle, color = Color(0xFF7C898D), fontSize = 13.sp)
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
    val background = when {
        content.learned -> LeafLight
        else -> SkyLight
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .62f))
    ) {
        // 未开始时也保留与朗读中完全一致的提示栏位置。这样点“开始”后，
        // 卡片高度和后续词句的位置都不会因为出现状态提示而跳动。
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 右侧操作区固定宽度，字、词、句的内容区便统一使用句子的最大可用宽度。
                StudyContentPanel(
                    content = content,
                    accent = accent,
                    background = background,
                    contentPressed = isContentPressed,
                    pointReadCharacterIndex = pointReadCharacterIndex,
                    modifier = Modifier.weight(1f),
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
                // 即使满星后隐藏动画与按钮，也必须保留完整的右侧操作区，不能让文字区扩张。
                Row(
                    modifier = Modifier.width(172.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PracticeRecordingDots(active = false, color = accent, visible = !content.learned)
                    Button(
                        onClick = onStart,
                        enabled = !content.learned && !otherRecordingActive,
                        modifier = Modifier.width(82.dp).graphicsLayer { alpha = if (content.learned) 0f else 1f },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 9.dp)
                    ) {
                        Text("开始", maxLines = 1)
                    }
                }
            }
            PracticeMessageSlot()
        }
    }
}

@Composable
private fun StudyContentPanel(
    content: LearningContent,
    accent: Color,
    background: Color,
    contentPressed: Boolean,
    pointReadCharacterIndex: Int?,
    modifier: Modifier = Modifier,
    onPressChanged: (Boolean) -> Unit,
    onLongPress: (Char, Int) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = when {
            contentPressed -> Sky.copy(alpha = .25f)
            content.learned -> background.copy(alpha = .65f)
            else -> Color.White
        },
        border = androidx.compose.foundation.BorderStroke(
            if (contentPressed) 2.dp else 1.dp,
            if (contentPressed) Sky else accent.copy(alpha = .55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 7.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudyStarRail(content.correctReadings, content.requiredReadings)
            // 与朗读中的文字使用同一套排版和内边距，避免切换状态时内容框高度变化；
            // 未开始的行同样保留长按点读和按压反馈，不能因为 UI 重构而丢失原交互。
            Box(Modifier.weight(1f)) {
                DialogReadingContent(
                    target = content.target,
                    enabled = true,
                    pointReadCharacterIndex = pointReadCharacterIndex,
                    onPressChanged = onPressChanged,
                    onLongPress = onLongPress
                )
            }
        }
    }
}

/** 行内朗读提示始终占位，状态切换只更新文案，绝不改变练习卡高度。 */
@Composable
private fun PracticeMessageSlot(message: String? = null, isError: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth().height(18.dp).padding(start = 28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        message?.let {
            Text(
                it,
                color = if (isError) EvaluationErrorRed else Color(0xFF718084),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudyStarRail(correctReadings: Int, requiredReadings: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-2).dp)) {
        repeat(requiredReadings) { index ->
            Text(if (index < correctReadings) "★" else "☆", color = Wheat, fontSize = 17.sp, lineHeight = 17.sp)
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
                        message = if (correctReadingsThisAttempt > 0) {
                            if (displayedCorrectReadingCount >= target.requiredCorrectReadings()) {
                                "太棒啦，这一项读完了！"
                            } else if (target.targetType == "character") {
                                "读对啦！"
                            } else {
                                "读对啦！再读 ${target.requiredCorrectReadings() - displayedCorrectReadingCount} 次就完成。"
                            }
                        } else {
                            "这次还没有点亮星星，看看红色的字，再读一次吧。"
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
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = ::close, enabled = !isWaitingForResult, colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF748185))) { Icon(Icons.Filled.Close, if (isWaitingForResult) "正在生成结果，请稍候" else "关闭") }
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
        state == RecordingState.RECORDING || state == RecordingState.EVALUATING -> Coral
        wrongCharacterIndexes.isNotEmpty() -> Coral
        else -> Sky
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(2.dp, panelAccent.copy(alpha = .72f))
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        contentPressed -> Sky.copy(alpha = .25f)
                        completed -> LeafLight.copy(alpha = .62f)
                        else -> Color.White
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        if (contentPressed) 2.dp else 1.dp,
                        if (contentPressed) Sky else panelAccent.copy(alpha = .55f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(start = 7.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudyStarRail(correctReadingCount, requiredReadingCount)
                        Box(Modifier.weight(1f)) {
                            DialogReadingContent(
                                target = target,
                                enabled = true,
                                wrongCharacterIndexes = wrongCharacterIndexes,
                                pointReadCharacterIndex = pointReadCharacterIndex,
                                onPressChanged = onPressChanged,
                                onLongPress = onLongPress
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.width(172.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PracticeRecordingDots(
                        active = state == RecordingState.RECORDING || state == RecordingState.EVALUATING,
                        color = accent,
                        // 最后一颗星点亮的同一帧即隐藏，位置仍由固定宽度的占位区域保持。
                        visible = !completed
                    )
                    Button(
                        onClick = onAction,
                        enabled = !completed && actionEnabled,
                        modifier = Modifier.width(82.dp).graphicsLayer { alpha = if (completed) 0f else 1f },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 9.dp)
                    ) {
                        Text(actionLabel, maxLines = 1)
                    }
                }
            }
            PracticeMessageSlot(
                message = message,
                isError = state == RecordingState.ERROR
            )
        }
    }
}

@Composable
private fun PracticeRecordingDots(active: Boolean, color: Color, visible: Boolean = true) {
    if (active) {
        Box(Modifier.width(80.dp).height(18.dp).graphicsLayer { alpha = if (visible) 1f else 0f }) {
            RecordingAnimation(color)
        }
    } else {
        Row(
            modifier = Modifier.width(80.dp).height(18.dp).graphicsLayer { alpha = if (visible) 1f else 0f },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) {
                Surface(shape = CircleShape, color = color.copy(alpha = .32f), modifier = Modifier.size(12.dp)) {}
            }
        }
    }
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
private fun RecordingAnimation(color: Color) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "recording")
    // 圆点始终占用相同空间，只在自己的图层内缩放，避免把下方按钮来回顶动。
    Row(
        modifier = Modifier.width(80.dp).height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                modifier = Modifier.size(12.dp).graphicsLayer {
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
private fun DialogCharacterText(
    target: String,
    targetType: String? = null,
    enabled: Boolean = true,
    wrongCharacterIndexes: Set<Int> = emptySet(),
    pointReadCharacterIndexes: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
    onPressChanged: (Boolean) -> Unit = {},
    onLongPress: (Char, Int) -> Unit
) {
    val isSentence = targetType == "sentence"
    val text = buildAnnotatedString {
        target.forEachIndexed { index, character ->
            val color = if (character.isChineseCharacter() && index in wrongCharacterIndexes) EvaluationErrorRed else Ink
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
        modifier = modifier.fillMaxWidth().padding(18.dp),
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
                fontSize = when (targetType) {
                    "word" -> 40.sp
                    "sentence" -> 34.sp
                    else -> if (target.count { it.isChineseCharacter() } == 1) 58.sp else 34.sp
                },
                letterSpacing = when (targetType) {
                    "word", "sentence" -> 1.5.sp
                    else -> 0.sp
                },
                fontWeight = FontWeight.ExtraBold
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
    childName: String,
    avatarUrl: String?,
    userId: String,
    onKnown: () -> Unit,
    onPending: () -> Unit,
    onLibrary: () -> Unit,
    onHelped: () -> Unit,
    onGenerateLiteracyTasks: () -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val libraryState by libraryViewModel.uiState.collectAsState()

    LaunchedEffect(userId) { libraryViewModel.load(userId) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Text("我的小麦仓", style = MaterialTheme.typography.headlineLarge, color = Ink) }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = SkyLight)) {
                Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(76.dp), border = androidx.compose.foundation.BorderStroke(3.dp, Wheat)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (avatarUrl.isNullOrBlank()) {
                                Text("🧒", fontSize = 42.sp)
                            } else {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "$childName 的头像",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(childName, style = MaterialTheme.typography.titleLarge, color = Ink); Text("今天也在认真认识汉字呀！", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF618091)) }
                }
            }
        }
        if (libraryState.hasLocalData) {
            item {
                MetricCard(
                    libraryState.characters.size.toString(),
                    "累计认识汉字",
                    "🌾",
                    LeafLight,
                    Modifier.fillMaxWidth()
                )
            }
        }
        item { Text("我的学习", style = MaterialTheme.typography.titleLarge, color = Ink) }
        item { ProfileMenuCard("已认识的字", "看看我已经掌握了哪些字", Icons.Filled.CheckCircle, Leaf, LeafLight, onKnown) }
        item { ProfileMenuCard("查看待认识的字", "看看接下来要学习哪些汉字", Icons.Filled.AutoStories, Wheat, WheatLight, onPending) }
        item { ProfileMenuCard("字库", "按拼音收集我的汉字", Icons.Filled.LibraryBooks, Sky, SkyLight, onLibrary) }
        item { ProfileMenuCard("帮助过的内容", "看看我请求朗读过的词和句", Icons.Filled.HelpOutline, Coral, CoralLight, onHelped) }
        item { ProfileMenuCard("智能添加识字", "输入汉字，自动生成字库范围内的词和句", Icons.Filled.AddCircle, Leaf, LeafLight, onGenerateLiteracyTasks) }
    }
}

@Composable
private fun GenerateLiteracyTasksDialog(
    onDismiss: () -> Unit,
    onPreview: suspend (String) -> Result<LiteracyTasksPreview>,
    onSave: suspend (String, List<GeneratedLiteracyTask>) -> Result<SavedLiteracyTasks>
) {
    var characters by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<LiteracyTasksPreview?>(null) }
    var editableTasks by remember { mutableStateOf<List<EditableLiteracyTask>>(emptyList()) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val normalizedCharacters = characters.trim()
    val validInput = normalizedCharacters.isNotEmpty() && normalizedCharacters.all { it.isChineseCharacter() }
    val hasTasksToSave = editableTasks.isNotEmpty()

    Dialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(.92f).widthIn(max = 680.dp).heightIn(max = 760.dp).padding(18.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (preview == null) "智能添加识字" else "确认识字内容", style = MaterialTheme.typography.headlineSmall, color = Ink)
                        Text(
                            if (preview == null) "DeepSeek V4 Flash 会按字库生成学习内容" else "字不可修改；词和句子可按需要修改",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF7D898C)
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isWorking) {
                        Icon(Icons.Filled.Close, "关闭", tint = Ink)
                    }
                }
                if (preview == null) {
                    OutlinedTextField(
                        value = characters,
                        onValueChange = {
                            characters = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWorking,
                        singleLine = true,
                        label = { Text("要添加的汉字") },
                        placeholder = { Text("例如：春夏秋冬") },
                        supportingText = {
                            Text("仅限汉字；去重后一次最多 12 个字", color = Color(0xFF7D898C))
                        },
                        isError = characters.isNotBlank() && !validInput,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Sky,
                            unfocusedBorderColor = Line,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = WheatLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "每个字生成词语和句子。词语优先使用字库里的字加本次输入字；句子允许少量字库外生字，不限制字数。",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = Ink
                        )
                    }
                } else {
                    preview?.knownCharacters?.takeIf { it.isNotEmpty() }?.let { knownCharacters ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = WheatLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "以下汉字已在字库中，仍已生成内容，可按需要删除：${knownCharacters.joinToString("、")}",
                                modifier = Modifier.padding(14.dp),
                                fontSize = 13.sp,
                                color = Ink
                            )
                        }
                    }
                    preview?.skippedExistingCharacters?.takeIf { it.isNotEmpty() }?.let { skipped ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = WheatLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "已存在待认识任务，不会覆盖：${skipped.joinToString("、")}",
                                modifier = Modifier.padding(14.dp),
                                fontSize = 13.sp,
                                color = Ink
                            )
                        }
                    }
                    if (hasTasksToSave) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(editableTasks, key = { it.character }) { task ->
                                EditableLiteracyTaskCard(
                                    task = task,
                                    isKnownCharacter = preview?.knownCharacters?.contains(task.character) == true,
                                    onWordsChange = { words ->
                                        editableTasks = editableTasks.map {
                                            if (it.character == task.character) it.copy(wordsText = words) else it
                                        }
                                        errorMessage = null
                                    },
                                    onSentenceChange = { sentence ->
                                        editableTasks = editableTasks.map {
                                            if (it.character == task.character) it.copy(sentence = sentence) else it
                                        }
                                        errorMessage = null
                                    },
                                    onDelete = {
                                        editableTasks = editableTasks.filter { it.character != task.character }
                                        errorMessage = null
                                    }
                                )
                            }
                        }
                    } else {
                        Text("输入的汉字都已在待认识任务中。", color = Color(0xFF7D898C))
                    }
                }
                errorMessage?.let { Text(it, color = Coral, fontSize = 13.sp) }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (preview == null) {
                                if (!validInput) {
                                    errorMessage = "请输入一个或多个汉字"
                                    return@launch
                                }
                                isWorking = true
                                val result = onPreview(normalizedCharacters)
                                if (result.isSuccess) {
                                    preview = result.getOrThrow()
                                    editableTasks = preview!!.tasks.map {
                                        EditableLiteracyTask(
                                            character = it.character,
                                            wordsText = it.words.joinToString("、") { example -> example.text },
                                            sentence = it.sentence.text
                                        )
                                    }
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "生成识字内容失败，请稍后重试"
                                }
                                isWorking = false
                            } else {
                                val tasks = editableTasks.map {
                                    GeneratedLiteracyTask(
                                        character = it.character,
                                        words = it.wordsText.split(Regex("[、，,\\s]+")).filter(String::isNotBlank).mapIndexed { index, text ->
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
                                val result = onSave(normalizedCharacters, tasks)
                                if (result.isSuccess) onDismiss()
                                else errorMessage = result.exceptionOrNull()?.message ?: "保存识字任务失败，请稍后重试"
                                isWorking = false
                            }
                        }
                    },
                    enabled = !isWorking && (preview == null || hasTasksToSave),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(if (preview == null) "正在生成…" else "正在保存…")
                    } else {
                        Icon(Icons.Filled.AddCircle, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (preview == null) "生成内容" else "确认并添加到待认识")
                    }
                }
            }
        }
    }
}

private data class EditableLiteracyTask(
    val character: String,
    val wordsText: String,
    val sentence: String
)

@Composable
private fun EditableLiteracyTaskCard(
    task: EditableLiteracyTask,
    isKnownCharacter: Boolean,
    onWordsChange: (String) -> Unit,
    onSentenceChange: (String) -> Unit,
    onDelete: () -> Unit
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
                TextButton(onClick = onDelete) {
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
    viewModel: HelpedCharactersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        BackHeader("帮助过的内容", "这些词和句请求朗读过", onBack)
        Spacer(Modifier.height(18.dp))
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral)
            }
            state.errorMessage != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(state.errorMessage!!, color = Color(0xFF839094))
                Button(onClick = { viewModel.load(userId) }, colors = ButtonDefaults.buttonColors(containerColor = Sky)) { Text("重新加载") }
            }
            state.contents.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有请求过帮助的内容", color = Color(0xFF839094), fontSize = 16.sp)
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            pendingExportContent = state.contents.toHelpedContentsTxt()
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            createDocumentLauncher.launch("帮助过的内容_$timestamp.txt")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("下载 TXT")
                    }
                    exportMessage?.let { message ->
                        Spacer(Modifier.width(10.dp))
                        Text(message, color = if (message == "已下载 TXT 文档") Leaf else Coral, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                state.deleteErrorMessage?.let { message ->
                    Text(message, color = Coral, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.contents, key = { it.id }) { item ->
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = WheatLight)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(item.highlightedTargetText(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                                    Text(if (item.targetType == "sentence") "请求朗读过的句子" else "请求朗读过的词", fontSize = 12.sp, color = Color(0xFF7D898C))
                                }
                                TextButton(
                                    onClick = { viewModel.delete(userId, item.id) },
                                    enabled = item.id !in state.deletingContentIds
                                ) {
                                    if (item.id in state.deletingContentIds) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Coral, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除这条帮助记录", tint = Coral)
                                        Spacer(Modifier.width(3.dp))
                                        Text("删除", color = Coral)
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

private fun List<HelpedContent>.toHelpedContentsTxt(): String {
    // 与列表逐项保持一致：红色高亮的字只是词、句里的定位信息，不单独导出。
    return joinToString(separator = "\n") { it.targetText }
}

private fun HelpedContent.highlightedTargetText() = buildAnnotatedString {
    append(targetText)
    val highlightStart = characterIndex?.takeIf { index ->
        requestedCharacter?.length == 1 && targetText.getOrNull(index)?.toString() == requestedCharacter
    } ?: return@buildAnnotatedString
    addStyle(
        style = SpanStyle(color = EvaluationErrorRed, fontWeight = FontWeight.ExtraBold),
        start = highlightStart,
        end = highlightStart + 1
    )
}

@Composable
private fun MetricCard(value: String, label: String, emoji: String, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(18.dp)) { Text(emoji, fontSize = 25.sp); Text(value, style = MaterialTheme.typography.headlineMedium, color = Ink); Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF728086)) }
    }
}

@Composable
private fun ProfileMenuCard(title: String, sub: String, icon: ImageVector, color: Color, light: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = light, modifier = Modifier.size(54.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(29.dp)) } }
            Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, color = Ink); Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF839094)) }; Icon(Icons.Filled.KeyboardArrowRight, null, tint = color)
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
    LaunchedEffect(userId) { viewModel.load(userId) }
    LaunchedEffect(state.topNotice) {
        state.topNotice?.let {
            onNotice(it)
            viewModel.consumeTopNotice()
        }
    }

    if (state.isLoadingPhonetics) {
        Dialog(onDismissRequest = viewModel::dismissPhonetics) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Sky, strokeWidth = 2.dp)
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
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("无法加载发音标注", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text(message, color = EvaluationErrorRed)
                    Button(onClick = viewModel::dismissPhonetics, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        BackHeader("已认识的字", "每一粒小麦，都是你的进步", onBack)
        Spacer(Modifier.height(15.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LeafLight)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎓", fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("已经认识 ${state.characters.size} 个汉字", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("置顶后，该字会从今天起重新学习 3 天", color = Leaf, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingContent() }
            state.characters.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyContent(
                    title = "还没有已认识的字",
                    subtitle = state.errorMessage ?: "完成一个认字任务后，它会出现在这里",
                    emoji = "🌱"
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                items(state.characters, key = { it.id.ifBlank { it.character } }) { character ->
                    RecognizedCharacterCard(
                        character = character,
                        isArchiving = character.id in archivingCharacterIds,
                        isTopping = state.toppingCharacterId == character.id,
                        onDetails = { viewModel.showPhonetics(character) },
                        onTop = { viewModel.topCharacter(character) },
                        onArchive = {
                            if (character.id.isBlank() || character.id in archivingCharacterIds) return@RecognizedCharacterCard
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
                        }
                    )
                }
            }
        }
    }
    if (state.isLoading) RequestLoadingOverlay()
}

@Composable
private fun BackHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White)) { Icon(Icons.Filled.ArrowBack, "返回", tint = Ink) }
        Spacer(Modifier.width(10.dp)); Column { Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF849094)) }
    }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = LeafLight, modifier = Modifier.size(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(character.character, fontSize = 33.sp, color = Ink, fontWeight = FontWeight.ExtraBold) }
            }
            Text("收录于 ${character.recognizedAt?.take(10) ?: "未知日期"}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7D898C))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDetails,
                    enabled = !isArchiving && !isTopping,
                    modifier = Modifier.weight(1f).height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sky),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                ) { Text("查看", fontSize = 12.sp) }
                Button(
                    onClick = onTop,
                    enabled = !isArchiving && !isTopping,
                    modifier = Modifier.weight(1f).height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Wheat, contentColor = Ink),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
                ) {
                    if (isTopping) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Ink, strokeWidth = 2.dp)
                    else Text("置顶", fontSize = 12.sp)
                }
                Button(
                    onClick = onArchive,
                    enabled = !isArchiving && !isTopping,
                    modifier = Modifier.weight(1f).height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                ) {
                    if (isArchiving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("存库", fontSize = 12.sp)
                    }
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
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Sky, strokeWidth = 2.dp)
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
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("无法加载发音标注", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text(message, color = EvaluationErrorRed)
                    Button(onClick = viewModel::dismissPhonetics, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        BackHeader("待认识的字", "这些汉字正等着和你见面", onBack)
        Spacer(Modifier.height(15.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = WheatLight) {
            Text(
                "待认识  ${state.characters.size} 个字",
                color = Ink,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingContent()
            }
            state.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyContent("暂时无法加载", state.errorMessage.orEmpty(), "📖")
            }
            state.characters.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyContent("暂时没有待认识的字", "完成新的识字任务后会显示在这里。", "📖")
            }
            else -> Column(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 58.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                ) {
                    items(state.characters, key = { it.id }) { character ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { viewModel.showPhonetics(character) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(character.character, fontSize = 28.sp, color = Ink, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
                Text(
                    "已显示全部待认识的字",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    color = Color(0xFF879296),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f).widthIn(max = 760.dp).padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${detail.character.character} 的发音标注", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.ExtraBold)
                        Text("词句内容只读；可修正腾讯使用的数字拼音", color = Color(0xFF748184), fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }
                }
                errorMessage?.let { Text(it, color = EvaluationErrorRed, fontSize = 13.sp) }
                if (detail.assets.isEmpty()) {
                    EmptyContent("暂时没有发音标注", "词句保存后会自动准备，请稍后再试。", "🔤")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 4.dp)
                    ) {
                        items(detail.assets, key = { it.id }) { asset ->
                            PendingPhoneticAssetCard(
                                asset = asset,
                                isSaving = savingAssetId == asset.id,
                                onSave = { tokens -> onSave(asset, tokens) }
                            )
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F3F2), contentColor = Ink),
                    shape = RoundedCornerShape(17.dp)
                ) { Text("关闭") }
            }
        }
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
                    fontSize = 12.sp
                )
            }
            asset.lastError?.let { Text("自动生成：$it", color = EvaluationErrorRed, fontSize = 12.sp) }
            if (characters.isEmpty()) {
                Text("此内容没有可编辑的汉字", color = EvaluationErrorRed, fontSize = 13.sp)
            } else {
                characters.forEachIndexed { index, character ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = WheatLight, modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(character, color = Ink, fontWeight = FontWeight.ExtraBold) }
                        }
                        if (drafts[index] == null) {
                            Text("腾讯不支持指定轻声", color = Color(0xFF748184), fontSize = 13.sp, modifier = Modifier.weight(1f))
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
                    shape = RoundedCornerShape(13.dp)
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
    val state by viewModel.uiState.collectAsState()
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
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 30.dp),
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
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        BackHeader("字库", "把认识的汉字收进我的小书架", onBack)
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = WheatLight) { Text("本地字库  ${state.characters.size} 个字", color = Ink, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) }
            Spacer(Modifier.width(10.dp))
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
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { viewModel.prepareExport() },
                enabled = !state.isLoading && !state.isRefreshing && !state.isPreparingExport && state.pendingExportContent == null,
                colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("导出字库")
            }
        }
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
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索汉字") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = Sky) }, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sky, unfocusedBorderColor = Line, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
        Spacer(Modifier.height(14.dp))
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingContent() }
            state.characters.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.hasLocalData) {
                    EmptyContent("字库还没有汉字", "点击更新字库后会保存最新数据。", "📚")
                } else {
                    EmptyContent("本地暂无字库数据", "请点击“更新字库”下载并保存汉字。", "📚")
                }
            }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyContent("没有找到这个字", "换一个汉字试试看吧", "🔎")
            }
            else -> Column(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 58.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                ) {
                    items(visible, key = { it.character }) { knownCharacter ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                            modifier = Modifier.aspectRatio(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(knownCharacter.character, fontSize = 28.sp, color = Ink, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
                if (query.isBlank()) Text("已显示本地保存的全部字库", modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), color = Color(0xFF879296), textAlign = TextAlign.Center, fontSize = 13.sp)
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
