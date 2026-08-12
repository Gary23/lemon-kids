package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.KnownCharacter
import com.lemonkids.shared.repository.KnownCharacterRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseKnownCharacterRepository @Inject constructor(
    private val supabase: SupabaseClient
) : KnownCharacterRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun getKnownCharacters(
        userId: String,
        offset: Long,
        limit: Long
    ): Result<List<KnownCharacter>> = runCatching {
        postgrest.from("known_characters").select {
            filter { eq("user_id", userId) }
            order("learned_at", Order.ASCENDING)
            order("character", Order.ASCENDING)
            range(offset, offset + limit - 1)
        }.decodeList<KnownCharacter>()
    }

}
