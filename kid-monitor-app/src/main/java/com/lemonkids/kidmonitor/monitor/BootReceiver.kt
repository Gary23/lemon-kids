package com.lemonkids.kidmonitor.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lemonkids.shared.model.DeviceStatusEventType
import com.lemonkids.kidmonitor.alarm.AlarmScheduler
import com.lemonkids.kidmonitor.alarm.AlarmSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT -> {
                KeepAliveWorker.schedule(context)
                DeviceStatusWorker.schedule(context)
                LimitEnforcementService.start(context)
                val eventType = when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> DeviceStatusEventType.USER_PRESENT
                    else -> DeviceStatusEventType.BOOT
                }
                DeviceStatusWorker.reportNow(context, eventType)
                // 解锁后或正常开机后立即拉取家长在离线期间的改动；不会参与准点触发。
                AlarmSyncWorker.schedule(context)
                AlarmSyncWorker.syncNow(context)
                // Android 重启会清空第三方 App 已登记的 AlarmManager 项；从设备保护存储立即恢复。
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    runCatching { alarmScheduler.restoreAfterBoot() }
                    pendingResult.finish()
                }
            }
        }
    }
}
