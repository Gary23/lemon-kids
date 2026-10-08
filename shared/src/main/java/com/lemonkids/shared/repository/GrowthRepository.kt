package com.lemonkids.shared.repository

import com.lemonkids.shared.model.GrowthSnapshot

interface GrowthRepository {
    suspend fun getOwnSnapshot(): Result<GrowthSnapshot>
}
