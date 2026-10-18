package com.example.fraud.generator.service

import com.example.fraud.generator.model.FraudLabel
import com.example.fraud.generator.model.MerchantEvent
import com.example.fraud.generator.model.MerchantProfile
import com.example.fraud.generator.model.PaymentTransaction
import com.example.fraud.generator.model.UserEvent
import com.example.fraud.generator.model.UserProfile
import com.example.fraud.generator.producer.EventProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.random.Random

@Component
class PaymentTrafficService(
  private val producer: EventProducer,
  @Value("\${app.backfill-days:14}") private val backfillDays: Int,
  @Value("\${app.backfill-tx:12000}") private val backfillTx: Int,
  @Value("\${app.fraud-rate:0.04}") private val fraudRate: Double,
  @Value("\${app.num-users:800}") private val numUsers: Int,
  @Value("\${app.num-merchants:120}") private val numMerchants: Int
) : ApplicationRunner {

  private val log = LoggerFactory.getLogger(javaClass)
  private val ready = AtomicBoolean(false)
  private val labelQueue = ConcurrentLinkedQueue<Pair<Instant, FraudLabel>>()
  private lateinit var users: List<UserProfile>
  private lateinit var merchants: List<MerchantProfile>
  private val random = Random(42)

  private val countries = listOf("US", "GB", "DE", "FR", "PL", "ES", "IT", "NL", "CA", "AU")
  private val channels = listOf("web", "mobile", "pos", "api")
  private val deviceOs = listOf("ios", "android", "windows", "macos", "linux")
  private val currencies = listOf("USD", "EUR", "GBP", "PLN")
  private val mccCatalog = listOf(
    Triple("5411", "Grocery", 0.15),
    Triple("5812", "Restaurants", 0.20),
    Triple("4111", "Transport", 0.18),
    Triple("5999", "Misc Retail", 0.35),
    Triple("6011", "ATM", 0.40),
    Triple("7995", "Gambling", 0.75),
    Triple("5967", "Adult Digital", 0.80),
    Triple("5732", "Electronics", 0.30),
    Triple("7011", "Hotels", 0.25),
    Triple("4814", "Telecom", 0.22)
  )
  private val fraudPatterns = listOf(
    "velocity", "amount_outlier", "geo_jump", "new_device_high_amount",
    "merchant_mule", "card_testing", "account_takeover"
  )

  override fun run(args: ApplicationArguments?) {
    users = buildUsers()
    merchants = buildMerchants()
    publishDims()
    backfill()
    ready.set(true)
    log.info("Generator ready: users={}, merchants={}, streaming live traffic", users.size, merchants.size)
  }

  @Scheduled(fixedDelayString = "\${app.stream-interval-ms:400}", initialDelay = 1000)
  fun streamLive() {
    if (!ready.get()) return
    val now = Instant.now()
    val sample = if (random.nextDouble() < fraudRate) makeFraudTx(now) else makeLegitTx(now)
    emitTx(sample.tx, sample.isFraud, sample.pattern, now)
    flushDueLabels(now)
  }

  private data class TxSample(val tx: PaymentTransaction, val isFraud: Int, val pattern: String)

  private fun iso(ts: Instant): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .withZone(ZoneOffset.UTC)
      .format(ts)

  private fun buildUsers(): List<UserProfile> {
    val base = Instant.now().minusSeconds((backfillDays + 30L) * 86400)
    val list = (0 until numUsers).map { i ->
      val country = countries.random(random)
      val created = base.plusSeconds(random.nextLong(0, 40L * 86400))
      val risk = listOf("low", "medium", "high").random(random).let {
        // weighted
        val r = random.nextDouble()
        when {
          r < 0.70 -> "low"
          r < 0.95 -> "medium"
          else -> "high"
        }
      }
      UserProfile(
        userId = "U%05d".format(i),
        email = "user$i@example.com",
        country = country,
        kycLevel = listOf("basic", "standard", "enhanced").random(random),
        riskTier = risk,
        createdAt = iso(created),
        updatedAt = iso(created),
        homeDevices = (0 until random.nextInt(1, 4)).map { j -> "D%05d_%d".format(i, j) },
        paymentMethods = (0 until random.nextInt(1, 3)).map { j -> "PM%05d_%d".format(i, j) },
        avgAmount = if (risk == "high") random.nextDouble(80.0, 300.0) else random.nextDouble(20.0, 120.0)
      )
    }.toMutableList()
    list.shuffled(random).take((numUsers / 20).coerceAtLeast(1)).forEach { u ->
      u.updatedAt = iso(Instant.now().minusSeconds(random.nextLong(1, 8) * 86400))
      u.riskTier = if (u.riskTier != "high") "high" else "medium"
    }
    return list
  }

  private fun buildMerchants(): List<MerchantProfile> {
    val base = Instant.now().minusSeconds((backfillDays + 60L) * 86400)
    return (0 until numMerchants).map { i ->
      val (mcc, prefix, risk) = mccCatalog.random(random)
      MerchantProfile(
        merchantId = "M%04d".format(i),
        merchantName = "$prefix Merchant-$i",
        mcc = mcc,
        country = countries.random(random),
        riskScore = (risk + random.nextDouble(-0.05, 0.05)).coerceIn(0.0, 1.0),
        createdAt = iso(base.plusSeconds(random.nextLong(0, 20L * 86400)))
      )
    }
  }

  private fun publishDims() {
    users.forEach { u ->
      producer.send(
        "payments.users",
        u.userId,
        UserEvent(u.userId, u.email, u.country, u.kycLevel, u.riskTier, u.createdAt, u.updatedAt)
      )
    }
    merchants.forEach { m ->
      producer.send(
        "payments.merchants",
        m.merchantId,
        MerchantEvent(m.merchantId, m.merchantName, m.mcc, m.country, m.riskScore, m.createdAt)
      )
    }
    log.info("Published {} users and {} merchants", users.size, merchants.size)
  }

  private fun makeLegitTx(eventTs: Instant): TxSample {
    val user = users.random(random)
    val merchant = merchants.random(random)
    val amount = abs(random.nextDouble(user.avgAmount * 0.3, user.avgAmount * 1.7)).coerceAtLeast(1.0)
    val tx = PaymentTransaction(
      transaction_id = UUID.randomUUID().toString(),
      event_ts = iso(eventTs),
      user_id = user.userId,
      merchant_id = merchant.merchantId,
      device_id = user.homeDevices.random(random),
      payment_method_id = user.paymentMethods.random(random),
      amount = "%.2f".format(amount).toDouble(),
      currency = currencies.random(random),
      channel = channels.random(random),
      status = if (random.nextDouble() < 0.94) "approved" else "declined",
      ip_country = if (random.nextDouble() > 0.05) user.country else countries.random(random),
      is_3ds = if (random.nextDouble() < 0.35) 1 else 0,
      card_bin = random.nextInt(400000, 599999).toString(),
      device_os = deviceOs.random(random),
      mcc = merchant.mcc,
      merchant_country = merchant.country
    )
    return TxSample(tx, 0, "")
  }

  private fun makeFraudTx(eventTs: Instant, pattern: String = fraudPatterns.random(random)): TxSample {
    val user = users.random(random)
    val highRisk = merchants.filter { it.riskScore >= 0.6 }.ifEmpty { merchants }
    val merchant = if (pattern == "merchant_mule") highRisk.random(random) else merchants.random(random)
    val base = makeLegitTx(eventTs).tx
    var amount = base.amount
    var deviceId = base.device_id
    var ipCountry = base.ip_country
    var channel = base.channel
    var is3ds = base.is_3ds

    when (pattern) {
      "velocity" -> {
        deviceId = "D_VEL_${user.userId}"
        amount = random.nextDouble(5.0, 40.0)
      }
      "amount_outlier" -> {
        amount = user.avgAmount * random.nextDouble(8.0, 20.0)
        channel = "web"
      }
      "geo_jump" -> {
        ipCountry = countries.filter { it != user.country }.random(random)
        amount = user.avgAmount * random.nextDouble(2.0, 5.0)
      }
      "new_device_high_amount" -> {
        deviceId = "D_NEW_${UUID.randomUUID().toString().take(8)}"
        amount = random.nextDouble(400.0, 1800.0)
        is3ds = 0
      }
      "merchant_mule" -> {
        amount = random.nextDouble(200.0, 900.0)
        channel = "api"
      }
      "card_testing" -> {
        amount = random.nextDouble(0.5, 3.5)
        channel = "api"
        is3ds = 0
      }
      "account_takeover" -> {
        deviceId = "D_ATO_${UUID.randomUUID().toString().take(8)}"
        ipCountry = countries.filter { it != user.country }.random(random)
        amount = user.avgAmount * random.nextDouble(4.0, 10.0)
        channel = "web"
      }
    }

    val tx = base.copy(
      user_id = user.userId,
      merchant_id = merchant.merchantId,
      mcc = merchant.mcc,
      merchant_country = merchant.country,
      payment_method_id = user.paymentMethods.random(random),
      device_id = deviceId,
      amount = "%.2f".format(amount).toDouble(),
      ip_country = ipCountry,
      channel = channel,
      is_3ds = is3ds,
      status = "approved",
      event_ts = iso(eventTs)
    )
    return TxSample(tx, 1, pattern)
  }

  private fun emitTx(tx: PaymentTransaction, isFraud: Int, pattern: String, eventTs: Instant) {
    producer.send("payments.transactions", tx.user_id, tx)
    if (isFraud == 1) {
      if (random.nextDouble() < 0.4) {
        producer.send(
          "payments.labels",
          tx.transaction_id,
          FraudLabel(tx.transaction_id, 1, "manual_review", pattern, iso(eventTs.plusSeconds(300)))
        )
      } else {
        val delayH = random.nextLong(6, 73)
        val labeledAt = eventTs.plusSeconds(delayH * 3600)
        labelQueue.add(
          labeledAt to FraudLabel(tx.transaction_id, 1, "chargeback", pattern, iso(labeledAt))
        )
      }
    } else {
      when {
        random.nextDouble() < 0.15 -> producer.send(
          "payments.labels",
          tx.transaction_id,
          FraudLabel(tx.transaction_id, 0, "auto_clear", "", iso(eventTs.plusSeconds(120)))
        )
        random.nextDouble() < 0.5 -> {
          val delayH = random.nextLong(12, 97)
          val labeledAt = eventTs.plusSeconds(delayH * 3600)
          labelQueue.add(
            labeledAt to FraudLabel(tx.transaction_id, 0, "auto_clear", "", iso(labeledAt))
          )
        }
      }
    }
  }

  private fun flushDueLabels(asOf: Instant) {
    while (true) {
      val head = labelQueue.peek() ?: break
      if (head.first.isAfter(asOf)) break
      val item = labelQueue.poll() ?: break
      producer.send("payments.labels", item.second.transaction_id, item.second)
    }
  }

  private fun backfill() {
    log.info("Backfilling {} transactions over {} days...", backfillTx, backfillDays)
    val start = Instant.now().minusSeconds(backfillDays.toLong() * 86400)
    repeat(40) {
      val clusterTs = start.plusSeconds(random.nextLong(0, backfillDays.toLong() * 86400 - 3600))
      val pattern = listOf("velocity", "card_testing").random(random)
      repeat(random.nextInt(8, 19)) { j ->
        val ts = clusterTs.plusSeconds(j * 12L)
        val sample = makeFraudTx(ts, pattern)
        emitTx(sample.tx, sample.isFraud, sample.pattern, ts)
      }
    }
    repeat(backfillTx) { i ->
      val eventTs = start.plusSeconds(random.nextLong(0, backfillDays.toLong() * 86400))
      val sample = if (random.nextDouble() < fraudRate) makeFraudTx(eventTs) else makeLegitTx(eventTs)
      emitTx(sample.tx, sample.isFraud, sample.pattern, eventTs)
      if (i % 2000 == 0) {
        flushDueLabels(eventTs)
        log.info("  backfill progress {}/{}", i, backfillTx)
      }
    }
    flushDueLabels(Instant.now())
    log.info("Backfill complete")
  }
}
