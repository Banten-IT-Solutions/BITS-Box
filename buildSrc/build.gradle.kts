plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    // Gradle Plugins - updated to latest stable
    implementation("com.android.tools.build:gradle:9.3.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
}
