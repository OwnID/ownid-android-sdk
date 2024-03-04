pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io") // For Gigya
    }
}

rootProject.name = "OwnID Android SDK"

include(":demo-common")
include(":demo-custom-integration")
include(":demo-direct-integration")
include(":demo-gigya")
include(":demo-gigya-compose")
include(":demo-gigya-java")
include(":demo-gigya-screens")

include(":sdk-core")
include(":sdk-compose")
include(":sdk-gigya")
include(":sdk-redirect")