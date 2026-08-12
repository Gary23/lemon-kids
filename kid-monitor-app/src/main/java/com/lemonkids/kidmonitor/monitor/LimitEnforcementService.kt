package com.lemonkids.kidmonitor.monitor

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.lemonkids.kidmonitor.MainActivity
import com.lemonkids.kidmonitor.R
import com.lemonkids.shared.model.AppLimit
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

class LimitEnforcementService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        val appUsageRepository: AppUsageRepository
        val authRepository: AuthRepository
    }

    private lateinit var windowManager: WindowManager
    private lateinit var stateStore: AppLimitStateStore
    private var floatingView: View? = null
    private var floatingLabelText: TextView? = null
    private var floatingTimeText: TextView? = null
    private var floatingProgress: ProgressBar? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var limits = listOf<AppLimit>()
    private var currentForegroundPackage: String? = null
    private var screenStateReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "屏幕关闭，结束当前应用会话")
                    endCurrentSession()
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "用户解锁，等待下一次前台检测")
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var monitorJob: Job? = null
    private var limitRefreshJob: Job? = null
    private var usageUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        stateStore = AppLimitStateStore(applicationContext)
        limits = stateStore.loadLimits()
        registerScreenStateReceiver()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务启动 onStartCommand")
        showFloatingWindow()
        startLimitRefresh()
        startMonitoring()
        startUsageUpdates()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "任务被移除，1秒后重启")
        scheduleServiceRestart(delayMs = 1000L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
        endCurrentSession()
        unregisterScreenStateReceiver()
        removeFloatingWindow()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingWindow() {
        if (AppLimitAccessibilityService.isEnabled(this)) return
        if (floatingView != null) return
        runCatching {
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_usage, null)
            floatingLabelText = floatingView!!.findViewById(R.id.tv_usage_label)
            floatingTimeText = floatingView!!.findViewById(R.id.tv_usage_time)
            floatingProgress = floatingView!!.findViewById(R.id.progress_usage_remaining)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 200
            }
            attachFloatingDrag(floatingView!!, params)
            windowManager.addView(floatingView, params)
            floatingParams = params
            floatingView?.visibility = View.GONE
            Log.d(TAG, "悬浮窗已显示")
        }.onFailure {
            Log.w(TAG, "悬浮窗显示失败，仅保留限制服务", it)
            floatingView = null
            floatingLabelText = null
            floatingTimeText = null
            floatingProgress = null
            floatingParams = null
        }
    }

    private fun removeFloatingWindow() {
        floatingView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        floatingView = null
        floatingLabelText = null
        floatingTimeText = null
        floatingProgress = null
        floatingParams = null
    }

    private fun attachFloatingDrag(view: View, params: WindowManager.LayoutParams) {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - startRawX).toInt()
                    params.y = startY + (event.rawY - startRawY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> true
            }
        }
    }

    private fun startLimitRefresh() {
        limitRefreshJob?.cancel()
        limitRefreshJob = scope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ServiceEntryPoint::class.java
            )
            val auth = entryPoint.authRepository

            while (auth.currentUserId == null) {
                Log.d(TAG, "等待用户登录...")
                delay(3000L)
            }

            val childId = auth.currentUserId ?: return@launch
            Log.d(TAG, "开始同步限制规则 childId=$childId")
            entryPoint.appUsageRepository.observeAppLimits(childId).collect { list ->
                val activeLimits = list.filter { it.isActive }
                if (activeLimits.isEmpty() && limits.isNotEmpty()) {
                    Log.w(TAG, "限制同步返回空列表，保留本地缓存: cached=${limits.size}")
                    return@collect
                }

                limits = activeLimits
                stateStore.saveLimits(limits)
                Log.d(TAG, "限制已更新: ${limits.size} 条")
            }
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var tick = 0
            while (true) {
                try {
                    checkAndRecordUsage()
                    tick++
                    if (tick % 12 == 0) {
                        Log.d(TAG, "心跳 #$tick fg=$currentForegroundPackage limits=${limits.size}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "监控循环异常", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun startUsageUpdates() {
        usageUpdateJob?.cancel()
        usageUpdateJob = scope.launch {
            while (true) {
                if (AppLimitAccessibilityService.isEnabled(applicationContext)) {
                    withContext(Dispatchers.Main) { hideFloatingDisplay() }
                } else {
                    updateFloatingDisplay()
                }
                delay(FLOATING_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun checkAndRecordUsage() {
        if (!isScreenInteractive()) {
            endCurrentSession()
            AppLimitAccessibilityService.hideUsageHint()
            hideFloatingDisplay()
            return
        }

        val detectedPkg = detectForegroundPackage()
        if (detectedPkg == null) {
            endCurrentSession()
            AppLimitAccessibilityService.hideUsageHint()
            hideFloatingDisplay()
            return
        }

        if (shouldIgnorePackage(detectedPkg)) {
            if (currentForegroundPackage != null) {
                Log.d(TAG, "前台切换到忽略包: $currentForegroundPackage -> $detectedPkg")
            }
            endTrackedSessions()
            AppLimitAccessibilityService.hideUsageHint()
            hideFloatingDisplay()
            return
        }

        val foregroundPkg = detectedPkg
        if (shouldIgnorePackage(foregroundPkg)) {
            endCurrentSession()
            AppLimitAccessibilityService.hideUsageHint()
            hideFloatingDisplay()
            return
        }

        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val previous = currentForegroundPackage
        val limit = limits.firstOrNull { it.packageName == foregroundPkg && it.isActive }

        if (foregroundPkg != previous) {
            previous?.let { stateStore.endSession(it, now, today) }
            currentForegroundPackage = foregroundPkg
            stateStore.startSession(
                packageName = foregroundPkg,
                nowMs = now,
                today = today,
                cooldownMinutes = limit?.cooldownMinutes ?: 0
            )
            Log.d(TAG, "前台切换: $previous -> $foregroundPkg")
        }

        if (limit == null) {
            stateStore.addUsage(foregroundPkg, CHECK_INTERVAL_MS, today)
        } else {
            val systemTodayMs = querySystemTodayUsageMs(foregroundPkg, now)
            if (systemTodayMs != null) {
                stateStore.updateTodayUsage(foregroundPkg, systemTodayMs, today)
            } else {
                stateStore.addUsage(foregroundPkg, CHECK_INTERVAL_MS, today)
            }
        }

        if (limit == null) {
            AppLimitAccessibilityService.hideUsageHint()
            hideFloatingDisplay()
            return
        }

        val state = stateForActiveDisplay(foregroundPkg, limit, now, today)

        val decision = AppLimitEvaluator.evaluate(
            packageName = foregroundPkg,
            nowMs = now,
            today = today,
            state = state,
            limit = limit
        )
        if (decision is LimitDecision.Blocked) {
            Log.d(
                TAG,
                "限制命中: pkg=$foregroundPkg reason=${decision.reason} todayMs=${state.todayMs} " +
                    "daily=${limit.dailyLimitMinutes} session=${limit.singleSessionMinutes} " +
                    "cooldown=${limit.cooldownMinutes}"
            )
            val cooldownUntilMs = decision.cooldownUntilMs
            stateStore.endSession(foregroundPkg, now, today, cooldownUntilMs)
            currentForegroundPackage = null
            hideFloatingDisplay()
            AppLimitAccessibilityService.hideUsageHint()
            AppLimitFeedback.showAfterHome(applicationContext, decision)
        } else {
            if (AppLimitAccessibilityService.isEnabled(this)) {
                hideFloatingDisplay()
                val shownByAccessibility = AppLimitAccessibilityService.showUsageHintForForeground(
                    packageName = foregroundPkg,
                    limit = limit,
                    state = state,
                    now = now
                )
                if (!shownByAccessibility) {
                    updateFloatingDisplay(limit, state, now)
                }
            } else {
                updateFloatingDisplay(limit, state, now)
            }
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }
        screenStateReceiverRegistered = true
    }

    private fun unregisterScreenStateReceiver() {
        if (!screenStateReceiverRegistered) return
        runCatching { unregisterReceiver(screenStateReceiver) }
        screenStateReceiverRegistered = false
    }

    private fun scheduleServiceRestart(delayMs: Long) {
        val restartIntent = Intent(this, LimitEnforcementService::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, RESTART_REQUEST_CODE, restartIntent, flags)
        } else {
            PendingIntent.getService(this, RESTART_REQUEST_CODE, restartIntent, flags)
        }
        val alarmManager = getSystemService(AlarmManager::class.java)
        val triggerAtMs = SystemClock.elapsedRealtime() + delayMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        }
    }

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isInteractive
    }

    private fun endCurrentSession() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        currentForegroundPackage?.let { stateStore.endSession(it, now, today) }
        currentForegroundPackage = null
        hideFloatingDisplay()
    }

    private fun endTrackedSessions() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val current = currentForegroundPackage
        current?.let { stateStore.endSession(it, now, today) }
        limits.forEach { limit ->
            if (limit.packageName != current) {
                stateStore.endSession(limit.packageName, now, today)
            }
        }
        currentForegroundPackage = null
        hideFloatingDisplay()
    }

    private fun shouldIgnorePackage(packageName: String): Boolean {
        return packageName == this.packageName ||
            packageName == "android" ||
            packageName == "com.android.settings" ||
            packageName in launcherPackages()
    }

    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }

    private fun detectForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()

        val recent = inferForegroundFromEvents(
            usageStatsManager = usageStatsManager,
            startMs = now - 10_000L,
            endMs = now,
            initialPackage = currentForegroundPackage
        )
        if (recent.hasEvents) return recent.packageName

        if (currentForegroundPackage == null) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return inferForegroundFromEvents(
                usageStatsManager = usageStatsManager,
                startMs = cal.timeInMillis,
                endMs = now,
                initialPackage = null
            )
                .packageName
        }

        return currentForegroundPackage
    }

    private fun inferForegroundFromEvents(
        usageStatsManager: UsageStatsManager,
        startMs: Long,
        endMs: Long,
        initialPackage: String?
    ): ForegroundEventsResult {
        val events = usageStatsManager.queryEvents(startMs, endMs)
        var activePackage = initialPackage
        var hasEvents = false
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    activePackage = event.packageName
                    hasEvents = true
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (activePackage == event.packageName) {
                        activePackage = null
                    }
                    hasEvents = true
                }
            }
        }
        return ForegroundEventsResult(activePackage, hasEvents)
    }

    private fun querySystemTodayUsageMs(packageName: String, now: Long): Long? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                cal.timeInMillis,
                now
            )
                .filter { it.packageName == packageName }
                .sumOf { it.totalTimeInForeground }
        }.getOrNull()
    }

    private suspend fun updateFloatingDisplay() {
        val packageName = currentForegroundPackage
        val limit = limits.firstOrNull { it.packageName == packageName && it.isActive }
        if (packageName == null || limit == null || !isScreenInteractive()) {
            withContext(Dispatchers.Main) { hideFloatingDisplay() }
            return
        }

        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val state = stateForActiveDisplay(packageName, limit, now, today)
        withContext(Dispatchers.Main) {
            updateFloatingDisplay(limit, state, now)
        }
    }

    private fun stateForActiveDisplay(
        packageName: String,
        limit: AppLimit,
        now: Long,
        today: String
    ): TrackedAppState {
        val state = stateStore.getState(packageName, today)
        if (state.sessionStartMs > 0L) return state
        return if (limit.singleSessionMinutes > 0 && limit.cooldownMinutes > 0) {
            stateStore.startSession(
                packageName = packageName,
                nowMs = now,
                today = today,
                cooldownMinutes = limit.cooldownMinutes
            )
        } else {
            state
        }
    }

    private fun updateFloatingDisplay(limit: AppLimit, state: TrackedAppState, now: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateFloatingDisplay(limit, state, now) }
            return
        }

        val usage = AppLimitUsageDisplay.calculate(limit, state, now)
        if (usage == null) {
            hideFloatingDisplay()
            return
        }

        val label = AppLimitUsageDisplay.labelFor(usage.scope)
        val timeText = AppLimitUsageDisplay.formatRemainingTime(usage.remainingMs)
        if (AppLimitAccessibilityService.isEnabled(this)) {
            floatingView?.visibility = View.GONE
            return
        }

        floatingLabelText?.text = label
        floatingTimeText?.text = timeText
        floatingProgress?.progress = usage.progressPercent
        floatingView?.visibility = View.VISIBLE
    }

    private fun hideFloatingDisplay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideFloatingDisplay() }
            return
        }

        floatingView?.visibility = View.GONE
    }

    private fun createNotification(): Notification {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (AppLimitAccessibilityService.isEnabled(this)) {
            "正在同步并执行应用限制"
        } else {
            "无障碍权限未开启，应用限制未完全生效"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("应用限制")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用限制服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "LimitEnforce"
        private const val CHANNEL_ID = "limit_enforcement_channel"
        private const val NOTIFICATION_ID = 2002
        private const val RESTART_REQUEST_CODE = 2003
        private const val CHECK_INTERVAL_MS = 1000L
        private const val FLOATING_UPDATE_INTERVAL_MS = 1000L

        private data class ForegroundEventsResult(
            val packageName: String?,
            val hasEvents: Boolean
        )

        fun start(context: Context) {
            val intent = Intent(context, LimitEnforcementService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LimitEnforcementService::class.java))
        }
    }
}
