package com.lemonkids.shared.repository

import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.CategoryTaskTemplate
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(familyId: String): Flow<List<Category>>
    suspend fun createCategory(category: Category): Result<String>
    suspend fun updateCategory(category: Category): Result<Unit>
    suspend fun deleteCategory(categoryId: String): Result<Unit>
    fun observeCategoryTaskTemplates(familyId: String): Flow<List<CategoryTaskTemplate>>
    /** 原子替换一个分类任务包的成员和顺序。 */
    suspend fun replaceCategoryTaskTemplates(categoryId: String, templateIds: List<String>): Result<Unit>
    /** 删除前检查仍会受分类改名影响的未来待完成任务。 */
    suspend fun getPendingTaskCountByCategory(categoryId: String): Result<Int>
}
