package com.example.retl.email

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(EmailMockProperties::class)
class EmailMockApplication

fun main(args: Array<String>) {
    runApplication<EmailMockApplication>(*args)
}
