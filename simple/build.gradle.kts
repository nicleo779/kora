import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
    testImplementation("org.mybatis:mybatis:3.5.19")
    testImplementation("com.baomidou:mybatis-plus-core:3.5.16")
    testImplementation("org.jooq:jooq:3.20.12")
    testImplementation("org.babyfish.jimmer:jimmer-sql:0.10.6")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor(project(":kora-processor"))
    testAnnotationProcessor("org.babyfish.jimmer:jimmer-apt:0.10.6")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Akora.mapper=${project.projectDir}/src/main/resources/mapper")
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Run JMH benchmarks for the simple module"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
}
