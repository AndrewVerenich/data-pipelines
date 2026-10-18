package com.example.fraud.ml.metrics

import kotlin.math.ln

object Metrics {

  fun rocAuc(yTrue: DoubleArray, yScore: DoubleArray): Double {
    val pairs = yTrue.indices.map { yTrue[it] to yScore[it] }.sortedByDescending { it.second }
    val nPos = yTrue.count { it > 0.5 }.toDouble()
    val nNeg = yTrue.size - nPos
    if (nPos == 0.0 || nNeg == 0.0) return 0.0
    var tp = 0.0
    var fp = 0.0
    var auc = 0.0
    var prevScore = Double.POSITIVE_INFINITY
    var prevTp = 0.0
    var prevFp = 0.0
    for ((label, score) in pairs) {
      if (score != prevScore) {
        auc += trapezoid(fp / nNeg, prevFp / nNeg, tp / nPos, prevTp / nPos)
        prevScore = score
        prevTp = tp
        prevFp = fp
      }
      if (label > 0.5) tp += 1 else fp += 1
    }
    auc += trapezoid(fp / nNeg, prevFp / nNeg, tp / nPos, prevTp / nPos)
    return auc
  }

  private fun trapezoid(x: Double, xPrev: Double, y: Double, yPrev: Double): Double =
    (x - xPrev) * (y + yPrev) / 2.0

  fun averagePrecision(yTrue: DoubleArray, yScore: DoubleArray): Double {
    val pairs = yTrue.indices.map { yTrue[it] to yScore[it] }.sortedByDescending { it.second }
    val nPos = yTrue.count { it > 0.5 }.toDouble()
    if (nPos == 0.0) return 0.0
    var tp = 0.0
    var fp = 0.0
    var ap = 0.0
    var prevRecall = 0.0
    for ((label, _) in pairs) {
      if (label > 0.5) tp += 1 else fp += 1
      val precision = tp / (tp + fp)
      val recall = tp / nPos
      ap += (recall - prevRecall) * precision
      prevRecall = recall
    }
    return ap
  }

  fun recallAtFpr(yTrue: DoubleArray, yScore: DoubleArray, targetFpr: Double = 0.01): Double {
    val order = yScore.indices.sortedByDescending { yScore[it] }
    val nNeg = yTrue.count { it <= 0.5 }.coerceAtLeast(1).toDouble()
    val nPos = yTrue.count { it > 0.5 }.coerceAtLeast(1).toDouble()
    var fp = 0.0
    var tp = 0.0
    var best = 0.0
    for (idx in order) {
      if (yTrue[idx] <= 0.5) fp += 1 else tp += 1
      val fpr = fp / nNeg
      if (fpr <= targetFpr) best = tp / nPos else break
    }
    return best
  }

  @Suppress("unused")
  fun logLoss(yTrue: DoubleArray, yScore: DoubleArray): Double {
    val eps = 1e-15
    return yTrue.indices.map { i ->
      val p = yScore[i].coerceIn(eps, 1 - eps)
      -(yTrue[i] * ln(p) + (1 - yTrue[i]) * ln(1 - p))
    }.average()
  }
}
