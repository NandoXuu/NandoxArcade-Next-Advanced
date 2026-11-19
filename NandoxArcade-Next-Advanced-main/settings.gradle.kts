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
        // LibretroDroid (frontend libretro para Android) é distribuído via JitPack
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Swordfish90") }
        }
    }
}

rootProject.name = "NandoxArcade"
include(":app")
