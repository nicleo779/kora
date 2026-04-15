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
    implementation(project(":kora-core"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("kora-processor")
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
