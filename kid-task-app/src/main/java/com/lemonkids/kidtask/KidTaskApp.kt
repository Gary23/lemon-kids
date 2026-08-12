package com.lemonkids.kidtask

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KidTaskApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
