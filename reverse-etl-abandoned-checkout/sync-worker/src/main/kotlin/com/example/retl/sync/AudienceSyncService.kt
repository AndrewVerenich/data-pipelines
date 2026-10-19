package com.example.retl.sync

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AudienceSyncService(
    private val properties: SyncProperties,
    private val jdbc: JdbcTemplate,
    private val emailClient: EmailClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(AudienceSyncService::class.java)
    private val clickHouse = ClickHouseClient(
        properties.clickhouseUrl,
        properties.clickhouseUser,
        properties.clickhousePassword,
    )

    @Synchronized
    fun runSync(): SyncResult {
        val started = Instant.now()
        val runId = jdbc.queryForObject(
            """
            INSERT INTO sync_runs (started_at, status)
            VALUES (NOW(), 'running')
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        ) ?: error("failed to create sync_run")

        return try {
            val desired = loadDesired()
            val current = loadCurrent()
            val changes = diff(desired, current)
            val lagSec = computeLagSec(desired)
            var delivered = 0
            var deadLetters = 0

            changes.chunked(properties.batchSize).forEach { batch ->
                batch.forEach { change ->
                    when (deliverOne(change, runId)) {
                        DeliveryOutcome.OK -> delivered++
                        DeliveryOutcome.DEAD_LETTER -> deadLetters++
                        DeliveryOutcome.SKIPPED -> Unit
                    }
                }
            }

            val result = SyncResult(
                enters = changes.count { it.action == SyncAction.ENTER },
                leaves = changes.count { it.action == SyncAction.LEAVE },
                updates = changes.count { it.action == SyncAction.UPDATE },
                delivered = delivered,
                deadLetters = deadLetters,
                lagSec = lagSec,
            )
            jdbc.update(
                """
                UPDATE sync_runs
                SET finished_at = NOW(),
                    status = 'success',
                    enters = ?, leaves = ?, updates = ?,
                    delivered = ?, dead_letters = ?, lag_sec = ?
                WHERE id = ?
                """.trimIndent(),
                result.enters,
                result.leaves,
                result.updates,
                result.delivered,
                result.deadLetters,
                result.lagSec,
                runId,
            )
            log.info(
                "sync#{} enter={} leave={} update={} delivered={} dlq={} lag={}s duration={}ms",
                runId,
                result.enters,
                result.leaves,
                result.updates,
                result.delivered,
                result.deadLetters,
                result.lagSec,
                Instant.now().toEpochMilli() - started.toEpochMilli(),
            )
            result
        } catch (ex: Exception) {
            jdbc.update(
                "UPDATE sync_runs SET finished_at = NOW(), status = 'failed', error = ? WHERE id = ?",
                ex.message?.take(500),
                runId,
            )
            throw ex
        }
    }

    private fun loadDesired(): Map<String, DesiredMember> {
        val body = clickHouse.queryJson(
            """
            SELECT
                user_id,
                email,
                cart_value,
                version,
                toString(checkout_at) AS checkout_at
            FROM ${properties.martTable}
            """.trimIndent(),
        )
        val data = objectMapper.readTree(body).path("data")
        val out = linkedMapOf<String, DesiredMember>()
        data.forEach { row ->
            val userId = row.text("user_id")
            out[userId] = DesiredMember(
                userId = userId,
                email = row.text("email"),
                cartValue = row.num("cart_value"),
                version = row.text("version"),
                checkoutAt = row.text("checkout_at"),
            )
        }
        return out
    }

    private fun loadCurrent(): Map<String, CurrentMember> {
        return jdbc.query(
            """
            SELECT user_id, email, cart_value, version
            FROM membership_snapshot
            WHERE audience = ? AND status IN ('active', 'rejected')
            """.trimIndent(),
            { rs, _ ->
                CurrentMember(
                    userId = rs.getString("user_id"),
                    email = rs.getString("email"),
                    cartValue = rs.getDouble("cart_value"),
                    version = rs.getString("version"),
                )
            },
            properties.audience,
        ).associateBy { it.userId }
    }

    private fun diff(
        desired: Map<String, DesiredMember>,
        current: Map<String, CurrentMember>,
    ): List<SyncChange> {
        val changes = mutableListOf<SyncChange>()
        desired.forEach { (userId, d) ->
            val c = current[userId]
            when {
                c == null -> changes += SyncChange(SyncAction.ENTER, d.userId, d.email, d.cartValue, d.version)
                c.version != d.version -> changes += SyncChange(SyncAction.UPDATE, d.userId, d.email, d.cartValue, d.version)
            }
        }
        current.keys.filter { it !in desired }.forEach { userId ->
            val c = current.getValue(userId)
            changes += SyncChange(SyncAction.LEAVE, c.userId, c.email, c.cartValue, c.version)
        }
        return changes.sortedBy { it.userId }
    }

    private fun deliverOne(change: SyncChange, runId: Long): DeliveryOutcome {
        val key = "${change.userId}:${properties.audience}:${change.action.name.lowercase()}:${change.version}"
        val already = jdbc.queryForObject(
            "SELECT COUNT(*) FROM delivery_log WHERE idempotency_key = ? AND status = 'delivered'",
            Int::class.java,
            key,
        ) ?: 0
        if (already > 0) {
            applyMembership(change)
            return DeliveryOutcome.SKIPPED
        }

        val result = try {
            emailClient.deliver(change, properties.audience, key)
        } catch (ex: Exception) {
            writeDeadLetter(change, key, 0, ex.message ?: "transport error")
            writeDeliveryLog(change, key, runId, "failed", 0, ex.message)
            return DeliveryOutcome.DEAD_LETTER
        }

        return when (result.httpStatus) {
            in 200..299 -> {
                writeDeliveryLog(change, key, runId, "delivered", result.httpStatus, result.body.take(200))
                applyMembership(change)
                DeliveryOutcome.OK
            }
            422 -> {
                // Permanent reject (bad email): park membership so the next diff does not re-enter forever.
                writeDeadLetter(change, key, result.httpStatus, result.body.take(300))
                writeDeliveryLog(change, key, runId, "dead_letter", result.httpStatus, result.body.take(200))
                parkRejected(change)
                DeliveryOutcome.DEAD_LETTER
            }
            429 -> {
                writeDeadLetter(change, key, result.httpStatus, result.body.take(300))
                writeDeliveryLog(change, key, runId, "dead_letter", result.httpStatus, result.body.take(200))
                DeliveryOutcome.DEAD_LETTER
            }
            else -> {
                writeDeadLetter(change, key, result.httpStatus, result.body.take(300))
                writeDeliveryLog(change, key, runId, "failed", result.httpStatus, result.body.take(200))
                DeliveryOutcome.DEAD_LETTER
            }
        }
    }

    private fun applyMembership(change: SyncChange) {
        when (change.action) {
            SyncAction.ENTER, SyncAction.UPDATE -> {
                jdbc.update(
                    """
                    INSERT INTO membership_snapshot (user_id, audience, email, cart_value, version, status, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'active', NOW())
                    ON CONFLICT (user_id, audience) DO UPDATE SET
                        email = EXCLUDED.email,
                        cart_value = EXCLUDED.cart_value,
                        version = EXCLUDED.version,
                        status = 'active',
                        updated_at = NOW()
                    """.trimIndent(),
                    change.userId,
                    properties.audience,
                    change.email,
                    change.cartValue,
                    change.version,
                )
            }
            SyncAction.LEAVE -> {
                jdbc.update(
                    """
                    UPDATE membership_snapshot
                    SET status = 'left', updated_at = NOW()
                    WHERE user_id = ? AND audience = ?
                    """.trimIndent(),
                    change.userId,
                    properties.audience,
                )
            }
        }
    }

    private fun parkRejected(change: SyncChange) {
        jdbc.update(
            """
            INSERT INTO membership_snapshot (user_id, audience, email, cart_value, version, status, updated_at)
            VALUES (?, ?, ?, ?, ?, 'rejected', NOW())
            ON CONFLICT (user_id, audience) DO UPDATE SET
                email = EXCLUDED.email,
                cart_value = EXCLUDED.cart_value,
                version = EXCLUDED.version,
                status = 'rejected',
                updated_at = NOW()
            """.trimIndent(),
            change.userId,
            properties.audience,
            change.email,
            change.cartValue,
            change.version,
        )
    }

    private fun writeDeliveryLog(
        change: SyncChange,
        key: String,
        runId: Long,
        status: String,
        httpStatus: Int,
        detail: String?,
    ) {
        jdbc.update(
            """
            INSERT INTO delivery_log (run_id, user_id, audience, action, version, idempotency_key, status, http_status, detail)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO UPDATE SET
                status = EXCLUDED.status,
                http_status = EXCLUDED.http_status,
                detail = EXCLUDED.detail,
                created_at = NOW()
            """.trimIndent(),
            runId,
            change.userId,
            properties.audience,
            change.action.name.lowercase(),
            change.version,
            key,
            status,
            httpStatus,
            detail,
        )
    }

    private fun writeDeadLetter(change: SyncChange, key: String, httpStatus: Int, error: String) {
        jdbc.update(
            """
            INSERT INTO dead_letter (user_id, audience, action, version, idempotency_key, http_status, error, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (idempotency_key) DO UPDATE SET
                http_status = EXCLUDED.http_status,
                error = EXCLUDED.error,
                created_at = NOW(),
                resolved = FALSE
            """.trimIndent(),
            change.userId,
            properties.audience,
            change.action.name.lowercase(),
            change.version,
            key,
            httpStatus,
            error,
            objectMapper.writeValueAsString(
                mapOf(
                    "email" to change.email,
                    "cartValue" to change.cartValue,
                ),
            ),
        )
    }

    private fun computeLagSec(desired: Map<String, DesiredMember>): Long {
        if (desired.isEmpty()) return 0
        val latestCheckout = desired.values.maxOfOrNull { it.checkoutAt } ?: return 0
        return try {
            val ts = Instant.parse(latestCheckout.replace(' ', 'T') + "Z")
            ((Instant.now().epochSecond - ts.epochSecond).coerceAtLeast(0))
        } catch (_: Exception) {
            0
        }
    }

    private fun JsonNode.text(field: String): String = path(field).asText()
    private fun JsonNode.num(field: String): Double = path(field).asDouble()

    private enum class DeliveryOutcome { OK, DEAD_LETTER, SKIPPED }
}
