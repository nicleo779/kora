plugins {
    id("java-library")
    id("maven-publish")
}

val quarkusVersion: String by project

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    api(project(":kora-core"))

    implementation(platform("io.quarkus:quarkus-bom:$quarkusVersion"))
    implementation("io.quarkus:quarkus-arc")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("projectGroup", project.group.toString())
    inputs.property("projectVersion", project.version.toString())
    filesMatching("META-INF/quarkus-extension.properties") {
        expand(
            "projectGroup" to project.group,
            "projectVersion" to project.version
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("kora-quarkus")
                description.set(project.description)
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }

    repositories {
        mavenLocal()
        maven {
            setUrl(providers.environmentVariable("MAVEN_URL").orElse("").get())
            credentials {
                username = providers.environmentVariable("MAVEN_USERNAME")
                    .orNull
                password = providers.environmentVariable("MAVEN_PASSWORD")
                    .orNull
            }
        }
    }
}
