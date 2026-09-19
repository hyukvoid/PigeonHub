plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Reads android/app/google-services.json (dedicated PigeonHub project,
    // package com.pigeonhub.app). The file is gitignored; a missing file fails
    // the build on purpose so config never silently drifts.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.pigeonhub.app"
    compileSdk = 36

    defaultConfig {
        // applicationId is FIXED: com.pigeonhub.app.
        // It must never be changed to match an existing Firebase project.
        applicationId = "com.pigeonhub.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0-beta003a"
    }

    buildTypes {
        release {
            // Local validation builds are signed with the debug key so the
            // release artifact can be installed on test devices. Store/public
            // release signing remains a separate, owner-held step.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    // firebase-messaging compiles and runs inert without a google-services.json
    // (FirebaseApp simply never initializes). It exists so the FCM service
    // boundary in push/PigeonMessagingService.kt is real code, not a stub.
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
