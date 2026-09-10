// Repository order matters on this machine.
//
// dl.google.com is region-gated from this connection: most paths need the local
// proxy, and some (e.g. androidx.compose.material:material-android:1.7.5) return
// 404 even through it, which failed the build. The Tencent and Huawei mirrors
// serve byte-identical artifacts, reachable directly at 700-800 KB/s versus
// ~213 KB/s through the proxy.
//
// Google and Maven Central stay last in the chain: Gradle falls through to them
// for anything a mirror lacks, so nothing here is *only* obtainable from a
// mirror. See tools/build.sh, which routes these hosts around the proxy.
pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
    }
}

rootProject.name = "MusicPlayer"
include(":shared")
include(":app")
include(":desktop")
