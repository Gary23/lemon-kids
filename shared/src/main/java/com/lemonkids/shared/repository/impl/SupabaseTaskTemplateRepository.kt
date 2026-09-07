package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.TaskTemplate
import com.lemonkids.shared.repository.TaskTemplateRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class TaskTemplateUpdate(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("reward_points") val rewardPoints: Int,
    @SerialName("penalty_points") val penaltyPoints: Int
)

@Serializable
private data class TaskTemplateCreate(
    @SerialName("family_id") val familyId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("reward_points") val rewardPoints: Int,
    @SerialName("penalty_points") val penaltyPoints: Int
)

@Singleton
class SupabaseTaskTemplateRepository @Inject constructor(
    private val supabase: SupabaseClient
) : TaskTemplateRepository {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val templateRefreshEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override fun observeTemplates(familyId: String): Flow<List<TaskTemplate>> = callbackFlow {
        val fetchMutex = Mutex()
        suspend fun fetch() {
            fetchMutex.withLock { try {
                val templates = postgrest.from("task_templates").select {
                    filter { eq("family_id", familyId) }
                    order("created_at", Order.ASCENDING)
                }.decodeList<TaskTemplate>()
                trySend(templates)
            } catch (_: Exception) {} }
        }
        fetch()
        launch { templateRefreshEvents.collect { fetch() } }
        // 模板仅由家长维护，写入后页面会直接更新；低频校准即可。
        while (true) { delay(300_000); fetch() }
        awaitClose()
    }

    override suspend fun createTemplate(template: TaskTemplate): Result<String> = runCatching {
        // 不提交 UI 的空 id/created_at，让数据库生成 UUID 与创建时间。
        postgrest.from("task_templates").insert(
            TaskTemplateCreate(
                familyId = template.familyId,
                title = template.title,
                description = template.description,
                rewardPoints = template.rewardPoints,
                penaltyPoints = template.penaltyPoints
            )
        ) { select() }.decodeSingle<TaskTemplate>().id
    }.onSuccess { templateRefreshEvents.tryEmit(Unit) }

    override suspend fun updateTemplate(template: TaskTemplate): Result<Unit> = runCatching {
        // 使用有明确序列化器的载荷；Map<String, Any> 在 Supabase 序列化失败时会导致请求未发出。
        // select() 同时保证 RLS 拦截或未命中记录时不会被误判为保存成功。
        postgrest.from("task_templates").update(
            TaskTemplateUpdate(
                title = template.title,
                description = template.description,
                rewardPoints = template.rewardPoints,
                penaltyPoints = template.penaltyPoints
            )
        ) {
            filter { eq("id", template.id) }
            select()
        }.decodeSingle<TaskTemplate>()
        Unit
    }.onSuccess { templateRefreshEvents.tryEmit(Unit) }

    override suspend fun deleteTemplate(templateId: String): Result<Unit> = runCatching {
        postgrest.from("task_templates").delete { filter { eq("id", templateId) } }
        Unit
    }.onSuccess { templateRefreshEvents.tryEmit(Unit) }
}
