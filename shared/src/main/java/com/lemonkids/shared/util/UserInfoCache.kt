package com.lemonkids.shared.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户信息本地缓存 —— 头像URL和名字，下次打开秒显示
 */
@Singleton
class UserInfoCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("lemonkids_userinfo", Context.MODE_PRIVATE)

    fun getCached(userId: String): CachedUserInfo? {
        val avatar = prefs.getString("avatar_$userId", null) ?: return null
        val name = prefs.getString("name_$userId", null) ?: return null
        return CachedUserInfo(name, avatar)
    }

    fun putCached(userId: String, name: String?, avatarUrl: String?) {
        prefs.edit().apply {
            name?.let { putString("name_$userId", it) }
            avatarUrl?.let { putString("avatar_$userId", it) }
        }.apply()
    }

    /** 孩子端用：通过 familyId 缓存唯一的 parent 信息 */
    fun putCachedParent(familyId: String, name: String?, avatarUrl: String?) {
        putCached("parent_$familyId", name, avatarUrl)
    }

    fun getCachedParent(familyId: String): CachedUserInfo? {
        return getCached("parent_$familyId")
    }

    data class CachedUserInfo(val name: String, val avatarUrl: String)
}
