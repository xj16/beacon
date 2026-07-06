plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")

    // Elasticsearch Java client (transport over the low-level REST client)
    implementation("co.elastic.clients:elasticsearch-java:8.14.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility:4.2.1")

    // Testcontainers for a real Elasticsearch in integration tests; Kafka is provided by Spring's
    // in-JVM EmbeddedKafka (from spring-kafka-test) for deterministic broker behaviour.
    // Versions are managed by Spring Boot's dependency management (spring-boot-dependencies).
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:elasticsearch")
}
