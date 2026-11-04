import java.security.KeyStore
import java.security.MessageDigest
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val lucenseVersion = providers.gradleProperty("lucenseVersion").get()

// Android wants a single increasing integer, so major.minor.patch is folded
// into one: 1.6.0 becomes 10600. Two digits each for minor and patch, which
// keeps the number readable next to the versionName it came from.
val lucenseVersionCode = lucenseVersion.split(".")
    .also {
        require(it.size == 3) {
            "lucenseVersion must be major.minor.patch, but was '$lucenseVersion'"
        }
    }
    .let { (major, minor, patch) ->
        major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    }

android {
    namespace = "com.exo.musicplayer"
    compileSdk = 35
    // Pinned to the build-tools package staged into .android-sdk by tools/setup-sdk.sh.
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.exo.musicplayer"
        // Android 7.0. java.time, which the shared code uses, comes through
        // core library desugaring below; nothing else here needs newer.
        minSdk = 24
        targetSdk = 35
        versionCode = lucenseVersionCode
        versionName = lucenseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    // yt-dlp ships a Python runtime and ffmpeg as native libraries, about 50 MB
    // for each processor type. One APK per type keeps each download that size;
    // the universal one carries all four and installs anywhere. Plenty of phones
    // run 32-bit Android even on a 64-bit chip, and those need armeabi-v7a.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Every APK published so far was a debug build, signed with the debug
            // key of the machine that builds releases. Android only installs an
            // update signed by the same key, so the release build is signed with
            // it too. What changes is the build itself: shrunk by R8, not
            // debuggable, and carrying the AndroidX baseline profiles, so Compose
            // is compiled ahead of time instead of interpreted on a debug runtime.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // java.time on Android 7, where the platform doesn't have it yet.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // For BuildConfig.VERSION_NAME, shown in Settings.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // The Python runtime is exec'd from disk, so it must be extracted
        // rather than loaded straight out of a compressed APK.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(project(":shared"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    // SoundCloud streams come as HLS playlists.
    implementation(libs.androidx.media3.hls)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)

    implementation(libs.ytdlp.library)
    implementation(libs.ytdlp.ffmpeg)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// ---- The release key -------------------------------------------------------
//
// Android installs an update only when it is signed with the same key as the
// app already on the phone. Lucense's key is this machine's debug key, which
// Gradle makes by itself when it finds none: on a new machine, or after the
// .android folder is gone, a release would come out signed with a fresh key
// that no phone with Lucense on it accepts, short of uninstalling and losing
// the library. So a release build first checks the key is the one every APK
// so far was signed with, and stops if not.
val checkReleaseKey by tasks.registering {
    val store = android.signingConfigs.getByName("debug").storeFile
    val password = android.signingConfigs.getByName("debug").storePassword ?: "android"
    val alias = android.signingConfigs.getByName("debug").keyAlias ?: "androiddebugkey"
    val expected = providers.gradleProperty("lucenseReleaseKeySha256")
        .getOrElse("893eab7415de067fa474a63a1bd4f79753ae970c735eb1ffe96f4871a9726b9e")
    doLast {
        val file = store ?: error("No signing key is set up for release builds.")
        check(file.isFile) {
            "Lucense's signing key isn't at $file. Restore it from its backup before building a release: " +
                "an APK signed with any other key can't install over Lucense."
        }
        val keys = KeyStore.getInstance(file, password.toCharArray())
        val cert = keys.getCertificate(alias) ?: error("$file has no key named $alias.")
        val found = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
        check(found == expected) {
            "$file is not Lucense's signing key (SHA-256 $found, expected $expected). Restore Lucense's key from " +
                "its backup: an APK signed with this one can't install over Lucense."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseKey) }
