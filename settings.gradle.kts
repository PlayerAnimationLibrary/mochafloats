pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradleup.nmcp.settings") version "1.6.2"
}

rootProject.name = "mocha"

include(
    "lexer",
    "parser",
    "runtime",
    "runtime-compiler",
)

// publishAggregationToCentralPortal uploads the publications of all modules as a single deployment
nmcpSettings {
    centralPortal {
        username = System.getenv("CENTRAL_USERNAME")
        password = System.getenv("CENTRAL_PASSWORD")
        publishingType = "AUTOMATIC"
    }
}
