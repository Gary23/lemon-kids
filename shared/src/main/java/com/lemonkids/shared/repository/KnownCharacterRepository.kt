package com.lemonkids.shared.repository

import com.lemonkids.shared.model.KnownCharacter

interface KnownCharacterRepository {
    /** 分页查询指定孩子字库中已认识的汉字，按收录时间排序。 */
    suspend fun getKnownCharacters(
        userId: String,
        offset: Long,
        limit: Long
    ): Result<List<KnownCharacter>>
}
