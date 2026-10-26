plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(25)

    // Android-таргет добавляется на этапе 3, iOS — на этапе 11. Модуль не зависит от Spring и Android.
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
