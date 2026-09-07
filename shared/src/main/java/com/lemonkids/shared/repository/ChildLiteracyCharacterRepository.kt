package com.lemonkids.shared.repository

import com.lemonkids.shared.model.ChildLiteracyCharacter

interface ChildLiteracyCharacterRepository {
    /** 查询孩子的全部汉字学习数据，由调用方按当天阅读状态分类。 */
    suspend fun getCharacters(childId: String): Result<List<ChildLiteracyCharacter>>

    /**
     * 取得北京时间当天固定的待认识任务；当天首次调用时由服务端原子地选出最多 6 个。
     *
     * [preferredCharacterIds] 仅用于把已升级设备本地保存的当天快照迁入服务端，服务端会
     * 校验这些任务确实归属当前孩子。服务端已有快照时该参数不会改变既有结果。
     */
    suspend fun getOrCreateTodayCharacters(
        childId: String,
        preferredCharacterIds: List<String> = emptyList()
    ): Result<List<ChildLiteracyCharacter>>
}
