package com.lemonkids.shared.repository.impl

import android.util.Log
import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskTemplate
import com.lemonkids.shared.repository.CategoryRepository
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
        postgrest.from("categories").update(mapOf("name" to category.name, "color" to category.color)) {
            filter { eq("id", category.id) }
        }
    }

    override suspend fun deleteCategory(categoryId: String): Result<Unit> = runCatching {
        postgrest.from("categories").delete { filter { eq("id", categoryId) } }
    }

    override suspend fun getTaskCountByCategory(familyId: String, categoryName: String): Result<Pair<Int, Int>> = runCatching {
        val allTasks = postgrest.from("tasks").select {
            filter { eq("family_id", familyId); eq("category", categoryName) }
        }.decodeList<Task>()
        val pending = allTasks.count { it.status.name == "PENDING" || it.status.name == "REJECTED" }
        val done = allTasks.count { it.status.name == "DONE" || it.status.name == "VERIFIED" || it.status.name == "EXPIRED" }
        Pair(pending, done)
    }

    override suspend fun getTaskTemplateCountByCategory(familyId: String, categoryName: String): Result<Int> = runCatching {
        postgrest.from("task_templates").select {
            filter { eq("family_id", familyId); eq("category", categoryName) }
        }.decodeList<TaskTemplate>().size
    }
}
