plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ubique.uniffi)
}

cargo {
    packageDirectory = layout.projectDirectory.dir("rust")
}

uniffi {
    generateFromLibrary()
}

kotlin {
    jvmToolchain(17)

    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.uniffi.runtime)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
        }
    }
}

android {
    namespace = "com.lancedb.kmp"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    ndkVersion = "26.2.11394342"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
