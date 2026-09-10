// Root build file.
// The google-services plugin is now ACTIVE: the owner registered com.pigeonhub.app
// in the dedicated PigeonHub Firebase project (pigeonhub-b958d) and provided the
// matching android/app/google-services.json (gitignored — never commit it).
// The build fails without that file by design; see docs/FIREBASE_SETUP.md.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
