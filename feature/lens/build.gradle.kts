plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.khaledbahaaeldin.emberbyte.lens"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:data"))
}
