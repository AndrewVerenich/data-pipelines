package com.example.banking.generator

import com.example.banking.model.Transaction
import com.example.banking.producer.EventProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

@Component
class TransactionGenerator(
  private val producer: EventProducer,
  @Value("\${app.generator.account-id-range}") private val accountIdRange: Int
) {
  private val log = LoggerFactory.getLogger(TransactionGenerator::class.java)

  private val weightedCategories = listOf(
    "groceries" to 20,
    "salary" to 10,
    "rent" to 10,
    "transfer" to 15,
    "utilities" to 10,
    "entertainment" to 15,
    "healthcare" to 10,
    "transport" to 10
  ).flatMap { (value, weight) -> List(weight) { value } }

  private val merchantsByCategory = mapOf(
    "groceries" to listOf("Walmart", "Whole Foods", "Trader Joes", "Target"),
    "salary" to listOf("Employer Payroll", "Acme Corp", "Globex"),
    "rent" to listOf("Landlord LLC", "City Apartments", "Housing Group"),
    "transfer" to listOf("Bank Transfer", "Peer Transfer", "Internal Transfer"),
    "utilities" to listOf("Electricity Co", "Water Utility", "Internet Provider"),
    "entertainment" to listOf("Netflix", "Spotify", "Cinema City", "Steam"),
    "healthcare" to listOf("City Clinic", "Pharmacy Plus", "Health Center"),
    "transport" to listOf("Uber", "Lyft", "Metro", "Fuel Station")
  )

  private val weightedChannels = listOf(
    "mobile" to 40,
    "web" to 25,
    "pos" to 25,
    "atm" to 10
  ).flatMap { (value, weight) -> List(weight) { value } }

  private val weightedTypes = listOf(
    "debit" to 70,
    "credit" to 30
  ).flatMap { (value, weight) -> List(weight) { value } }

  @Scheduled(fixedDelayString = "\${app.generator.transaction-interval-ms}")
  fun generate() {
    val accountId = Random.nextLong(1, accountIdRange.toLong() + 1)
    val category = weightedCategories.random()
    val channel = weightedChannels.random()
    val transactionType = weightedTypes.random()

    val transaction = Transaction(
      transaction_id = UUID.randomUUID().toString(),
      account_id = accountId,
      amount = randomAmount(category),
      currency = listOf("USD", "EUR", "GBP").random(),
      category = category,
      merchant = merchantsByCategory[category]?.random() ?: "Unknown Merchant",
      channel = channel,
      transaction_type = transactionType,
      timestamp = Instant.now()
    )

    producer.send("banking.transactions", accountId.toString(), transaction)
    log.info(
      "Transaction generated: account={} category={} type={} amount={}",
      accountId,
      category,
      transactionType,
      transaction.amount
    )
  }

  private fun randomAmount(category: String): BigDecimal {
    val range = when (category) {
      "salary" -> 2000.0..8000.0
      "rent" -> 800.0..2500.0
      "groceries" -> 10.0..200.0
      "transfer" -> 50.0..2000.0
      "utilities" -> 40.0..400.0
      "entertainment" -> 15.0..600.0
      "healthcare" -> 20.0..1200.0
      "transport" -> 5.0..250.0
      else -> 1.0..500.0
    }

    return BigDecimal(Random.nextDouble(range.start, range.endInclusive))
      .setScale(2, RoundingMode.HALF_UP)
  }
}
