// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.real3rr0r303yt.emojifontcustomizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.real3rr0r303yt.emojifontcustomizer"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.1"

        // Set `donationUrl` in gradle.properties (or with -PdonationUrl=...) to show the donate button.
        val donationUrl = providers.gradleProperty("donationUrl").getOrElse("")
        buildConfigField("String", "DONATION_URL", "\"$donationUrl\"")
        buildConfigField("String", "SOURCE_URL", "\"https://github.com/Real3rr0r303YT/EmojiFontCustomizer\"")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/** Ships the repository's GPL-3.0 LICENSE inside the APK so the app can show it. */
abstract class CopyLicenseTask : DefaultTask() {
    @get:InputFile
    abstract val licenseFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.apply { mkdirs() }
        licenseFile.get().asFile.copyTo(File(out, "LICENSE"), overwrite = true)
    }
}

val copyLicense = tasks.register<CopyLicenseTask>("copyLicenseToAssets") {
    licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLicense, CopyLicenseTask::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
