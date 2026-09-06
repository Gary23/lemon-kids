package com.lemonkids.familyvideo.di

import com.lemonkids.familyvideo.data.FamilyVideoRepository
import com.lemonkids.familyvideo.data.CloudDriveProvider
import com.lemonkids.familyvideo.data.SupabaseFamilyVideoRepository
import com.lemonkids.familyvideo.data.SupabaseEdgeCloudDriveProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FamilyVideoModule {
    @Binds @Singleton abstract fun bindRepository(impl: SupabaseFamilyVideoRepository): FamilyVideoRepository
    @Binds @Singleton abstract fun bindCloudDriveProvider(impl: SupabaseEdgeCloudDriveProvider): CloudDriveProvider
}
