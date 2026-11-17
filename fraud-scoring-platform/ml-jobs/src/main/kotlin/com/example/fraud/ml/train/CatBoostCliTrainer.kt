package com.example.fraud.ml.train

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

@Component
class CatBoostCliTrainer(
  @Value("\${app.catboost-bin:catboost}") private val catboostBin: String,
  @Value("\${app.catboost-iterations:200}") private val iterations: Int
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fit(
    workDir: Path,
    learnTsv: Path,
    testTsv: Path,
    columnDesc: Path,
    modelOut: Path,
    catFeatureIndices: List<Int>
  ) {
    Files.createDirectories(workDir)
    val cmd = mutableListOf(
      catboostBin,
      "fit",
      "--learn-set", learnTsv.toString(),
      "--test-set", testTsv.toString(),
      "--column-description", columnDesc.toString(),
      "--loss-function", "Logloss",
      "--eval-metric", "AUC",
      "--iterations", iterations.toString(),
      "--depth", "6",
      "--learning-rate", "0.08",
      "--auto-class-weights", "Balanced",
      "--random-seed", "42",
      "--od-type", "Iter",
      "--od-wait", "30",
      "--allow-writing-files", "false",
      "--model-file", modelOut.toString()
    )
    if (catFeatureIndices.isNotEmpty()) {
      // indices already encoded in column description; keep for clarity in logs
      log.info("Training with categorical columns indices={}", catFeatureIndices)
    }
    log.info("Running: {}", cmd.joinToString(" "))
    val process = ProcessBuilder(cmd)
      .directory(workDir.toFile())
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    workDir.resolve("catboost-fit.log").writeText(output)
    if (code != 0) {
      throw IllegalStateException("catboost fit failed code=$code\n$output")
    }
    log.info("CatBoost model written to {}", modelOut)
  }

  fun writeColumnDescription(path: Path, numericCount: Int, categoricalCount: Int) {
    val lines = mutableListOf("0\tLabel")
    var idx = 1
    repeat(numericCount) {
      lines += "$idx\tNum"
      idx++
    }
    repeat(categoricalCount) {
      lines += "$idx\tCateg"
      idx++
    }
    path.writeText(lines.joinToString("\n") + "\n")
  }
}
