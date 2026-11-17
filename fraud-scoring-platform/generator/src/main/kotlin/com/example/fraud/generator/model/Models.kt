package com.example.fraud.generator.model

data class UserProfile(
  val userId: String,
  val email: String,
  val country: String,
  val kycLevel: String,
  var riskTier: String,
  val createdAt: String,
  var updatedAt: String,
  val homeDevices: List<String>,
  val paymentMethods: List<String>,
  val avgAmount: Double
)

data class MerchantProfile(
  val merchantId: String,
  val merchantName: String,
  val mcc: String,
  val country: String,
  val riskScore: Double,
  val createdAt: String
)

data class PaymentTransaction(
  val transaction_id: String,
  val event_ts: String,
  val user_id: String,
  val merchant_id: String,
  val device_id: String,
  val payment_method_id: String,
  val amount: Double,
  val currency: String,
  val channel: String,
  val status: String,
  val ip_country: String,
  val is_3ds: Int,
  val card_bin: String,
  val device_os: String,
  val mcc: String,
  val merchant_country: String
)

data class FraudLabel(
  val transaction_id: String,
  val is_fraud: Int,
  val label_source: String,
  val fraud_pattern: String,
  val labeled_at: String
)

data class UserEvent(
  val user_id: String,
  val email: String,
  val country: String,
  val kyc_level: String,
  val risk_tier: String,
  val created_at: String,
  val updated_at: String
)

data class MerchantEvent(
  val merchant_id: String,
  val merchant_name: String,
  val mcc: String,
  val country: String,
  val risk_score: Double,
  val created_at: String
)
