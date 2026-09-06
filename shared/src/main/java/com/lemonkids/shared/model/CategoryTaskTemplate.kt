package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 分类任务包与任务模板之间的多对多关联。 */
@Serializable
data class CategoryTaskTemplate(
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("template_id") val templateId: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0
)
