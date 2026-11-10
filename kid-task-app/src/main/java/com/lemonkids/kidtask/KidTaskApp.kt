package com.lemonkids.kidtask

import android.app.Application
import com.lemonkids.shared.di.SupabaseEnvironment
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KidTaskApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SupabaseEnvironment.initialize(this)
    }
}
