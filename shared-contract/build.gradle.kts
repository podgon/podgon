import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Android-таргет подключается отдельным скриптом, чтобы сервер собирался без Android SDK
if (!providers.gradleProperty("serverOnly").isPresent) {
    pluginManager.apply("com.android.kotlin.multiplatform.library")
    val android = (kotlin as ExtensionAware).extensions.getByName("android")
    (android as KotlinMultiplatformAndroidLibraryTarget).apply {
        namespace = "app.podgon.contract"
        compileSdk = 37
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(25)

    // Сервер использует jvm-таргет, Android-клиент — androidLibrary; iOS добавляется на этапе 11.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
