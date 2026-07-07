plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("co.elastic.clients:elasticsearch-java:8.14.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Self-instrumentation: Prometheus scrape endpoint at /actuator/prometheus.
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Alerting metadata store. H2 runs everywhere out of the box (the default); the Postgres
    // driver is on the classpath so pointing SPRING_DATASOURCE_URL at the compose Postgres works
    // with no code changes.
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:elasticsearch")
}
