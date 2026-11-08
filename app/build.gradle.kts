plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionCode = 5
val appVersionName = "1.1.3"

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        getByName("release") {
            isMinifyEnabled = false
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
    androidResources { noCompress += "opus" }
    testOptions { unitTests.isReturnDefaultValues = true }
}


dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.register("printAndroidVersionConfig") {
    doLast {
        val androidExtension = extensions.getByType<com.android.build.api.dsl.ApplicationExtension>()
        println("VERSION_DIAG defaultConfig.versionCode=${androidExtension.defaultConfig.versionCode}")
        println("VERSION_DIAG defaultConfig.versionName=${androidExtension.defaultConfig.versionName}")
        println("VERSION_DIAG project.android.injected.version.code=${project.findProperty("android.injected.version.code")}")
        println("VERSION_DIAG project.android.injected.version.name=${project.findProperty("android.injected.version.name")}")
        println("VERSION_DIAG system.android.injected.version.code=${System.getProperty("android.injected.version.code")}")
        println("VERSION_DIAG system.android.injected.version.name=${System.getProperty("android.injected.version.name")}")
    }
}
