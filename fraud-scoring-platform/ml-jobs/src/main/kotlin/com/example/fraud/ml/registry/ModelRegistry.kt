package com.example.fraud.ml.registry

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

data class LatestPointer(
  val model_version: String,
  val promoted: Boolean,
  val auc: Double,
  val path: String
)

@Component
class ModelRegistry(
  @Value("\${app.model-root}") private val modelRoot: String,
  private val mapper: ObjectMapper = jacksonObjectMapper()
) {

  private val root: Path get() = Path.of(modelRoot)

  fun ensureRoot() {
    root.createDirectories()
    root.resolve("latest").createDirectories()
  }

  fun versionDir(version: String): Path = root.resolve(version).also { it.createDirectories() }

  fun writeJson(path: Path, value: Any) {
    path.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
  }

  fun promote(versionDir: Path, latest: LatestPointer) {
    val latestDir = root.resolve("latest")
    if (latestDir.exists()) {
      latestDir.toFile().listFiles()?.forEach { it.deleteRecursively() }
    } else {
      latestDir.createDirectories()
    }
    Files.walk(versionDir).use { stream ->
      stream.filter { Files.isRegularFile(it) }.forEach { src ->
        val target = latestDir.resolve(versionDir.relativize(src).toString())
        target.parent?.createDirectories()
        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    writeJson(root.resolve("latest.json"), latest)
  }

  fun latestModelPath(): Path = root.resolve("latest").resolve("model.cbm")

  fun readLatest(): LatestPointer =
    mapper.readValue(root.resolve("latest.json").toFile(), LatestPointer::class.java)

  fun readFeatureList(): Map<*, *> =
    mapper.readValue(root.resolve("latest").resolve("feature_list.json").toFile(), Map::class.java)
}
