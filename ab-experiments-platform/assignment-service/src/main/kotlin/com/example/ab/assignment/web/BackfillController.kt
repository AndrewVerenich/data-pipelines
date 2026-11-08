package com.example.ab.assignment.web

import com.example.ab.assignment.service.BackfillService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class BackfillController(private val backfillService: BackfillService) {
  @PostMapping("/backfill")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun backfill() {
    backfillService.ensureDemoBackfill()
  }
}
