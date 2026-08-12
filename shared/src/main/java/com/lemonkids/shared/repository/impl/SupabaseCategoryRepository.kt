package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.Task
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

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override fun observeCategories(familyId: String): Flow<List<Category>> = callbackFlow {
        suspend fun fetch() {
            try {
                val list = postgrest.from("categories").select {
                    filter { eq("family_id", familyId) }
                    order("created_at", Order.ASCENDING)
                }.decodeList<Category>()
                trySend(list)
            } catch (_: Exception) {}
        }
        fetch()
        while (true) { delay(10000); fetch() }
    }

    override suspend fun createCategory(category: Category): Result<String> = runCatching {
        postgrest.from("categories").insert(category) { select() }.decodeSingle<Category>().id
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
}
