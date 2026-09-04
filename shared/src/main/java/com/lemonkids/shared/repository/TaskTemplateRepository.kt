package com.lemonkids.shared.repository

import com.lemonkids.shared.model.TaskTemplate
import kotlinx.coroutines.flow.Flow

interface TaskTemplateRepository {
    fun observeTemplates(familyId: String): Flow<List<TaskTemplate>>
    suspend fun createTemplate(template: TaskTemplate): Result<String>
    suspend fun updateTemplate(template: TaskTemplate): Result<Unit>
    suspend fun deleteTemplate(templateId: String): Result<Unit>
}
