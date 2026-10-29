package com.lemonkids.parent

import android.app.Application
import com.lemonkids.shared.di.SupabaseEnvironment
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LemonParentsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SupabaseEnvironment.initialize(this)
    }
}
