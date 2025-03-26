pluginManagement {
    repositories {
        gradlePluginPortal()
        google()  // ✅ ESSENCIAL para encontrar o Compose Compiler
        mavenCentral()

    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()  // ✅ ESSENCIAL aqui também
        mavenCentral()
    }
}

rootProject.name = "YouTubeMusicConnect"
include(":app")
