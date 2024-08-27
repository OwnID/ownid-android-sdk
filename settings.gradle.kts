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

include(":demo:common")
include(":demo:gigya")
include(":demo:gigya-java")
include(":demo:gigya-screens")
include(":demo:gigya-view")
include(":demo:integration-custom")
include(":demo:integration-direct")

include(":sdk:core")
include(":sdk:compose")
include(":sdk:gigya")
