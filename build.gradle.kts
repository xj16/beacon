plugins {
    java
    jacoco
    kotlin("jvm") version "1.9.25" apply false
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "dev.xj16.beacon"
    version = "0.2.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

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

    // Per-module coverage. The report aggregates whatever execution data is present, so it reflects
    // unit tests alone locally and unit + integration tests in CI (where Docker is available).
    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    tasks.named<JacocoReport>("jacocoTestReport") {
        executionData(fileTree(layout.buildDirectory).include("jacoco/*.exec"))
    }
    tasks.named("test") { finalizedBy(tasks.named("jacocoTestReport")) }
}

// ---------------------------------------------------------------------------
// Aggregate coverage across all modules into one XML + HTML report, and print a
// single line-coverage percentage that the README badge / CI can consume.
// ---------------------------------------------------------------------------
tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Aggregates JaCoCo coverage across all modules."
    val reportProjects = subprojects
    dependsOn(reportProjects.map { "${it.path}:test" })

    executionData.setFrom(
        files(reportProjects.map { fileTree(it.layout.buildDirectory).include("jacoco/*.exec") })
    )
    sourceDirectories.setFrom(
        files(reportProjects.flatMap { p ->
            listOf("src/main/java", "src/main/kotlin").map { p.file(it) }
        })
    )
    classDirectories.setFrom(
        files(reportProjects.map { it.layout.buildDirectory.dir("classes") })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregate/jacoco.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregate/html"))
    }
}

// Prints e.g. "Coverage: 84.2% lines" from the aggregate XML. Handy in CI logs.
tasks.register("printCoverage") {
    group = "verification"
    dependsOn("jacocoRootReport")
    doLast {
        val xml = layout.buildDirectory.file("reports/jacoco/aggregate/jacoco.xml").get().asFile
        if (!xml.exists()) {
            println("No aggregate coverage report found.")
            return@doLast
        }
        // The <report> element's last top-level <counter type="LINE" .../> holds the totals.
        val text = xml.readText()
        val m = Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""")
            .findAll(text).lastOrNull()
        if (m != null) {
            val missed = m.groupValues[1].toLong()
            val covered = m.groupValues[2].toLong()
            val pct = if (missed + covered == 0L) 0.0 else covered * 100.0 / (missed + covered)
            println("Coverage: ${"%.1f".format(pct)}% lines ($covered/${missed + covered})")
        } else {
            println("Could not parse coverage from $xml")
        }
    }
}
