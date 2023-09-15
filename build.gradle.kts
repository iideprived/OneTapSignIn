import org.jetbrains.kotlin.gradle.plugin.extraProperties


buildscript {

    dependencies {
        classpath(libs.google.services)
        classpath(libs.kotlin.gradle.plugin)
    }
    repositories{
        mavenCentral()
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.library) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}
true // Needed to make the Suppress annotation work for the plugins block