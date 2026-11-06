package com.example.fraud.ml

import com.example.fraud.ml.score.ScoreBatchJob
import com.example.fraud.ml.train.TrainJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

@SpringBootApplication
class MlJobsApplication

fun main(args: Array<String>) {
  runApplication<MlJobsApplication>(*args)
}

@Component
class ModeRunner(
  private val trainJob: TrainJob,
  private val scoreBatchJob: ScoreBatchJob,
  @Value("\${app.mode:train}") private val mode: String
) : ApplicationRunner {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun run(args: ApplicationArguments?) {
    val resolved = when {
      args?.nonOptionArgs?.isNotEmpty() == true -> args.nonOptionArgs.first()
      else -> mode
    }
    log.info("Running ml-jobs mode={}", resolved)
    when (resolved.lowercase()) {
      "train" -> trainJob.run()
      "score-batch", "score_batch", "score" -> scoreBatchJob.run()
      else -> throw IllegalArgumentException("Unknown mode: $resolved (expected train|score-batch)")
    }
    exitProcess(0)
  }
}
