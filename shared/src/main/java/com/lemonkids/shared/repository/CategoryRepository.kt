package com.lemonkids.shared.repository

import com.lemonkids.shared.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(familyId: String): Flow<List<Category>>
    suspend fun createCategory(category: Category): Result<String>
    suspend fun updateCategory(category: Category): Result<Unit>
    suspend fun deleteCategory(categoryId: String): Result<Unit>
    /** 查询某个分类下的任务数量（按状态），用于删除前校验 */
    suspend fun getTaskCountByCategory(familyId: String, categoryName: String): Result<Pair<Int, Int>> // (未完成数量, 已完成数量)
}
