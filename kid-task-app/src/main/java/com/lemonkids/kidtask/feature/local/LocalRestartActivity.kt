package com.lemonkids.kidtask.feature.local

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lemonkids.kidtask.MainActivity
import com.lemonkids.shared.di.SupabaseEnvironment
import com.lemonkids.shared.auth.ChildBindingStore
import com.lemonkids.kidtask.reminder.TaskReminderScheduler
import com.lemonkids.kidtask.widget.TaskWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A visible activity in a separate process owns the handoff, without background launches. */
class LocalRestartActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var message: TextView
    private lateinit var retry: Button
    private val switchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running = false
    private var failed = false
    private var handedOff = false
    private var verified = false
    private var deadline = 0L
    private val targetUrl get() = intent.getStringExtra(EXTRA_TARGET_URL).orEmpty()
    private val oldPid get() = intent.getIntExtra(EXTRA_OLD_PID, -1)
    private val processes get() = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
        .runningAppProcesses.orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SupabaseEnvironment.isLocal || parseLocalServerAddress(targetUrl.removePrefix("http://")) != targetUrl) {
            finish()
            return
        }
        verified = savedInstanceState?.getBoolean("verified") ?: false
        message = TextView(this).apply { text = "正在切换服务，请稍候…" }
        retry = Button(this).apply {
            text = "重试"
            visibility = android.view.View.GONE
            setOnClickListener { failed = false; beginRestart() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(message)
            addView(retry)
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !failed && !handedOff && ::message.isInitialized) beginRestart()
    }

    private fun beginRestart() {
        if (running || !hasWindowFocus()) return
        running = true
        retry.visibility = android.view.View.GONE
        message.text = "正在切换服务，请稍候…"
        // Wait for the helper's foreground window before ending the old process.
        handler.postDelayed({
            if (!hasWindowFocus()) {
                running = false
                return@postDelayed
            }
            try {
                val target = processes.firstOrNull { it.pid == oldPid }
                if (!verified) {
                    if (target == null || !isLocalRestartTarget(
                            target.pid, target.uid, target.processName,
                            Process.myPid(), Process.myUid(), packageName
                        )) {
                        showFailure("无法确认待切换的应用进程，请返回测试应用重试")
                        return@postDelayed
                    }
                    verified = true
                }
                if (target != null) {
                    if (!isLocalRestartTarget(target.pid, target.uid, target.processName,
                            Process.myPid(), Process.myUid(), packageName)) {
                        showFailure("应用进程已变化，请返回测试应用重试")
                        return@postDelayed
                    }
                    Process.killProcess(oldPid)
                }
                deadline = SystemClock.elapsedRealtime() + 3000
                awaitOldProcess()
            } catch (_: Exception) {
                showFailure("切换服务失败，请重试")
            }
        }, 150)
    }

    private fun awaitOldProcess() {
        if (!hasWindowFocus()) {
            running = false
            return
        }
        try {
            if (processes.any { it.pid == oldPid }) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    showFailure("等待旧连接关闭超时，请重试")
                } else {
                    handler.postDelayed({ awaitOldProcess() }, 50)
                }
                return
            }
            switchScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        switchLocalEnvironment(
                            clearSession = { check(ChildBindingStore.clearLocalTaskSession(this@LocalRestartActivity)) },
                            clearBinding = { ChildBindingStore.clearTaskBinding(this@LocalRestartActivity) },
                            clearCaches = {
                                TaskReminderScheduler.cancelLocalEnvironment(this@LocalRestartActivity)
                                listOf("task_widget_cache", "home_pending_task_order").map { name ->
                                    getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit()
                                }.all { it }
                            },
                            saveAddress = { SupabaseEnvironment.saveLocalUrl(targetUrl) }
                        )
                    }
                    if (!hasWindowFocus()) {
                        running = false
                        return@launch
                    }
                    TaskWidgetProvider.showLocalUnbound(this@LocalRestartActivity)
                    startActivity(Intent(this@LocalRestartActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    })
                    handedOff = true
                    finishAndRemoveTask()
                } catch (_: Exception) {
                    showFailure("清理旧连接或保存新地址失败，请重试")
                }
            }
        } catch (_: Exception) {
            showFailure("重新打开应用失败，请重试")
        }
    }

    private fun showFailure(text: String) {
        running = false
        failed = true
        message.text = text
        retry.visibility = android.view.View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("verified", verified)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        switchScope.cancel()
        super.onDestroy()
        if (handedOff) Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_TARGET_URL = "target_local_url"
        const val EXTRA_OLD_PID = "old_main_pid"
    }
}
