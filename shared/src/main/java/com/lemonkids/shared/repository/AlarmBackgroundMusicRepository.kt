package com.lemonkids.shared.repository

import com.lemonkids.shared.model.AlarmBackgroundMusicAsset
import com.lemonkids.shared.model.FamilyAlarmMusicUpload

/** 曲目目录与短时下载地址的唯一入口，禁止调用方持久化 URL 或对象路径。 */
interface AlarmBackgroundMusicRepository {
    suspend fun getPublishedMusic(): Result<List<AlarmBackgroundMusicAsset>>
    suspend fun createDownloadUrl(music: AlarmBackgroundMusicAsset): Result<String>
    suspend fun uploadFamilyMusic(familyId: String, upload: FamilyAlarmMusicUpload): Result<AlarmBackgroundMusicAsset>
}
