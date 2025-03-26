plugins {
    kotlin("android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.android.application") version "8.3.0" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}

allprojects {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}