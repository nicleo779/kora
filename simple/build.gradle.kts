plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
    implementation(project(":kora-core"))
    annotationProcessor(project(":kora-processor"))
// Source: https://mvnrepository.com/artifact/org.projectlombok/lombok

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.2.224")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor(project(":kora-processor"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Akora.projectDir=${project.projectDir.absolutePath}")
}

tasks.test {
    useJUnitPlatform()
}
