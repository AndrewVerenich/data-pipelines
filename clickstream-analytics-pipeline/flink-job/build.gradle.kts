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

repositories { mavenCentral() }

val flinkVersion = "1.17.1"

dependencies {
  // Flink core: provided by the Flink cluster at runtime, so compileOnly to keep the shadow jar slim.
  compileOnly("org.apache.flink:flink-streaming-java:$flinkVersion")
  compileOnly("org.apache.flink:flink-clients:$flinkVersion")

  // Kafka connector is NOT bundled with Flink 1.17: must be shipped in the shadow jar.
  implementation("org.apache.flink:flink-connector-kafka:$flinkVersion")

  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

  testImplementation("org.apache.flink:flink-streaming-java:$flinkVersion")
  testImplementation("org.apache.flink:flink-clients:$flinkVersion")
}

application {
  mainClass.set("com.example.flink.ClickstreamAnalyticsJob")
}

tasks {
  shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
    archiveBaseName.set("flink-job")
    mergeServiceFiles()
  }

  build {
    dependsOn(shadowJar)
  }
}
