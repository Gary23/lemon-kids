package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.RecognizedCharacter
import com.lemonkids.shared.repository.RecognizedCharacterRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseRecognizedCharacterRepository @Inject constructor(
    private val supabase: SupabaseClient
) : RecognizedCharacterRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun getRecognizedCharacters(
        childId: String,
        offset: Long,
        limit: Long
    ): Result<List<RecognizedCharacter>> = runCatching {
        postgrest.from("recognized_characters").select {
            filter { eq("child_id", childId) }
            order("recognized_at", Order.DESCENDING)
            order("character", Order.ASCENDING)
            range(offset, offset + limit - 1)
        }.decodeList<RecognizedCharacter>()
    }
}
