plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "app.podgon.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.podgon"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.0"
        // Запасные адреса API через запятую (ADR-010, R2): -Ppodgon.apiFallbackUrls=https://a,https://b
        buildConfigField(
            "String",
            "API_FALLBACK_URLS",
            "\"${providers.gradleProperty("podgon.apiFallbackUrls").getOrElse("")}\"",
        )
        // Куда вести с экрана обязательного обновления (R1)
        buildConfigField(
            "String",
            "UPDATE_URL",
            "\"${providers.gradleProperty(
                "podgon.updateUrl",
            ).getOrElse("https://play.google.com/store/apps/details?id=app.podgon")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Эмулятор обращается к серверу на хосте по 10.0.2.2 (cleartext разрешён только в debug, см. src/debug)
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${providers.gradleProperty("podgon.debugApiBaseUrl").getOrElse("http://10.0.2.2:8080")}\"",
            )
        }
        release {
            // Адрес продакшена задаётся при сборке: -Ppodgon.apiBaseUrl=https://...; R8 включается на этапе 9
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${providers.gradleProperty("podgon.apiBaseUrl").getOrElse("https://api.podgon.invalid")}\"",
            )
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":mobile:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
}
