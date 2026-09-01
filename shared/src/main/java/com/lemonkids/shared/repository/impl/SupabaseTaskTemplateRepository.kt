package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.TaskTemplate
import com.lemonkids.shared.repository.TaskTemplateRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTaskTemplateRepository @Inject constructor(
    private val supabase: SupabaseClient
) : TaskTemplateRepository {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override fun observeTemplates(familyId: String): Flow<List<TaskTemplate>> = callbackFlow {
        suspend fun fetch() {
            try {
                val templates = postgrest.from("task_templates").select {
                    filter { eq("family_id", familyId) }
                    order("created_at", Order.ASCENDING)
                }.decodeList<TaskTemplate>()
                trySend(templates)
            } catch (_: Exception) {}
        }
        fetch()
        while (true) { delay(10_000); fetch() }
        awaitClose()
    }

    override suspend fun createTemplate(template: TaskTemplate): Result<String> = runCatching {
        postgrest.from("task_templates").insert(template) { select() }.decodeSingle<TaskTemplate>().id
    }

    override suspend fun updateTemplate(template: TaskTemplate): Result<Unit> = runCatching {
        postgrest.from("task_templates").update(
            mapOf(
                "title" to template.title,
                "description" to template.description,
                "category" to template.category,
                "reward_points" to template.rewardPoints,
                "penalty_points" to template.penaltyPoints
            )
        ) { filter { eq("id", template.id) } }
    }

    override suspend fun deleteTemplate(templateId: String): Result<Unit> = runCatching {
        postgrest.from("task_templates").delete { filter { eq("id", templateId) } }
    }
}
