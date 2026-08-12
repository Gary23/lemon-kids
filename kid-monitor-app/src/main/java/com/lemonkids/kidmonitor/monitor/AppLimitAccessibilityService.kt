package com.lemonkids.kidmonitor.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.lemonkids.kidmonitor.R
import com.lemonkids.shared.model.DeviceStatusEventType
import java.lang.ref.WeakReference
import java.time.LocalDate

class AppLimitAccessibilityService : AccessibilityService() {
    private lateinit var stateStore: AppLimitStateStore
    private val mainHandler = Handler(Looper.getMainLooper())
    private var blockFeedbackView: View? = null
    private var usageHintView: View? = null
    private var usageHintLabelText: TextView? = null
    private var usageHintTimeText: TextView? = null
    private var usageHintProgress: ProgressBar? = null
    private var usageHintParams: WindowManager.LayoutParams? = null
    private var currentUsageHintPackage: String? = null
    private var usageHintRefreshRunning = false
    private var pendingHideToken = 0
    private val usageHintRefreshRunnable = object : Runnable {
        override fun run() {
            refreshUsageHint()
            if (currentUsageHintPackage != null) {
                mainHandler.postDelayed(this, USAGE_HINT_REFRESH_MS)
            } else {
                usageHintRefreshRunning = false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
        stateStore = AppLimitStateStore(applicationContext)
        LimitEnforcementService.start(applicationContext)
    }

    override fun onDestroy() {
        if (activeService?.get() === this) {
            activeService = null
        }
        DeviceStatusWorker.reportNow(applicationContext, DeviceStatusEventType.ACCESSIBILITY_DISABLED)
        stopUsageHintRefresh()
        removeUsageHint()
        removeBlockFeedback()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        val className = event.className?.toString().orEmpty()
        if (isOwnOverlayEvent(packageName, className)) return
        if (shouldIgnorePackage(packageName)) {
            scheduleUsageHintHide(packageName)
            return
        }

        val limits = stateStore.loadLimits()
        val limit = limits.firstOrNull { it.isActive && it.packageName == packageName }
        if (limit == null) {
            scheduleUsageHintHide(packageName)
            return
        }
        cancelPendingUsageHintHide()
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val state = stateStore.startSession(
            packageName = packageName,
            nowMs = now,
            today = today,
            cooldownMinutes = limit.cooldownMinutes
        )
        val decision = AppLimitEvaluator.evaluate(packageName, now, today, state, limit)

        if (decision is LimitDecision.Blocked) {
            if (decision.cooldownUntilMs != null) {
                stateStore.endSession(packageName, now, today, decision.cooldownUntilMs)
            }
            stopUsageHintRefresh()
            removeUsageHint()
            AppLimitFeedback.showAfterHome(applicationContext, decision)
        } else {
            currentUsageHintPackage = packageName
            showUsageHintFor(limit, state, now)
            startUsageHintRefresh()
        }
    }

    override fun onInterrupt() = Unit

    private fun shouldIgnorePackage(packageName: String): Boolean {
        return packageName == applicationContext.packageName ||
            packageName == "android" ||
            packageName == SETTINGS_PACKAGE ||
            packageName in launcherPackages()
    }

    private fun isOwnOverlayEvent(packageName: String, className: String): Boolean {
        if (packageName != applicationContext.packageName) return false
        if (usageHintView == null) return false
        return !className.startsWith(applicationContext.packageName)
    }

    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }

    private fun showBlockedFeedbackAfterHome(decision: LimitDecision.Blocked) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed(
            { showBlockFeedback(decision) },
            HOME_TO_BLOCK_DELAY_MS
        )
    }

    private fun showBlockFeedback(decision: LimitDecision.Blocked) {
        if (blockFeedbackView != null) return

        removeUsageHint()
        removeBlockFeedback()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 0, 0, 0))
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            isClickable = true
            isFocusable = true
        }

        root.addView(TextView(this).apply {
            text = "⏰"
            textSize = 48f
            gravity = Gravity.CENTER
        })

        root.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            24
        ))

        root.addView(TextView(this).apply {
            text = decision.title
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        root.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            16
        ))

        root.addView(TextView(this).apply {
            text = decision.message
            textSize = 16f
            setTextColor(Color.argb(210, 255, 255, 255))
            gravity = Gravity.CENTER
        })

        root.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            40
        ))

        root.addView(Button(this).apply {
            text = "好，我去休息"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(220, 76, 175, 80))
            setOnClickListener {
                removeBlockFeedback()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        runCatching {
            getSystemService(WindowManager::class.java).addView(root, params)
            blockFeedbackView = root
            Log.d(TAG, "无障碍阻挡提示已显示")
        }.onFailure {
            Log.w(TAG, "无障碍阻挡提示显示失败", it)
        }
    }

    private fun removeBlockFeedback() {
        val view = blockFeedbackView ?: return
        runCatching {
            getSystemService(WindowManager::class.java).removeView(view)
        }
        blockFeedbackView = null
    }

    private fun showUsageHint(label: String, timeText: String, progressPercent: Int) {
        mainHandler.post {
            val view = usageHintView ?: createUsageHintView()
            usageHintLabelText?.text = label
            usageHintTimeText?.text = timeText
            usageHintProgress?.progress = progressPercent.coerceIn(0, 100)
            if (usageHintView == null) {
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = dp(50)
                    y = dp(200)
                }

                runCatching {
                    getSystemService(WindowManager::class.java).addView(view, params)
                    usageHintView = view
                    usageHintParams = params
                    Log.d(TAG, "无障碍剩余时间提示已显示")
                }.onFailure {
                    usageHintView = null
                    usageHintLabelText = null
                    usageHintTimeText = null
                    usageHintProgress = null
                    usageHintParams = null
                    Log.w(TAG, "无障碍剩余时间提示显示失败", it)
                }
            }
        }
    }

    private fun showUsageHintFor(limit: com.lemonkids.shared.model.AppLimit, state: TrackedAppState, now: Long) {
        val usage = AppLimitUsageDisplay.calculate(limit, state, now)
        if (usage == null) {
            removeUsageHint()
            return
        }
        showUsageHint(
            label = AppLimitUsageDisplay.labelFor(usage.scope),
            timeText = AppLimitUsageDisplay.formatRemainingTime(usage.remainingMs),
            progressPercent = usage.progressPercent
        )
    }

    private fun showUsageHintForForeground(
        packageName: String,
        limit: com.lemonkids.shared.model.AppLimit,
        state: TrackedAppState,
        now: Long
    ) {
        cancelPendingUsageHintHide()
        currentUsageHintPackage = packageName
        showUsageHintFor(limit, state, now)
        startUsageHintRefresh()
    }

    private fun startUsageHintRefresh() {
        if (usageHintRefreshRunning) return
        usageHintRefreshRunning = true
        mainHandler.postDelayed(usageHintRefreshRunnable, USAGE_HINT_REFRESH_MS)
    }

    private fun stopUsageHintRefresh() {
        currentUsageHintPackage = null
        usageHintRefreshRunning = false
        mainHandler.removeCallbacks(usageHintRefreshRunnable)
    }

    private fun scheduleUsageHintHide(packageName: String) {
        if (currentUsageHintPackage == null && usageHintView == null) return
        val token = ++pendingHideToken
        mainHandler.postDelayed(
            {
                if (token != pendingHideToken) return@postDelayed
                Log.d(TAG, "隐藏剩余时间提示: foreground=$packageName")
                stopUsageHintRefresh()
                removeUsageHint()
            },
            USAGE_HINT_HIDE_DEBOUNCE_MS
        )
    }

    private fun cancelPendingUsageHintHide() {
        pendingHideToken++
    }

    private fun refreshUsageHint() {
        val packageName = currentUsageHintPackage ?: return
        val limit = stateStore.loadLimits().firstOrNull { it.isActive && it.packageName == packageName }
        if (limit == null) {
            stopUsageHintRefresh()
            removeUsageHint()
            return
        }

        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val state = stateForActiveHint(packageName, limit, now, today)
        val decision = AppLimitEvaluator.evaluate(packageName, now, today, state, limit)
        if (decision is LimitDecision.Blocked) {
            stopUsageHintRefresh()
            removeUsageHint()
            return
        }

        showUsageHintFor(limit, state, now)
    }

    private fun stateForActiveHint(
        packageName: String,
        limit: com.lemonkids.shared.model.AppLimit,
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

    private fun createUsageHintView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundResource(R.drawable.floating_bg)
        }

        val textRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            usageHintLabelText = TextView(this@AppLimitAccessibilityService).apply {
                text = "还可以用 "
                textSize = 14f
                setTextColor(Color.rgb(51, 51, 51))
            }
            usageHintTimeText = TextView(this@AppLimitAccessibilityService).apply {
                textSize = 16f
                setTextColor(Color.rgb(255, 107, 53))
                setTypeface(typeface, Typeface.BOLD)
            }
            addView(usageHintLabelText)
            addView(usageHintTimeText)
        }

        usageHintProgress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
        }

        root.addView(textRow)
        root.addView(
            usageHintProgress,
            LinearLayout.LayoutParams(dp(112), dp(6)).apply {
                topMargin = dp(6)
            }
        )
        attachUsageHintDrag(root)
        return root
    }

    private fun attachUsageHintDrag(view: View) {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0

        view.setOnTouchListener { _, event ->
            val params = usageHintParams ?: return@setOnTouchListener false
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
                    runCatching {
                        getSystemService(WindowManager::class.java).updateViewLayout(view, params)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun removeUsageHint() {
        mainHandler.post {
            val view = usageHintView ?: return@post
            runCatching {
                getSystemService(WindowManager::class.java).removeView(view)
            }
            usageHintView = null
            usageHintLabelText = null
            usageHintTimeText = null
            usageHintProgress = null
            usageHintParams = null
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "AppLimitA11y"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val HOME_TO_BLOCK_DELAY_MS = 250L
        private const val USAGE_HINT_REFRESH_MS = 1000L
        private const val USAGE_HINT_HIDE_DEBOUNCE_MS = 600L
        private var activeService: WeakReference<AppLimitAccessibilityService>? = null

        fun goHome(): Boolean {
            return activeService?.get()?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        }

        fun showBlockedFeedbackAfterHome(decision: LimitDecision.Blocked): Boolean {
            val service = activeService?.get() ?: return false
            service.showBlockedFeedbackAfterHome(decision)
            return true
        }

        fun hideUsageHint() {
            activeService?.get()?.let {
                it.stopUsageHintRefresh()
                it.removeUsageHint()
            }
        }

        fun showUsageHintForForeground(
            packageName: String,
            limit: com.lemonkids.shared.model.AppLimit,
            state: TrackedAppState,
            now: Long
        ): Boolean {
            val service = activeService?.get() ?: return false
            service.showUsageHintForForeground(packageName, limit, state, now)
            return true
        }

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${AppLimitAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
