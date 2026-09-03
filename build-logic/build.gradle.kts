plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation("com.android.tools.build:gradle:9.2.1")
}

gradlePlugin {
    plugins {
        create("ownIdSdkLibrary") {
            id = "ownid.sdk-library"
            implementationClass = "OwnIdSdkLibraryPlugin"
        }
    }
}
