import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Backend keys live in local.properties (never committed). Without them the app runs with a local
// development account so every screen can still be tried.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String) = "\"${localProps.getProperty(name, "")}\""

android {
    namespace = "agape.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "agape.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "3.0.0"

        buildConfigField("String", "SUPABASE_URL", localProp("supabase.url"))
        buildConfigField("String", "SUPABASE_ANON_KEY", localProp("supabase.anonKey"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", localProp("google.webClientId"))
        buildConfigField("String", "DONATION_URL", "\"https://example.org/agape/donate\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"support@example.org\"")
    }

    androidResources {
        localeFilters += listOf("en", "nl")
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/assets"))
        }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val ensureBibleDb by tasks.registering(Exec::class) {
    description = "Ensures app/src/main/assets/bible/bible.db is populated with verses, guides, and cross-references"
    workingDir = rootDir
    commandLine("python3", "${rootDir}/gradle/ensure_bible_db.py")
}

tasks.named("preBuild") {
    dependsOn(ensureBibleDb)
}

