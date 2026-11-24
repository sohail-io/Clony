plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.nekohasekai.sagernet"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("proguard-rules.pro")
        buildConfigField("String", "APPLICATION_ID", "\"com.clony\"")
        buildConfigField("String", "VERSION_NAME", "\"1.4.2\"")
        buildConfigField("int", "VERSION_CODE", "46")
        buildConfigField("String", "PRE_VERSION_NAME", "\"\"")
    }

    flavorDimensions += "vendor"
    productFlavors {
        create("oss")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

}

dependencies {
    // Clony already builds and packages this NekoBox-derived core in :proxy-engine.
    compileOnly(files(rootProject.file("proxy-engine/build/embedded/libcore.aar")))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.appcompat)
    api(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.multiprocess)

    implementation(libs.google.material)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.lite)
    implementation(libs.editorkit)
    implementation(libs.language.base)
    implementation(libs.language.json)
    implementation(libs.okhttp)
    implementation(libs.snakeyaml)
    implementation(libs.material.about)
    implementation(libs.process.phoenix)
    implementation(libs.kryo)
    implementation(libs.guava)
    implementation(libs.ini4j)
    implementation(libs.recyclerview.fastscroll) {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.roomigrant.lib)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

tasks.named("preBuild").configure {
    dependsOn(":proxy-engine:buildEmbeddedCore")
}
