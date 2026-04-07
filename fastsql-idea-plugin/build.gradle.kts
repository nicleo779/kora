plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

val pluginDescription = """
    FastSql adds SQL-template authoring support for Java string templates written with `FastSql.sql(...)`.
    It provides syntax checks for `@if`, `@for`, and `${'$'}{...}` blocks, plus basic completion for variables and members from the surrounding Java scope.
""".trimIndent()

val publishToken = providers.gradleProperty("intellijPublishToken")
    .orElse(providers.environmentVariable("PUBLISH_TOKEN"))
    .orElse(providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN"))

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":fastsql-core"))

    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        description = pluginDescription
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "241.*"
        }
    }
    publishing {
        token = publishToken
        channels = listOf("default")
    }
    buildSearchableOptions = false
}
