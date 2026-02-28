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

rootProject.name = "Minnal"

include(":app")
include(":libtorrent")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":feature:torrentlist")
include(":feature:addtorrent")
include(":feature:settings")
