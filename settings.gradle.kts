pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kora"

include("kora-core")
include("kora-processor")
include("simple")
