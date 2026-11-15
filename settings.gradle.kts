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

rootProject.name = "Lume"

// Nome e pacote provisórios (ver README.md): "Lume" / app.lume vieram do
// protótipo e NÃO são decisão de marca. Decidir nome/pacote finais antes
// de publicar na Play Store — o applicationId não pode mudar depois.

include(":app")
