package com.lemonkids.shared.repository

import com.lemonkids.shared.model.ChildLiteracyCharacter

interface ChildLiteracyCharacterRepository {
    /** 查询孩子的全部汉字学习数据，由调用方按当天阅读状态分类。 */
    suspend fun getCharacters(childId: String): Result<List<ChildLiteracyCharacter>>
}
