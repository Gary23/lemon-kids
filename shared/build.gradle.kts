import java.util.Properties

val productionSupabaseUrl = "https://ebiikfxehhcrtrkioxqa.supabase.co"
val productionSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWlrZnhlaGhjcnRya2lveHFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAzMjQxMTMsImV4cCI6MjA5NTkwMDExM30.PbYlbBiUN7CI4EFedzzEWANrcLI1gElvAjBTlGKi7Go"
val localSupabaseRequested = providers.gradleProperty("localSupabase").orNull == "true"
val localConfig = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun localValue(propertyName: String, environmentName: String): String =
    (providers.gradleProperty(propertyName).orNull
        ?: localConfig.getProperty(propertyName)
        ?: System.getenv(environmentName))?.trim().orEmpty()
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.lemonkids.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("debug") {
            val localUrl = if (localSupabaseRequested) localValue("localSupabaseUrl", "LEMON_KIDS_LOCAL_SUPABASE_URL") else ""
            val localKey = if (localSupabaseRequested) localValue("localSupabaseAnonKey", "LEMON_KIDS_LOCAL_SUPABASE_ANON_KEY") else ""
            if (localSupabaseRequested) {
                require(localUrl.startsWith("http://") || localUrl.startsWith("https://")) {
                    "localSupabase=true 时必须设置 localSupabaseUrl 或 LEMON_KIDS_LOCAL_SUPABASE_URL"
                }
                require(localKey.isNotBlank()) {
                    "localSupabase=true 时必须设置 localSupabaseAnonKey 或 LEMON_KIDS_LOCAL_SUPABASE_ANON_KEY"
                }
            }
            resValue("string", "supabase_api_url", if (localSupabaseRequested) localUrl.trimEnd('/') else productionSupabaseUrl)
            resValue("string", "supabase_anon_key", if (localSupabaseRequested) localKey else productionSupabaseAnonKey)
            resValue("bool", "supabase_is_local", localSupabaseRequested.toString())
        }
        getByName("release") {
            resValue("string", "supabase_api_url", productionSupabaseUrl)
            resValue("string", "supabase_anon_key", productionSupabaseAnonKey)
            resValue("bool", "supabase_is_local", "false")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    // Compose (BindingCodeScreen 需要)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Supabase
    implementation(platform(libs.supabase.kt.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)

    // Ktor engine for Supabase HTTP
    implementation(libs.ktor.client.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager for background sync
    implementation(libs.androidx.work.runtime.ktx)

    // Coil image loader
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
