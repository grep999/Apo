plugins {
    kotlin("jvm") version "1.9.24"
}

group = "com.emberfall"
version = "0.1.0"

repositories { mavenCentral() }

kotlin { jvmToolchain(21) }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test { useJUnitPlatform() }
