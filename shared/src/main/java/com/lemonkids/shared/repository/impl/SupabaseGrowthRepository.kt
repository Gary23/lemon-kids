package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.GrowthSnapshot
import com.lemonkids.shared.repository.GrowthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseGrowthRepository @Inject constructor(private val supabase: SupabaseClient) : GrowthRepository {
    override suspend fun getOwnSnapshot(): Result<GrowthSnapshot> = runCatching {
        supabase.pluginManager.getPlugin(Postgrest).rpc("growth_snapshot").decodeAs<GrowthSnapshot>()
    }
}
