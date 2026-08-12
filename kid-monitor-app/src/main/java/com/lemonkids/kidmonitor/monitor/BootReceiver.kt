package com.lemonkids.kidmonitor.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lemonkids.shared.model.DeviceStatusEventType

class BootReceiver : BroadcastReceiver() {
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
            }
        }
    }
}
