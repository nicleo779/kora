plugins {
    id("java-library")
    id("maven-publish")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":kora-core"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "kora-processor"

            pom {
                name.set("kora-processor")
                description.set(project.description)
                url.set("https://github.com/nicleo/kora")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("nicleo")
                        name.set("nicleo")
                    }
                }

                scm {
                    url.set("https://github.com/nicleo/kora")
                    connection.set("scm:git:https://github.com/nicleo/kora.git")
                    developerConnection.set("scm:git:https://github.com/nicleo/kora.git")
                }
            }
        }
    }

    repositories {
        mavenLocal()
        maven {
            name = "repsy"
            url = uri("https://repo.repsy.io/user30355289/public")
            credentials {
                username = providers.gradleProperty("repsyUsername")
                    .orElse(providers.environmentVariable("REPSY_USERNAME"))
                    .orElse("user30355289")
                    .get()
                password = providers.gradleProperty("repsyPassword")
                    .orElse(providers.environmentVariable("REPSY_PASSWORD"))
                    .orNull
            }
        }
    }
}
