package com.example.banking.generator

import com.example.banking.model.Account
import com.example.banking.producer.EventProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

@Component
class AccountGenerator(
  private val producer: EventProducer,
  @Value("\${app.generator.customer-id-range}") private val customerIdRange: Int
) {
  private val log = LoggerFactory.getLogger(AccountGenerator::class.java)
  private val accountId = AtomicLong(1)

  private val weightedAccountTypes = listOf(
    "checking" to 50,
    "savings" to 35,
    "credit" to 15
  ).flatMap { (value, weight) -> List(weight) { value } }

  private val weightedCurrencies = listOf(
    "USD" to 60,
    "EUR" to 25,
    "GBP" to 15
  ).flatMap { (value, weight) -> List(weight) { value } }

  @Scheduled(fixedDelayString = "\${app.generator.account-interval-ms}")
  fun generate() {
    val id = accountId.getAndIncrement()
    val customerId = Random.nextLong(1, customerIdRange.toLong() + 1)
    val accountType = weightedAccountTypes.random()
    val currency = weightedCurrencies.random()

    val account = Account(
      account_id = id,
      customer_id = customerId,
      account_type = accountType,
      currency = currency,
      opened_at = LocalDate.now().minusDays(Random.nextLong(0, 3650)),
      timestamp = Instant.now()
    )

    producer.send("banking.accounts", id.toString(), account)
    log.info("Account generated: id={} customer={} type={} currency={}", id, customerId, accountType, currency)
  }
}
