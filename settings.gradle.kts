rootProject.name = "podgon"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":server", ":shared-contract")
// Мобильные модули требуют Android SDK; образ backend собирается без них (-PserverOnly)
if (!providers.gradleProperty("serverOnly").isPresent) {
    include(":mobile:shared", ":mobile:androidApp")
}
