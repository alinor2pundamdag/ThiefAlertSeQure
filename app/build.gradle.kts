plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionCode = 7
val appVersionName = "1.1.5_Developer_Alinor"
val appTestVersionName = "${appVersionName.substringBefore("_Developer_Alinor")}-test"

val releaseSigningReady = listOf(
    "ANDROID_KEYSTORE_PATH",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD"
).all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "com.thiefalertsequre.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.thiefalertsequre.app"
        minSdk = 26
        targetSdk = 36
        // Increase this for every distributable update. Never reset it between releases.
        versionCode = appVersionCode
        versionName = appVersionName
    }
    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH")!!)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        // Keep CI/device-test APKs side-by-side with the production installation.
        // This prevents debug builds from replacing the production package.
        getByName("debug") {
            applicationIdSuffix = ".v114test"

        }
        getByName("release") {
            // Harden every distributable release build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // A release APK is signed only with the persistent owner-controlled key.
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += "opus" }
    testOptions {
        testBuildType = "release"
        unitTests.isReturnDefaultValues = true
    }
}


androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(appVersionCode)
            output.versionName.set(appTestVersionName)
        }
    }
}

dependencies {
    // Compose 1.11 stable line is compatible with this project's compileSdk 36 / AGP 8.10.x.
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    // Filament real-time PBR renderer and Android surface helpers (same pinned release).
    implementation("com.google.android.filament:filament-android:1.81.0")
    implementation("com.google.android.filament:filament-utils-android:1.81.0")
    implementation("com.google.android.filament:filamat-android:1.81.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}

