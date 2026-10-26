// Корневой проект только объявляет плагины (версии из каталога); логика — в подпроектах.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
