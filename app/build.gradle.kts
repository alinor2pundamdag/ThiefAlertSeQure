plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionCode = 6
val appVersionName = "1.1.4"

val instrumentationTestBuild = providers.gradleProperty("instrumentationTestBuild").orNull == "true"

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
        minSdk = 23
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
        // Keep CI/device-test APKs side-by-side with the production installation.
        // This prevents debug builds from replacing the production package.
        getByName("debug") {
            applicationIdSuffix = ".v114test"

        }
        getByName("release") {
            // Harden only distributable builds; keep debug APKs readable for device diagnostics.
            // The dedicated instrumentation build is unminified so test bytecode can inspect
            // framework-facing app classes. The distributable release never sets this property.
            isMinifyEnabled = !instrumentationTestBuild
            isShrinkResources = !instrumentationTestBuild
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // A release APK is signed only with the persistent owner-controlled key.
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            } else if (providers.gradleProperty("signReleaseForTests").orNull == "true") {
                // CI-only minified instrumentation uses the local debug key; never publish this APK.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "opus" }
    testOptions {
        testBuildType = "release"
        unitTests.isReturnDefaultValues = true
    }
}


androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(6)
            output.versionName.set("1.1.4-test")
        }
    }
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
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
}

