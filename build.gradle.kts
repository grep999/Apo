plugins {
    kotlin("jvm") version "1.9.24"
}

group = "com.emberfall"
version = "0.1.0"

repositories { mavenCentral() }

kotlin { jvmToolchain(21) }

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
