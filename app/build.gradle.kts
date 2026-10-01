plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.khaledbahaaeldin.emberbyte"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.khaledbahaaeldin.emberbyte"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":ui:design"))
    implementation(project(":core:engine"))
    implementation(project(":core:data"))
    implementation(project(":feature:lens"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.haze)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
