pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

rootProject.name = "mocha"

include(
    "lexer",
    "parser",
    "runtime",
    "runtime-compiler",
)
