package com.lemonkids.kidmonitor

import android.app.Application
import com.lemonkids.kidmonitor.monitor.KeepAliveWorker
import com.lemonkids.kidmonitor.monitor.LimitEnforcementService
import com.lemonkids.kidmonitor.monitor.UsageCollectWorker
import com.lemonkids.kidmonitor.monitor.DeviceStatusWorker
import com.lemonkids.shared.model.DeviceStatusEventType
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KidMonitorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        UsageCollectWorker.schedule(this)
        KeepAliveWorker.schedule(this)
        DeviceStatusWorker.schedule(this)
        DeviceStatusWorker.reportNow(this, DeviceStatusEventType.APP_START)
        LimitEnforcementService.start(this)
    }
}
