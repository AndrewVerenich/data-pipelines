plugins {
  kotlin("jvm")
  id("com.github.johnrengelman.shadow") version "8.1.1"
  application
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions.jvmTarget = "11"
}

//java {
//  sourceCompatibility = JavaVersion.VERSION_11
//  targetCompatibility = JavaVersion.VERSION_11
//}

repositories { mavenCentral() }

dependencies {
  implementation("org.apache.flink:flink-streaming-java:1.17.1")
  implementation("org.apache.flink:flink-clients:1.17.1")
  implementation("org.apache.flink:flink-connector-kafka:1.17.1")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
}

application {
  mainClass.set("com.example.flink.UserAnalyticsJob")
}

tasks {
  shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
  }
}