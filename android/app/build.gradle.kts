plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // NOTE: com.google.gms.google-services is intentionally NOT applied here.
    // See docs/FIREBASE_SETUP.md — applying it without a real google-services.json
    // would break the build. The owner adds it together with the real config file.
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
        versionCode = 1
        versionName = "0.1.0-night001"
    }

    buildTypes {
        release {
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

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
