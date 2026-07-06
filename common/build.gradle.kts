plugins {
    java
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
