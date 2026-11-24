plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val generatedLauncherAssetsDir = layout.buildDirectory.dir("generated/launcher-assets")
val debugSigningKey = file("${System.getProperty("user.home")}/.android/debug.keystore")

android {
    namespace = "com.clony"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.clony"
        minSdk = 24
        // BlackBox relies on reflection access that Android restricts for newer targets.
        // This is suitable for sideloaded research builds, but not current Play publishing.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets.getByName("main").assets.directories.add(
        generatedLauncherAssetsDir.get().asFile.absolutePath
    )
}

val generateLauncherAssets = tasks.register<Exec>("generateLauncherAssets") {
    val launcherScript = rootProject.file("launcher-tools/build_launchers.py")
    val launcherSource = rootProject.file("launcher-tools/LauncherActivity.java")
    inputs.files(launcherScript, launcherSource, debugSigningKey)
    inputs.property("hostPackage", android.defaultConfig.applicationId ?: "com.clony")
    outputs.dir(generatedLauncherAssetsDir)
    commandLine(
        "python3",
        launcherScript.absolutePath,
        "--sdk", androidComponents.sdkComponents.sdkDirectory.get().asFile.absolutePath,
        "--jdk", System.getProperty("java.home"),
        "--output", generatedLauncherAssetsDir.get().asFile.absolutePath,
        "--host-package", android.defaultConfig.applicationId ?: "com.clony",
        "--keystore", debugSigningKey.absolutePath,
        "--build-tools", "36.1.0"
    )
}

val generatedCloneAliasDir = layout.buildDirectory.dir("generated/clone-package-alias")
tasks.register<Exec>("buildCloneAliasLauncher") {
    val launcherScript = rootProject.file("launcher-tools/build_launchers.py")
    val launcherSource = rootProject.file("launcher-tools/LauncherActivity.java")
    val packageAlias = providers.gradleProperty("cloneAliasPackage")
    val aliasLabel = providers.gradleProperty("cloneAliasLabel")
    inputs.files(launcherScript, launcherSource, debugSigningKey)
    inputs.property("cloneAliasPackage", packageAlias.orElse(""))
    inputs.property("cloneAliasLabel", aliasLabel.orElse(""))
    outputs.dir(generatedCloneAliasDir)
    doFirst {
        val requestedAlias = packageAlias.orNull?.trim()?.takeIf(String::isNotEmpty)
            ?: throw GradleException("Pass -PcloneAliasPackage=<clone package alias>.")
        val label = aliasLabel.orNull?.trim()?.takeIf(String::isNotEmpty) ?: requestedAlias
        val aliasApk = generatedCloneAliasDir.get()
            .file("${requestedAlias.substringAfterLast('.')}.apk")
        commandLine(
            "python3",
            launcherScript.absolutePath,
            "--sdk", androidComponents.sdkComponents.sdkDirectory.get().asFile.absolutePath,
            "--jdk", System.getProperty("java.home"),
            "--output", generatedCloneAliasDir.get().asFile.absolutePath,
            "--host-package", android.defaultConfig.applicationId ?: "com.clony",
            "--keystore", debugSigningKey.absolutePath,
            "--build-tools", "36.1.0",
            "--package-alias", requestedAlias,
            "--alias-label", label,
            "--output-apk", aliasApk.asFile.absolutePath
        )
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateLauncherAssets)
    dependsOn(":proxy-engine:buildEmbeddedCore")
}

dependencies {
    implementation(project(":nekobox"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":Bcore"))
    implementation(files(rootProject.file("proxy-engine/build/embedded/libcore.aar")))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Keep the test APK installable on current Android without changing the runtime host target.
androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.androidTest?.targetSdk = 37
    }
}
