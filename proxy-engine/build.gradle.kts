plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clony.proxyengine"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val embeddedCore = layout.buildDirectory.file("embedded/libcore.aar")
val buildEmbeddedCore by tasks.registering(Exec::class) {
    val sourceFiles = fileTree("native/libcore") {
        exclude(".build/**")
    }
    inputs.files(sourceFiles)
    inputs.file("build_core.sh")
    inputs.property("singBoxCommit", "aed32ee3066cdbc7d471e3e0415c5134088962df")
    inputs.property("libnekoCommit", "1c47a3af71990a7b2192e03292b4d246c308ef0b")
    outputs.file(embeddedCore)

    commandLine("bash", file("build_core.sh").absolutePath, embeddedCore.get().asFile.absolutePath)
}

tasks.named("preBuild").configure {
    dependsOn(buildEmbeddedCore)
}
