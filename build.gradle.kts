// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Shared values consumed by the vendored BlackBox library's Groovy build script.
extra["compileSdkVersion"] = 37
extra["minSdk"] = 24
