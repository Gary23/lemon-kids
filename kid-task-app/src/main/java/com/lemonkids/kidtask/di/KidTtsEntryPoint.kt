package com.lemonkids.kidtask.di

import com.lemonkids.kidtask.util.KidTtsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KidTtsEntryPoint {
    fun ttsManager(): KidTtsManager
}
