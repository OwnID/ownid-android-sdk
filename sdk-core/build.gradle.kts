plugins {
    base
}

val sdkVersion = providers.fileContents(layout.projectDirectory.file("../sdk-version.txt")).asText.map(String::trim).get()

group = "com.ownid.android-sdk"
version = sdkVersion

subprojects {
    group = rootProject.group
    version = rootProject.version
}
