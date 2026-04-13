plugins {
    id("java-library")
    id("maven-publish")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.2.224")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "kora-core"

            pom {
                name.set("kora-core")
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
            url = uri("https://repo.repsy.io/${providers.environmentVariable("REPSY_USERNAME")}/public")
            credentials {
                username = providers.gradleProperty("repsyUsername")
                    .orElse(providers.environmentVariable("REPSY_USERNAME"))
                    .orElse("${providers.environmentVariable("REPSY_USERNAME")}")
                    .get()
                password = providers.gradleProperty("repsyPassword")
                    .orElse(providers.environmentVariable("REPSY_PASSWORD"))
                    .orNull
            }
        }
    }
}
