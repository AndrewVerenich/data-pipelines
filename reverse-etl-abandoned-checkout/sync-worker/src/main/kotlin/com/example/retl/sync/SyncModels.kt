package com.example.retl.sync

data class DesiredMember(
    val userId: String,
    val email: String,
    val cartValue: Double,
    val version: String,
    val checkoutAt: String,
)

data class CurrentMember(
    val userId: String,
    val email: String,
    val cartValue: Double,
    val version: String,
)

enum class SyncAction {
    ENTER,
    LEAVE,
    UPDATE,
}

data class SyncChange(
    val action: SyncAction,
    val userId: String,
    val email: String,
    val cartValue: Double,
    val version: String,
)

data class SyncResult(
    val enters: Int,
    val leaves: Int,
    val updates: Int,
    val delivered: Int,
    val deadLetters: Int,
    val lagSec: Long,
)
