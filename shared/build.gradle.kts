// Platform-neutral core: fingerprinting, DSP, metadata/lyrics providers and the
// weather scoring. Plain Kotlin/JVM on purpose — it is consumed by both the
// Android app and the Windows desktop app, so it must not reference the Android
// framework.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Targets Java 17 bytecode using whichever JDK is running, rather than
// demanding a JDK 17 toolchain be installed. :app consumes this module and
// Android rejects class files newer than 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    // Android ships org.json in the framework, so packaging a second copy would
    // collide. Each consumer supplies it: Android from the platform, desktop as
    // a real dependency.
    compileOnly(libs.org.json)
}
