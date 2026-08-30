plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.hubhelper"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.hubhelper"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "0.9.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.directories.add("build/generated/referenceAssets")
}

val prepareReferenceAssets by tasks.registering(Sync::class) {
    from(rootProject.file("Hubbell_Killark_CBA_2025-2029.md"))
    from(rootProject.file("Light_Industrial_Attendance_Policy.md"))
    from(rootProject.file("WORK_SCHEDULES.md"))
    into(layout.buildDirectory.dir("generated/referenceAssets"))
}

tasks.named("preBuild").configure { dependsOn(prepareReferenceAssets) }

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
