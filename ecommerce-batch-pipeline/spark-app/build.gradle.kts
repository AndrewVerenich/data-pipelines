plugins {
  application
  java
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

group = "com.example"
version = "1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.apache.spark:spark-core_2.12:3.5.0")
  implementation("org.apache.spark:spark-sql_2.12:3.5.0")
}

application {
  mainClass.set("com.example.SimpleSparkJob")
}

tasks.shadowJar {
  isZip64 = true
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
