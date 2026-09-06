package com.lemonkids.shared.repository.impl

import android.util.Log
import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.CategoryTaskTemplate
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.repository.CategoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseCategoryRepository @Inject constructor(
    private val supabase: SupabaseClient
) : CategoryRepository {

    companion object {
        private const val TAG = "CategoryRepo"
    }

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override fun observeCategories(familyId: String): Flow<List<Category>> = callbackFlow {
        suspend fun fetch() {
            try {
                val list = postgrest.from("categories").select {
                    filter { eq("family_id", familyId) }
                    order("created_at", Order.ASCENDING)
                }.decodeList<Category>()
                trySend(list)
            } catch (e: Exception) {
                Log.e(TAG, "分类查询失败 familyId=$familyId", e)
            }
        }
        fetch()
        while (true) { delay(10000); fetch() }
    }

    override suspend fun createCategory(category: Category): Result<String> = runCatching {
        // id 与 created_at 由数据库生成。不能直接序列化 Category：界面的乐观更新会
        // 使用临时 id，而 categories.id 是 UUID，传给数据库会导致插入被拒绝。
        postgrest.from("categories").insert(
            mapOf(
                "family_id" to category.familyId,
                "name" to category.name,
                "color" to category.color
            )
        ) { select() }.decodeSingle<Category>().id
    }.onFailure { e ->
        Log.e(TAG, "新增分类失败 familyId=${category.familyId}, name=${category.name}", e)
    }

    override suspend fun updateCategory(category: Category): Result<Unit> = runCatching {
        // 改名同时由服务端更新未来待完成任务的展示分组，历史任务保持原快照。
        postgrest.rpc(
            function = "rename_category",
            parameters = mapOf(
                "p_category_id" to category.id,
                "p_name" to category.name,
                "p_color" to category.color
            )
        )
    }

    override suspend fun deleteCategory(categoryId: String): Result<Unit> = runCatching {
        postgrest.from("categories").delete { filter { eq("id", categoryId) } }
    }

    override fun observeCategoryTaskTemplates(familyId: String): Flow<List<CategoryTaskTemplate>> = callbackFlow {
        suspend fun fetch() {
            try {
                val list = postgrest.from("category_task_templates").select {
                    filter { eq("family_id", familyId) }
                    order("sort_order", Order.ASCENDING)
                }.decodeList<CategoryTaskTemplate>()
                trySend(list)
            } catch (e: Exception) {
                Log.e(TAG, "分类任务包查询失败 familyId=$familyId", e)
            }
        }
        fetch()
        while (true) { delay(10_000); fetch() }
        awaitClose()
    }

    override suspend fun replaceCategoryTaskTemplates(categoryId: String, templateIds: List<String>): Result<Unit> =
        runCatching {
            postgrest.rpc(
                function = "set_category_task_templates",
                // 不能使用混合 String/List 的 Map：Kotlin 会将其推导成 Map<String, Any>，
                // Supabase 序列化器无法为 Any 生成序列化器，请求会在设备端直接失败。
                parameters = JsonObject(
                    mapOf(
                        "p_category_id" to JsonPrimitive(categoryId),
                        "p_template_ids" to JsonArray(templateIds.distinct().map(::JsonPrimitive))
                    )
                )
            )
            Unit
        }.onSuccess {
            Log.i(TAG, "分类任务保存成功 categoryId=$categoryId taskCount=${templateIds.distinct().size}")
        }.onFailure { error ->
            Log.e(TAG, "分类任务保存失败 categoryId=$categoryId taskCount=${templateIds.distinct().size}", error)
        }

    override suspend fun getPendingTaskCountByCategory(categoryId: String): Result<Int> = runCatching {
        val tasks = postgrest.from("tasks").select {
            filter { eq("source_category_id", categoryId) }
        }.decodeList<Task>()
        tasks.count {
            it.status.name == "PENDING" && it.deletedAt == null &&
                runCatching { LocalDate.parse(it.dueDate) >= LocalDate.now() }.getOrDefault(false)
        }
    }
}
