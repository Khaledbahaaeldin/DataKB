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
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }

    sourceSets.getByName("main") {
        res.directories.add("${project.layout.buildDirectory.get().asFile}/generated/res/licenses")
    }
}

val generateLicensesRaw = tasks.register<Copy>("generateLicensesRaw") {
    from(rootProject.file("NOTICE")) {
        rename { "notice.txt" }
    }
    from(rootProject.file("licenses/roboto-flex-OFL.txt")) {
        rename { "roboto_flex_ofl.txt" }
    }
    into(layout.buildDirectory.dir("generated/res/licenses/raw"))
}

tasks.named("preBuild") {
    dependsOn(generateLicensesRaw)
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    val testTask = this as? Test
    inputs.dir(rootProject.file("fastlane")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("NOTICE")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("docs/release/dependency-licences.txt")).withPathSensitivity(PathSensitivity.RELATIVE)
    mustRunAfter(tasks.matching { it.name == "packageRelease" })

    val isVerifyReleaseApk = gradle.startParameter.taskNames.any { it.contains("verifyReleaseApk") }
    if (isVerifyReleaseApk) {
        testTask?.systemProperty("emberbyte.verifyReleaseApk", "true")
    }
}

val verifyReleaseApk = tasks.register("verifyReleaseApk") {
    description = "Verifies the unsigned release APK matches contract requirements"
    group = "verification"
    dependsOn("assembleRelease")
    dependsOn(tasks.matching { it.name == "testDebugUnitTest" })
    inputs.file(layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk"))
    doLast {
        val apkFile = layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk").get().asFile
        if (!apkFile.exists() || apkFile.length() == 0L) {
            throw GradleException("Release APK does not exist or is empty at ${apkFile.absolutePath}")
        }
        logger.lifecycle("Release APK verified: ${apkFile.name} (${apkFile.length()} bytes)")
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":ui:design"))
    implementation(project(":core:engine"))
    implementation(project(":core:data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
