import org.gradle.api.tasks.compile.JavaCompile

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
    implementation(project(":kora-quarkus"))
    implementation("io.quarkus:quarkus-core-deployment:$quarkusVersion")
    implementation("io.quarkus:quarkus-arc-deployment:$quarkusVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation(platform("io.quarkus:quarkus-bom:$quarkusVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.quarkus:quarkus-junit5:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-junit5-internal:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-agroal")
    testImplementation("io.quarkus:quarkus-jdbc-h2")
    testImplementation("com.h2database:h2:2.2.224")
    testCompileOnly(project(":kora-processor"))
    testAnnotationProcessor(project(":kora-processor"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    if (name == "compileTestJava") {
        options.compilerArgs.add("-Akora.mapper=${project.projectDir}/src/test/resources/mapper")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("kora-quarkus-deployment")
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
