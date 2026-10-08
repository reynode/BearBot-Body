pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.papermc.paperweight.userdev") {
                useModule("io.papermc.paperweight:paperweight-userdev:${requested.version}")
            }
        }
    }
    repositories {
        maven {
            url = uri("$settingsDir/.gradle/local-maven")
        }
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "BearBot Body"
