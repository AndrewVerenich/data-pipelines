package com.example.banking.generator

import com.example.banking.model.Customer
import com.example.banking.producer.EventProducer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

@Component
class CustomerGenerator(
  private val producer: EventProducer
) {
  private val log = LoggerFactory.getLogger(CustomerGenerator::class.java)
  private val customerId = AtomicLong(1)

  private val firstNames = listOf(
    "John", "Emma", "Liam", "Olivia", "Noah", "Ava", "Mason", "Sophia", "Ethan", "Mia",
    "Lucas", "Amelia", "Henry", "Isabella", "Jack", "Elena", "Leo", "Nora", "Daniel", "Aria"
  )
  private val lastNames = listOf(
    "Smith", "Johnson", "Brown", "Taylor", "Miller", "Wilson", "Moore", "Clark", "Walker", "Hall",
    "Young", "Allen", "King", "Wright", "Scott", "Green", "Baker", "Adams", "Carter", "Turner"
  )
  private val locations = listOf(
    "New York" to "USA",
    "Chicago" to "USA",
    "London" to "UK",
    "Manchester" to "UK",
    "Berlin" to "Germany",
    "Munich" to "Germany",
    "Paris" to "France",
    "Lyon" to "France",
    "Madrid" to "Spain",
    "Barcelona" to "Spain"
  )

  @Scheduled(fixedDelayString = "\${app.generator.customer-interval-ms}")
  fun generate() {
    val id = customerId.getAndIncrement()
    val firstName = firstNames.random()
    val lastName = lastNames.random()
    val location = locations.random()

    val customer = Customer(
      customer_id = id,
      first_name = firstName,
      last_name = lastName,
      email = "${firstName.lowercase()}.${lastName.lowercase()}$id@example.com",
      age = Random.nextInt(18, 71),
      city = location.first,
      country = location.second,
      registration_date = LocalDate.now().minusDays(Random.nextLong(0, 3650)),
      timestamp = Instant.now()
    )

    producer.send("banking.customers", id.toString(), customer)
    log.info("Customer generated: id={} email={}", id, customer.email)
  }
}
