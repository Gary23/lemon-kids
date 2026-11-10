package com.lemonkids.shared.auth

import android.content.Context
import com.lemonkids.shared.di.SupabaseEnvironment
import io.github.jan.supabase.gotrue.SettingsSessionManager

/** Binding credentials belong to this application's private data and selected environment. */
object ChildBindingStore {
    const val TASK_PREFERENCES = "task_binding"
    const val CODE_KEY = "binding_code"

    fun isTaskApplication(packageName: String): Boolean =
        packageName == "com.lemonkids.kidtask" || packageName == "com.lemonkids.kidtask.local"

    fun clearTaskBinding(context: Context): Boolean = context
        .getSharedPreferences(TASK_PREFERENCES, Context.MODE_PRIVATE)
        .edit().remove(CODE_KEY).commit()

    fun clearLocalTaskSession(context: Context): Boolean {
        check(context.packageName == "com.lemonkids.kidtask.local" && SupabaseEnvironment.isLocal)
        // SupabaseModule uses SettingsSessionManager() with multiplatform-settings 1.1.1:
        // its default backing file is <package>_preferences and its key is SETTINGS_KEY.
        // The library's no-arg Context provider does not run in the restart process.
        // Clear synchronously through Android Context after the old process has exited.
        return context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit().remove(SettingsSessionManager.SETTINGS_KEY).commit()
    }
}
