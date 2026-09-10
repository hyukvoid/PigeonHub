// Root build file. Firebase (google-services plugin) is intentionally NOT applied:
// the project owner will register com.pigeonhub.app in their own Firebase console
// and drop the matching google-services.json in android/app/ (see docs/FIREBASE_SETUP.md).
// The build must stay green without any Firebase configuration.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
