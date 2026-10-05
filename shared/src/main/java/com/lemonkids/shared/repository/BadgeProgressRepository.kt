package com.lemonkids.shared.repository

import com.lemonkids.shared.model.BadgeProgress

interface BadgeProgressRepository {
    suspend fun getForChild(childId: String): Result<List<BadgeProgress>>
    suspend fun setAbsolute(childId: String, key: String, segment: String, value: Int, expectedVersion: Int): Result<BadgeProgress>
}
