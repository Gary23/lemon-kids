package com.lemonkids.shared.di

import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.CategoryRepository
import com.lemonkids.shared.repository.DeviceStatusRepository
import com.lemonkids.shared.repository.FamilyRepository
import com.lemonkids.shared.repository.KnownCharacterRepository
import com.lemonkids.shared.repository.ChildLiteracyCharacterRepository
import com.lemonkids.shared.repository.RecognizedCharacterRepository
import com.lemonkids.shared.repository.RewardRepository
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.shared.repository.TaskTemplateRepository
import com.lemonkids.shared.repository.impl.SupabaseAppUsageRepository
import com.lemonkids.shared.repository.impl.SupabaseAuthRepository
import com.lemonkids.shared.repository.impl.SupabaseCategoryRepository
import com.lemonkids.shared.repository.impl.SupabaseDeviceStatusRepository
import com.lemonkids.shared.repository.impl.SupabaseFamilyRepository
import com.lemonkids.shared.repository.impl.SupabaseKnownCharacterRepository
import com.lemonkids.shared.repository.impl.SupabaseChildLiteracyCharacterRepository
import com.lemonkids.shared.repository.impl.SupabaseRecognizedCharacterRepository
import com.lemonkids.shared.repository.impl.SupabaseRewardRepository
import com.lemonkids.shared.repository.impl.SupabaseTaskRepository
import com.lemonkids.shared.repository.impl.SupabaseTaskTemplateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SharedRepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindTaskRepository(impl: SupabaseTaskRepository): TaskRepository

    @Binds
    abstract fun bindTaskTemplateRepository(impl: SupabaseTaskTemplateRepository): TaskTemplateRepository

    @Binds
    abstract fun bindRewardRepository(impl: SupabaseRewardRepository): RewardRepository

    @Binds
    abstract fun bindFamilyRepository(impl: SupabaseFamilyRepository): FamilyRepository

    @Binds
    abstract fun bindKnownCharacterRepository(impl: SupabaseKnownCharacterRepository): KnownCharacterRepository

    @Binds
    abstract fun bindChildLiteracyCharacterRepository(impl: SupabaseChildLiteracyCharacterRepository): ChildLiteracyCharacterRepository

    @Binds
    abstract fun bindRecognizedCharacterRepository(impl: SupabaseRecognizedCharacterRepository): RecognizedCharacterRepository

    @Binds
    abstract fun bindAppUsageRepository(impl: SupabaseAppUsageRepository): AppUsageRepository

    @Binds
    abstract fun bindCategoryRepository(impl: SupabaseCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindDeviceStatusRepository(impl: SupabaseDeviceStatusRepository): DeviceStatusRepository
}
