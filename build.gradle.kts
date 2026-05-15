group = "com.nicleo"
version = "1.1.7"

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

allprojects {
    description = when (name) {
        "kora-core" -> "Kora lightweight SQL framework core runtime"
        "kora-processor" -> "Kora annotation processor for mapper, reflector, and meta generation"
        "kora-quarkus" -> "Kora Quarkus extension runtime support"
        "kora-quarkus-deployment" -> "Kora Quarkus extension deployment support"
        "kora-spring-boot" -> "Kora Spring Boot auto-configuration support"
        else -> "Kora project"
    }
}
