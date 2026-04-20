plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

tasks.bootJar {
  archiveFileName.set("banking-generator-1.0.0.jar")
}
