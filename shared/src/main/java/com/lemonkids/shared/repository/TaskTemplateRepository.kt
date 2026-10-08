package com.lemonkids.shared.repository

import com.lemonkids.shared.model.TaskTemplate
import kotlinx.coroutines.flow.Flow

interface TaskTemplateRepository {
    fun observeTemplates(familyId: String): Flow<List<TaskTemplate>>
    suspend fun createTemplate(template: TaskTemplate): Result<String>
    suspend fun updateTemplate(template: TaskTemplate): Result<Unit>
    suspend fun deleteTemplate(templateId: String): Result<Unit>
    suspend fun previewGrowthDomainBackfill(templateId: String): Result<GrowthDomainBackfillPreview>
    suspend fun backfillGrowthDomain(templateId: String, growthDomain: String): Result<Int>
}

data class GrowthDomainBackfillPreview(val total: Int, val completed: Int)
