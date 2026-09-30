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
        versionCode = 48
        versionName = "0.12.7"
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

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    testImplementation("org.json:json:20240303")
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
