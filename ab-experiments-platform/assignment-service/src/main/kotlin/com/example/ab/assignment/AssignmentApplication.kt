package com.example.ab.assignment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AssignmentApplication

fun main(args: Array<String>) {
  runApplication<AssignmentApplication>(*args)
}
