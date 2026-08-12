package com.lemonkids.shared.repository

import com.lemonkids.shared.model.Family

interface FamilyRepository {
    suspend fun createFamily(name: String): Result<Family>
    suspend fun joinByInviteCode(inviteCode: String, childId: String): Result<Family>
    suspend fun getFamily(familyId: String): Result<Family>
}
