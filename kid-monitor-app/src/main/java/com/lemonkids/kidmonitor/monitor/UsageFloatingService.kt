package com.lemonkids.kidmonitor.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.lemonkids.kidmonitor.MainActivity
import com.lemonkids.kidmonitor.R
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.util.Constants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class UsageFloatingService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        val appUsageRepository: AppUsageRepository
        val authRepository: AuthRepository
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var timeText: TextView
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showFloatingWindow()
        startPeriodicUpdate()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingWindow()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingWindow() {
        if (floatingView != null) return

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_usage, null)
        timeText = floatingView!!.findViewById(R.id.tv_usage_time)

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

        windowManager.addView(floatingView, params)
    }

    private fun removeFloatingWindow() {
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
    }

    private fun startPeriodicUpdate() {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, ServiceEntryPoint::class.java
        )
        val repo = entryPoint.appUsageRepository
        val auth = entryPoint.authRepository

        updateJob = scope.launch {
            while (true) {
                updateUsageDisplay(repo, auth)
                delay(30_000L)
            }
        }
    }

    private suspend fun updateUsageDisplay(repo: AppUsageRepository, auth: AuthRepository) {
        val userId = auth.currentUserId ?: return
        val today = LocalDate.now().toString()
        val records = repo.getTodayUsage(userId, today)
        val totalSeconds = records.sumOf { it.durationSeconds }
        val minutes = totalSeconds / 60

        withContext(Dispatchers.Main) {
            timeText.text = "${minutes}分钟"
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("使用监控")
            .setContentText("正在记录设备使用情况")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "使用监控服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "usage_monitor_channel"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, UsageFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageFloatingService::class.java))
        }
    }
}
