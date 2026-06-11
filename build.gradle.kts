import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.vanniktech.maven.publish") apply false
}

group = "org.byteora"
version = "1.0.3"

allprojects {
    description = when (name) {
        "kyra-core" -> "Kyra reflector core runtime"
        "kyra-processor-core" -> "Kyra annotation processor shared code generation support"
        "kyra-processor" -> "Kyra reflector annotation processor"
        "kyra-orm" -> "Kyra lightweight SQL framework ORM runtime"
        "kyra-orm-processor" -> "Kyra ORM annotation processor for mapper and meta generation"
        "kyra-quarkus" -> "Kyra Quarkus extension runtime support"
        "kyra-quarkus-deployment" -> "Kyra Quarkus extension deployment support"
        "kyra-spring-boot" -> "Kyra Spring Boot auto-configuration support"
        "kyra-json" -> "Kyra JSON serialization support"
        "kyra-excel" -> "Kyra dependency-free Excel (.xlsx) engine"
        else -> "Kyra project"
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    if (name == "simple") {
        return@subprojects
    }
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions)
            .addStringOption("Xdoclint:none", "-quiet")
    }
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "signing")

    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
        publishToMavenCentral(true)
        signAllPublications()

        pom {
            name.set(project.name)
            description.set(project.description ?: "Kyra project")
            url.set("https://github.com/byteora/kyra")

            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }

            developers {
                developer {
                    id.set("byterola")
                    name.set("byterola")
                }
            }

            scm {
                connection.set("scm:git:https://github.com/byteora/kyra.git")
                developerConnection.set("scm:git:ssh://git@github.com:byteora/kyra.git")
                url.set("https://github.com/byteora/kyra")
            }
        }
    }
}
