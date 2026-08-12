package com.lemonkids.shared.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://ebiikfxehhcrtrkioxqa.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWlrZnhlaGhjcnRya2lveHFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAzMjQxMTMsImV4cCI6MjA5NTkwMDExM30.PbYlbBiUN7CI4EFedzzEWANrcLI1gElvAjBTlGKi7Go"
        ) {
            install(Postgrest)
            install(Auth) {
                // 默认内存会话会在进程被回收后丢失，孩子端会因此再次要求输入绑定码。
                // 会话保存于各应用私有数据；清除应用数据或主动退出后才会被清除。
                sessionManager = SettingsSessionManager()
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Storage)
            install(Realtime)
        }
    }
}
