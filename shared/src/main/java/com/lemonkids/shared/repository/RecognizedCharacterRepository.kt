package com.lemonkids.shared.repository

import com.lemonkids.shared.model.RecognizedCharacter

interface RecognizedCharacterRepository {
    /**
     * 分页读取指定孩子的已认识汉字，按收录时间倒序排列。
     * 首页传入默认值读取最近 24 个；“已认识的字”页会继续读取后续页。
     */
    suspend fun getRecognizedCharacters(
        childId: String,
        offset: Long = 0,
        limit: Long = 24,
        /** 仅返回此时间点之前收录的记录；为空时不限制收录日期。 */
        recognizedBefore: String? = null
    ): Result<List<RecognizedCharacter>>
}
