package app.aaps.plugins.aps.openAPSAIMI.ml

/**
 * Indicador de tendência compartilhado entre treino e inferência.
 *
 * Fonte única (BUG-11): antes duplicado em AimiSmbTrainer, AimiBgConfidenceTrainer
 * e DetermineBasalAdapterAIMI — se os limiares divergissem, treino e produção
 * usariam features diferentes.
 */
fun computeTrendIndicator(delta: Float, shortAvgDelta: Float, longAvgDelta: Float): Float {
    val shortVsLong = shortAvgDelta - longAvgDelta
    return when {
        delta > 2 && shortVsLong > 1 -> 2f    // accelerating up
        delta < -2 && shortVsLong < -1 -> -2f // accelerating down
        delta > 1 -> 1f                       // going up
        delta < -1 -> -1f                     // going down
        else -> 0f                            // stable
    }
}