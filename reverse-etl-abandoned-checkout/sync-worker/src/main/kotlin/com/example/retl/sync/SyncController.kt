package com.example.retl.sync

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sync")
class SyncController(
    private val syncService: AudienceSyncService,
) {
    @PostMapping("/run")
    fun run(): ResponseEntity<SyncResult> {
        return ResponseEntity.ok(syncService.runSync())
    }
}
