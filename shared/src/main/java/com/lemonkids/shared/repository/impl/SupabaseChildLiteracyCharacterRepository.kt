package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.repository.ChildLiteracyCharacterRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class TodayLiteracyTasksParameters(
    @SerialName("p_child_id") val childId: String,
    @SerialName("p_preferred_literacy_character_ids") val preferredCharacterIds: List<String>
)

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

    override suspend fun getOrCreateTodayCharacters(
        childId: String,
        preferredCharacterIds: List<String>
    ): Result<List<ChildLiteracyCharacter>> = runCatching {
        postgrest.rpc(
            function = "get_or_create_today_literacy_tasks",
            parameters = TodayLiteracyTasksParameters(
                childId = childId,
                preferredCharacterIds = preferredCharacterIds
            )
        ).decodeList<ChildLiteracyCharacter>()
    }
}
