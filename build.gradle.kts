// Kotlin 2.4 metadata requires R8 9.1.29 or newer:
// https://developer.android.com/build/kotlin-support
buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath("com.android.tools:r8:9.1.43") }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
