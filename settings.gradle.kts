pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "phoenix-android"

include(":fyt-ipc")
include(":ui-theme")
include(":mock-syu-ms")
include(":launcher")
include(":hvac-bar")
