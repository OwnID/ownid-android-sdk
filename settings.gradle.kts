pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "OwnID Android SDK"

include(":demo-common")
include(":demo-custom-integration")
include(":demo-direct-integration")
include(":demo-gigya")
include(":demo-gigya-compose")
include(":demo-gigya-screens")

include(":sdk-core")
include(":sdk-compose")
include(":sdk-gigya")
include(":sdk-redirect")