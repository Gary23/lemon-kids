package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.Family
import com.lemonkids.shared.repository.FamilyRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFamilyRepository @Inject constructor(
    private val supabase: SupabaseClient
) : FamilyRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun createFamily(name: String): Result<Family> = runCatching {
        val code = (1..6).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
        val family = postgrest.from("families").insert(
            mapOf("name" to name, "invite_code" to code)
        ) {
            select()
        }.decodeSingle<Family>()
        family
    }

    override suspend fun joinByInviteCode(inviteCode: String, childId: String): Result<Family> =
        runCatching {
            val family = postgrest.from("families").select {
                filter { eq("invite_code", inviteCode) }
            }.decodeSingle<Family>()

            postgrest.from("users").update(mapOf("family_id" to family.id)) {
                filter { eq("uid", childId) }
            }
            family
        }

    override suspend fun getFamily(familyId: String): Result<Family> = runCatching {
        postgrest.from("families").select {
            filter { eq("id", familyId) }
        }.decodeSingle<Family>()
    }
}
