plugins {
  java
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

group = "com.example"
version = "1.0.0"

repositories {
  mavenCentral()
}

val sparkVersion = "3.5.0"

dependencies {
  compileOnly("org.apache.spark:spark-sql_2.12:$sparkVersion")
  compileOnly("org.apache.spark:spark-core_2.12:$sparkVersion")
}

tasks.jar {
  archiveFileName.set("banking-spark-jobs-1.0.0.jar")
}
