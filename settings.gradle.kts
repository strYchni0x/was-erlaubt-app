pluginManagement {
    repositories {
        google()
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

rootProject.name = "WasErlaubt"
include(":app")

// Build-Ausgabe nach D:/build umleiten (verhindert OneDrive-Sync-Sperren)
val buildBase = File("D:/build/waserlaubt")
gradle.allprojects {
    layout.buildDirectory.set(buildBase.resolve(project.name))
}
