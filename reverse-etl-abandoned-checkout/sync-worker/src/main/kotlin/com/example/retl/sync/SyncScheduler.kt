package com.example.retl.sync

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SyncScheduler(
    private val syncService: AudienceSyncService,
) {
    private val log = LoggerFactory.getLogger(SyncScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${retl.sync.interval-ms:30000}",
        initialDelayString = "\${retl.sync.initial-delay-ms:20000}",
    )
    fun tick() {
        try {
            syncService.runSync()
        } catch (ex: Exception) {
            log.warn("scheduled sync failed: {}", ex.message)
        }
    }
}
