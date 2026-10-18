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
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation("com.clickhouse:clickhouse-jdbc:0.6.0-patch5")
  implementation("ai.catboost:catboost-prediction:1.2.5")
}

tasks.bootJar {
  archiveFileName.set("fraud-scoring-service-1.0.0.jar")
}
