plugins {
    id("java-library")
    id("maven-publish")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

val javacExports = listOf(
    "jdk.compiler/com.sun.tools.javac.api",
    "jdk.compiler/com.sun.tools.javac.parser",
    "jdk.compiler/com.sun.tools.javac.processing",
    "jdk.compiler/com.sun.tools.javac.tree",
    "jdk.compiler/com.sun.tools.javac.util"
)

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(javacExports.flatMap { listOf("--add-exports", "$it=ALL-UNNAMED") })
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(javacExports.flatMap { listOf("--add-exports", "$it=ALL-UNNAMED") })
}

dependencies {
    api(project(":fastsql-core"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "fastsql-compiler"

            pom {
                name.set("fastsql-compiler")
                description.set("Javac compiler plugin for compiling FastSql templates into Java string building code.")
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
