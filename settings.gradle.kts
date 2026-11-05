pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application" || requested.id.id == "com.android.library") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Clony"
include(":app")
include(":Bcore")
include(":black-reflection")
include(":compiler")

project(":Bcore").projectDir = file("third_party/BlackBoxReborn/Bcore")
project(":black-reflection").projectDir = file("third_party/BlackBoxReborn/black-reflection")
project(":compiler").projectDir = file("third_party/BlackBoxReborn/compiler")
