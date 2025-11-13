pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://storage.googleapis.com/tensorflow/maven")
        maven("https://androidx.dev/snapshots/builds/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://storage.googleapis.com/tensorflow/maven")
        maven("https://androidx.dev/snapshots/builds/")
    }
}

rootProject.name = "RoadGuardianAuto2"
include(":app")
