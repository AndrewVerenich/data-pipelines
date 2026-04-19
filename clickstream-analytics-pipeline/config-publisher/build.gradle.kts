plugins {
  kotlin("jvm")
  id("com.github.johnrengelman.shadow") version "8.1.1"
  application
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

dependencies {
  implementation("org.apache.kafka:kafka-clients:3.5.1")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
  implementation("org.slf4j:slf4j-simple:2.0.9")
}

application {
  mainClass.set("com.example.config.ConfigPublisherKt")
}

tasks {
  shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
    archiveBaseName.set("config-publisher")
  }
}
