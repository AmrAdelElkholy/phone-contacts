rootProject.name = "EasyPhone"
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://www.jitpack.io") }
        mavenLocal()
    }
    versionCatalogs {
        create("commonsLibs") {
            from(files("commons/libs.versions.toml"))
        }
    }
}
include(":app", ":contacts", ":commons")
