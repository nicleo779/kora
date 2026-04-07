plugins {
    id("java-library")
    id("maven-publish")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "fastsql-core"

            pom {
                name.set("fastsql-core")
                description.set("Core template parser and runtime entrypoint for FastSql.")
                url.set("https://github.com/nicleo/fastsql")

                licenses {
                    license {
                        name.set("Apache License 2.0")
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
                    url.set("https://github.com/nicleo/fastsql")
                    connection.set("scm:git:https://github.com/nicleo/fastsql.git")
                    developerConnection.set("scm:git:https://github.com/nicleo/fastsql.git")
                }
            }
        }
    }

    repositories {
        mavenLocal()

        val releasesRepoUrl = providers.gradleProperty("mavenReleasesRepoUrl")
            .orElse(providers.environmentVariable("MAVEN_RELEASES_URL"))
        val snapshotsRepoUrl = providers.gradleProperty("mavenSnapshotsRepoUrl")
            .orElse(providers.environmentVariable("MAVEN_SNAPSHOTS_URL"))
        val username = providers.gradleProperty("mavenRepoUsername")
            .orElse(providers.environmentVariable("MAVEN_USERNAME"))
        val password = providers.gradleProperty("mavenRepoPassword")
            .orElse(providers.environmentVariable("MAVEN_PASSWORD"))

        val targetRepoUrl = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        if (targetRepoUrl.isPresent) {
            maven {
                name = "remote"
                url = uri(targetRepoUrl.get())
                credentials {
                    this.username = username.orNull
                    this.password = password.orNull
                }
            }
        }
    }
}
