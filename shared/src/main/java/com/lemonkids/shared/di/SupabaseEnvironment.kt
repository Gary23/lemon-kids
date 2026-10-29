package com.lemonkids.shared.di

import android.content.Context
import com.lemonkids.shared.R

/** One selected Android resource set for the SDK and the binding-code HTTP calls. */
object SupabaseEnvironment {
    private const val LOCAL_PREFS = "lemonkids_local_supabase"
    private const val LOCAL_URL = "api_url"
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val url: String get() = if (isLocal) {
        appContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
            .getString(LOCAL_URL, null) ?: appContext.getString(R.string.supabase_api_url)
    } else {
        appContext.getString(R.string.supabase_api_url)
    }

    fun saveLocalUrl(url: String): Boolean {
        check(isLocal) { "Only local builds can change the Supabase URL" }
        return appContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
            .edit().putString(LOCAL_URL, url).commit()
    }
    val anonKey: String get() = appContext.getString(R.string.supabase_anon_key)
    val isLocal: Boolean get() = appContext.resources.getBoolean(R.bool.supabase_is_local)
}
