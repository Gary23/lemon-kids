package com.lemonkids.kidtask.feature.profile

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.AppLimit
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.GrowthRepository
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.shared.model.TaskStatus
import com.lemonkids.shared.model.GrowthSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class KidProfileUiState(
    val userName: String = "",
    val hasUser: Boolean = false,
    val totalPoints: Int = 0,
    val avatarUrl: String? = null,
    val growth: GrowthSnapshot? = null,
    val upgradedLevel: Int? = null,
    val isGrowthLoading: Boolean = true,
    val growthError: Boolean = false,
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val isUsageLoading: Boolean = true,
    val todayUsageMinutes: Long = 0,
    val dailyLimitMinutes: Int = 0,
    val usagePermissionDenied: Boolean = false,
    val appLimits: List<KidAppLimitItem> = emptyList(),
    val todayTaskTotal: Int = 0,
    val todayTaskCompleted: Int = 0
)

data class KidAppLimitItem(
    val appName: String,
    val packageName: String,
    val dailyLimitMinutes: Int,
    val singleSessionMinutes: Int,
    val cooldownMinutes: Int
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val growthRepository: GrowthRepository,
    private val taskRepository: TaskRepository,
    private val appUsageRepository: AppUsageRepository,
    private val supabase: SupabaseClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(KidProfileUiState())
    val uiState: StateFlow<KidProfileUiState> = _uiState.asStateFlow()
    private var activeGrowthUserId: String? = null
    private var growthRequestId = 0L
    private var todayTaskJob: Job? = null

    init {
        loadProfile()
        refreshUsage()
    }

    fun refreshUsage() {
        val permGranted = hasUsageStatsPermission()
        if (!permGranted) {
            _uiState.value = _uiState.value.copy(
                isUsageLoading = false,
                usagePermissionDenied = true
            )
            return
        }

        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val today = LocalDate.now()

            // 直接从系统 UsageStatsManager 读取实时总时长，不再依赖 Supabase 缓存
            val totalSeconds = withContext(Dispatchers.IO) {
                val usageStatsManager = context.getSystemService(
                    Context.USAGE_STATS_SERVICE
                ) as UsageStatsManager
                val startMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMs = System.currentTimeMillis()
                val statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startMs, endMs
                )
                statsList.sumOf { it.totalTimeInForeground } / 1000
            }

            _uiState.value = _uiState.value.copy(
                isUsageLoading = false,
                todayUsageMinutes = totalSeconds / 60,
                usagePermissionDenied = false
            )

            // 异步上传到 Supabase 保证家长端同步
            uploadCurrentUsage(userId, today)
        }

        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            appUsageRepository.observeAppLimits(userId).collect { limits ->
                val active = limits.filter { it.isActive }
                val totalLimit = active.sumOf { it.dailyLimitMinutes }
                val items = active.map {
                    KidAppLimitItem(
                        appName = it.appName,
                        packageName = it.packageName,
                        dailyLimitMinutes = it.dailyLimitMinutes,
                        singleSessionMinutes = it.singleSessionMinutes,
                        cooldownMinutes = it.cooldownMinutes
                    )
                }
                _uiState.value = _uiState.value.copy(
                    dailyLimitMinutes = totalLimit,
                    appLimits = items
                )
            }
        }
    }

    /** 异步上传当前系统使用数据到 Supabase，先删后插保证不重复 */
    private fun uploadCurrentUsage(userId: String, date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch
            val usageStatsManager = context.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager
            val startMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMs = System.currentTimeMillis()
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startMs, endMs
            )
            val pm = context.packageManager
            val records = statsList
                .filter { it.totalTimeInForeground > 0 }
                .map { stats ->
                    val appName = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(stats.packageName, 0)).toString()
                    }.getOrDefault(stats.packageName)
                    AppUsageRecord(
                        familyId = familyId,
                        childId = userId,
                        packageName = stats.packageName,
                        appName = appName,
                        durationSeconds = stats.totalTimeInForeground / 1000,
                        date = date.toString()
                    )
                }

            // 先删除当天旧数据，再插入新数据，避免重复累加
            val postgrest = supabase.pluginManager.getPlugin(Postgrest)
            runCatching {
                postgrest.from("app_usage").delete {
                    filter { eq("child_id", userId); eq("date", date.toString()) }
                }
            }
            if (records.isNotEmpty()) {
                appUsageRepository.uploadUsageRecords(records)
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadProfile() {
        viewModelScope.launch {
            authRepository.observeCurrentUser().collect { user ->
                if (activeGrowthUserId != user?.uid) {
                    activeGrowthUserId = user?.uid
                    growthRequestId++
                    todayTaskJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        growth = null, upgradedLevel = null,
                        isGrowthLoading = user != null, growthError = false,
                        todayTaskTotal = 0, todayTaskCompleted = 0
                    )
                    if (user != null) {
                        refreshGrowth()
                        observeTodayTasks(user.uid)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    userName = user?.name.orEmpty(),
                    hasUser = user != null,
                    totalPoints = user?.totalPoints ?: 0,
                    avatarUrl = user?.avatarUrl
                )
            }
        }
    }

    fun clearGrowthForSwitch() {
        activeGrowthUserId = null
        growthRequestId++
        todayTaskJob?.cancel()
        _uiState.value = _uiState.value.copy(
            growth = null, upgradedLevel = null, isGrowthLoading = false, growthError = false,
            todayTaskTotal = 0, todayTaskCompleted = 0
        )
    }

    private fun observeTodayTasks(childId: String) {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        todayTaskJob = viewModelScope.launch {
            taskRepository.observeTodayTasks(childId, today).collect { tasks ->
                if (activeGrowthUserId != childId) return@collect
                _uiState.value = _uiState.value.copy(
                    todayTaskTotal = tasks.size,
                    todayTaskCompleted = tasks.count { it.status == TaskStatus.DONE || it.status == TaskStatus.VERIFIED }
                )
            }
        }
    }

    fun refreshGrowth() {
        val userId = activeGrowthUserId ?: return
        val requestId = ++growthRequestId
        _uiState.value = _uiState.value.copy(isGrowthLoading = true, growthError = false)
        viewModelScope.launch {
            val result = growthRepository.getOwnSnapshot()
            if (requestId != growthRequestId || activeGrowthUserId != userId) return@launch
            result.fold(
                onSuccess = { snapshot ->
                    if (snapshot.childId != userId) {
                        _uiState.value = _uiState.value.copy(isGrowthLoading = false, growthError = true)
                    } else {
                        val oldLevel = _uiState.value.growth?.let { GrowthRules.level(it.totalExp).number }
                        val newLevel = GrowthRules.level(snapshot.totalExp).number
                        _uiState.value = _uiState.value.copy(
                            growth = snapshot, upgradedLevel = newLevel.takeIf { oldLevel != null && it > oldLevel },
                            isGrowthLoading = false, growthError = false
                        )
                    }
                },
                onFailure = {
                    Log.e("KidProfileVM", "成长快照读取失败", it)
                    _uiState.value = _uiState.value.copy(isGrowthLoading = false, growthError = true)
                }
            )
        }
    }

    private fun loadUsageData() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val today = LocalDate.now().toString()

            val records = appUsageRepository.getTodayUsage(userId, today)
            val totalSeconds = records.sumOf { it.durationSeconds }
            _uiState.value = _uiState.value.copy(
                isUsageLoading = false,
                todayUsageMinutes = totalSeconds / 60
            )
        }

        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            appUsageRepository.observeAppLimits(userId).collect { limits ->
                val active = limits.filter { it.isActive }
                val totalLimit = active.sumOf { it.dailyLimitMinutes }
                val items = active.map {
                    KidAppLimitItem(
                        appName = it.appName,
                        packageName = it.packageName,
                        dailyLimitMinutes = it.dailyLimitMinutes,
                        singleSessionMinutes = it.singleSessionMinutes,
                        cooldownMinutes = it.cooldownMinutes
                    )
                }
                _uiState.value = _uiState.value.copy(
                    dailyLimitMinutes = totalLimit,
                    appLimits = items
                )
            }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, fileName: String) {
        Log.d("KidProfileVM", "开始上传头像: $fileName, size=${imageBytes.size}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            try {
                val user = authRepository.observeCurrentUser().first()
                val userId = user?.uid
                if (userId == null) {
                    Log.e("KidProfileVM", "无法获取当前用户")
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        errorMessage = "请先登录"
                    )
                    return@launch
                }
                Log.d("KidProfileVM", "userId=$userId, 调用 uploadAndSetAvatar")

                val result = withTimeout(30_000L) {
                    authRepository.uploadAndSetAvatar(userId, imageBytes, fileName)
                }
                result.fold(
                    onSuccess = { url ->
                        Log.d("KidProfileVM", "头像上传成功: $url")
                        _uiState.value = _uiState.value.copy(
                            avatarUrl = url,
                            isUploading = false
                        )
                    },
                    onFailure = { e ->
                        Log.e("KidProfileVM", "头像上传失败", e)
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            errorMessage = "头像上传失败：${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("KidProfileVM", "头像上传异常", e)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = "上传超时或异常：${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun updateName(newName: String) {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            authRepository.updateName(userId, newName).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(userName = newName)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "更新失败：${it.message}"
                    )
                }
            )
        }
    }
}
