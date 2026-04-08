group = "com.nicleo"
version = "1.0-SNAPSHOT"

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
        else -> "Kora project"
    }
}
