package com.lemonkids.kidmonitor.alarm

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import com.lemonkids.kidmonitor.monitor.AppLimitAccessibilityService

/** 已解锁状态的全屏普通悬浮层。锁屏一律交给全屏通知和 AlarmActivity。 */
class AlarmOverlayController(private val context: Context) : AlarmOverlayHost {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var usingAccessibilityOverlay = false

    override fun show(presentation: AlarmPresentation, onDismiss: () -> Unit): AlarmOverlayResult {
        // 无障碍已获用户明确授权时优先使用独立 API；失败后仍按一期普通悬浮窗降级。
        if (AppLimitAccessibilityService.isEnabled(context) &&
            AppLimitAccessibilityService.showAlarmOverlay(presentation, onDismiss)
        ) {
            usingAccessibilityOverlay = true
            return AlarmOverlayResult.Shown
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "未授予悬浮窗权限，改由全屏通知展示 alarmId=${presentation.alarmId}")
            return AlarmOverlayResult.PermissionMissing
        }
        remove()
        val close = Button(context).apply {
            text = AlarmPresentationUi.DISMISS_LABEL
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(255, 117, 168))
            textSize = 18f
            setOnClickListener { onDismiss() }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(Color.rgb(255, 238, 245))
            isClickable = true
            isFocusable = true
            addView(TextView(context).apply { text = AlarmPresentationUi.ICON; textSize = 88f; gravity = Gravity.CENTER })
            addView(space(20))
            addView(TextView(context).apply {
                text = presentation.title; textSize = 30f; setTextColor(Color.rgb(91, 35, 71)); gravity = Gravity.CENTER
            })
            addView(space(12))
            addView(TextView(context).apply {
                text = presentation.message; textSize = 18f; setTextColor(Color.rgb(106, 56, 83)); gravity = Gravity.CENTER
            })
            addView(space(42))
            addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        return runCatching {
            windowManager.addView(root, params)
            view = root
            usingAccessibilityOverlay = false
            AlarmOverlayResult.Shown
        }.getOrElse {
            Log.w(TAG, "闹钟悬浮层创建失败 alarmId=${presentation.alarmId}", it)
            AlarmOverlayResult.Failed(it)
        }
    }

    override fun remove() {
        if (usingAccessibilityOverlay) {
            AppLimitAccessibilityService.hideAlarmOverlay()
            usingAccessibilityOverlay = false
        }
        val current = view ?: return
        runCatching { windowManager.removeViewImmediate(current) }
        view = null
    }

    private fun space(heightDp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object { private const val TAG = "AlarmOverlay" }
}
