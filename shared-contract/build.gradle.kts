plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(25)

    // Сервер использует jvm-таргет, Android-клиент — androidLibrary; iOS добавляется на этапе 11.
    jvm()
    android {
        namespace = "app.podgon.contract"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
