plugins {
    java
    kotlin("jvm") version "1.9.25" apply false
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "dev.xj16.beacon"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // The default `test` task runs only fast, container-free unit tests so it passes with no
    // Docker daemon. Testcontainers-backed integration tests (class name ends in `IT`) are split
    // into a separate `integrationTest` task that CI runs where Docker is available.
    tasks.named<Test>("test") {
        filter {
            excludeTestsMatching("*IT")
        }
    }

    tasks.register<Test>("integrationTest") {
        description = "Runs Testcontainers-backed integration tests (requires Docker)."
        group = "verification"
        useJUnitPlatform()
        filter {
            includeTestsMatching("*IT")
            // Modules without integration tests (e.g. :common) must not fail the task.
            isFailOnNoMatchingTests = false
        }
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        shouldRunAfter(tasks.named("test"))
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
