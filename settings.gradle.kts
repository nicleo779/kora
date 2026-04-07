pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "fastSQL"

include("fastsql-core")
include("fastsql-compiler")
include("fastsql-idea-plugin")
