pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/") { content { includeGroup("com.ownid.android-sdk") } }
    }
}

rootProject.name = "OwnIDAndroidSDK"

includeBuild("sdk-core") {
    dependencySubstitution {
        substitute(module("com.ownid.android-sdk:core")).using(project(":core"))
    }
}

includeBuild("sdk-compose") {
    dependencySubstitution {
        substitute(module("com.ownid.android-sdk:compose")).using(project(":compose"))
    }
}

includeBuild("demoBase")
includeBuild("demoAdvanced")
