package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.repository.ChildLiteracyCharacterRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseChildLiteracyCharacterRepository @Inject constructor(
    private val supabase: SupabaseClient
) : ChildLiteracyCharacterRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun getCharacters(childId: String): Result<List<ChildLiteracyCharacter>> = runCatching {
        postgrest.from("child_literacy_characters").select {
            filter { eq("child_id", childId) }
            order("sort_order", Order.ASCENDING)
            order("character", Order.ASCENDING)
        }.decodeList<ChildLiteracyCharacter>()
    }
}
