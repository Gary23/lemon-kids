package com.lemonkids.parent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LemonParentsApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
