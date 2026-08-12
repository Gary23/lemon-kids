package com.lemonkids.kidmonitor.feature.profile

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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class KidProfileUiState(
    val userName: String = "",
    val totalPoints: Int = 0,
    val avatarUrl: String? = null,
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val isUsageLoading: Boolean = true,
    val todayUsageMinutes: Long = 0,
    val dailyLimitMinutes: Int = 0,
    val usagePermissionDenied: Boolean = false,
    val appLimits: List<KidAppLimitItem> = emptyList()
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
    private val appUsageRepository: AppUsageRepository,
    private val supabase: SupabaseClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(KidProfileUiState())
    val uiState: StateFlow<KidProfileUiState> = _uiState.asStateFlow()

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
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            _uiState.value = _uiState.value.copy(
                userName = user.name,
                totalPoints = user.totalPoints,
                avatarUrl = user.avatarUrl
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
