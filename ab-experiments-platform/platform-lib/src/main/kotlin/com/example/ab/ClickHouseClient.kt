package com.example.ab

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ClickHouseClient(
    baseUrl: String,
    private val user: String,
    private val password: String,
) {
    private val endpoint = baseUrl.trimEnd('/')
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun command(sql: String): String = post(sql)

    fun queryJson(sql: String): String {
        val body = if (sql.contains("FORMAT", ignoreCase = true)) sql else "$sql\nFORMAT JSON"
        return post(body)
    }

    fun insertJsonEachRow(table: String, lines: List<String>) {
        if (lines.isEmpty()) return
        lines.chunked(5_000).forEach { chunk ->
            post("INSERT INTO $table FORMAT JSONEachRow\n" + chunk.joinToString("\n"))
        }
    }

    private fun post(body: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$endpoint/"))
            .timeout(Duration.ofSeconds(120))
            .header("X-ClickHouse-User", user)
            .header("X-ClickHouse-Key", password)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("ClickHouse HTTP ${response.statusCode()}: ${response.body().take(500)}")
        }
        return response.body()
    }
}
