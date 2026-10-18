package com.example.ab.assignment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AssignmentProperties::class)
class AssignmentApplication

fun main(args: Array<String>) {
    runApplication<AssignmentApplication>(*args)
}
