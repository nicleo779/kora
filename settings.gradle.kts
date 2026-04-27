pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("io.quarkus.extension") version providers.gradleProperty("quarkusVersion").get()
    }
}

rootProject.name = "kora"

include("kora-core")
include("kora-processor")
include("kora-quarkus")
include("kora-quarkus-deployment")
include("kora-spring-boot")
include("simple")
