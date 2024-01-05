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
        maven(url = "https://s01.oss.sonatype.org/content/groups/staging/")
        maven(url = "https://jitpack.io") // For Gigya
    }
}

rootProject.name = "OwnID Android SDK"

include(":demo-common")
include(":demo-gigya")
include(":demo-gigya-compose")
include(":demo-gigya-java")
include(":demo-gigya-screens")
include(":demo-integration")
include(":demo-redirect")

include(":sdk-core")
include(":sdk-compose")
include(":sdk-gigya")
include(":sdk-redirect")