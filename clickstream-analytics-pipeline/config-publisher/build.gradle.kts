plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.apache.kafka:kafka-clients:3.5.1")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
}

tasks {
  bootJar {
    archiveFileName.set("config-publisher.jar")
  }
  jar { enabled = false }
}
