plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("org.apache.commons:commons-math3:3.6.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
