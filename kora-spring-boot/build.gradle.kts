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
    api(project(":kora-core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure:4.0.0")
    implementation("org.springframework:spring-jdbc:6.1.11")

    compileOnly("org.springframework.boot:spring-boot-configuration-processor:4.0.0")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.0.0")
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.0")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:4.0.0")
    testCompileOnly(project(":kora-processor"))
    testAnnotationProcessor(project(":kora-processor"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("kora-spring-boot")
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
