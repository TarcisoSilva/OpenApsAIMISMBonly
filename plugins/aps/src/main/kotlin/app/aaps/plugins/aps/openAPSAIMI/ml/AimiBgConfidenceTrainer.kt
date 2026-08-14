package app.aaps.plugins.aps.openAPSAIMI.ml

import android.util.Log
import app.aaps.plugins.aps.openAPSAIMI.AimiNeuralNetwork
import app.aaps.plugins.aps.openAPSAIMI.BgConfidenceGuard
import app.aaps.plugins.aps.openAPSAIMI.TrainingConfig
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * AimiBgConfidenceTrainer — Treinador da rede neural de confiança de leituras de BG.
 *
 * Versão Refatorada V3 (13/Ago/2026):
 *  - Melhoria 3 Fix: Duplicação de peso de amostra realizada estritamente APÓS a divisão de treino e validação (elimina vazamento de dados)
 *  - Melhoria 5 Fix: tryLock() seguro sem Time-of-Check to Time-of-Use race condition
 *  - Bug 1 Fix: Passagem de estatísticas Z-score (means/stds) com inputs padronizados de forma consistente
 *  - Limiares alinhados para alvos 0.0 (OK), 1.0 (UNCERTAIN) e 2.0 (BAD)
 */
object AimiBgConfidenceTrainer {

    private const val TAG = "AimiBgConfidenceTrainer"
    const val INPUT_SIZE = 16

    private val FEATURE_COLUMNS = listOf(
        "bg", "delta", "shortAvgDelta", "longAvgDelta",
        "saltoAnterior", "reversao", "idadeSensorMin", "compressaoAtiva",
        "iob", "cob", "tdd7DaysPerHour", "isNight", "faseSensor",
        "direcaoDivergencia", "emJanelaRefeicao"
    )
    private const val TARGET_COLUMN = "bgConfidenceTarget"

    private const val CB_MAX_FAILURES = 3
    private const val CB_COOLDOWN_MS = 6 * 60 * 60 * 1000L
    private const val TRAIN_INTERVAL_MS = 6 * 60 * 60 * 1000L

    // BUG-8: retreinar somente se houver dados novos suficientes desde o último treino.
    // Antes o treino rodava a cada 6h mesmo sem linhas novas.
    private const val MIN_NEW_ROWS_TO_RETRAIN = 100

    private const val GABARITO_OK_MAX = 15.0
    private const val GABARITO_UNCERTAIN_MAX = 30.0

    private val modelRef = AtomicReference<AimiNeuralNetwork?>(null)
    private val trainMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cbFailures = AtomicInteger(0)
    private val cbCoolingUntilMs = AtomicLong(0L)
    private val lastTrainMs = AtomicLong(0L)
    private val rowsAtLastTrain = AtomicLong(0L)
    private val ultimaCapturaAutomaticaMs = AtomicLong(0L)

    private var modelDir: File? = null

    fun loadModel(dir: File) {
        modelDir = dir
        scope.launch {
            val net = AimiBgModelStore.load(dir, INPUT_SIZE)
            modelRef.set(net)
            if (net != null) {
                Log.i(TAG, "BG confidence model loaded from $dir (${INPUT_SIZE} inputs)")
            } else {
                Log.i(TAG, "No pre-trained BG confidence model — classification inactive (OK fallback)")
            }
        }
    }

    fun classify(
        bg: Double, delta: Double, prevDelta: Double, shortAvgDelta: Double, longAvgDelta: Double,
        saltoAnterior: Double, reversao: Double, idadeSensorMin: Long,
        compressaoAtiva: Double, iob: Double, cob: Double,
        tdd7DaysPerHour: Double, isNight: Double, faseSensor: Double,
        direcaoDivergencia: Double, emJanelaRefeicao: Double
    ): Int {
        val now = System.currentTimeMillis()
        if (isCircuitOpen(now)) return 0

        val model = modelRef.get() ?: return 0

        return try {
            val features = floatArrayOf(
                bg.toFloat(), delta.toFloat(), shortAvgDelta.toFloat(), longAvgDelta.toFloat(),
                saltoAnterior.toFloat(), reversao.toFloat(), idadeSensorMin.toFloat(),
                compressaoAtiva.toFloat(), iob.toFloat(), cob.toFloat(),
                tdd7DaysPerHour.toFloat(), isNight.toFloat(), faseSensor.toFloat(),
                direcaoDivergencia.toFloat(), emJanelaRefeicao.toFloat()
            )
            val trend = computeTrendIndicator(features.getOrElse(1) { 0f }, features.getOrElse(2) { 0f }, features.getOrElse(3) { 0f })
            val enhanced = features.copyOf(INPUT_SIZE).also { it[INPUT_SIZE - 1] = trend }

            val out = model.predict(enhanced)
            val prob = out.firstOrNull() ?: return 0
            if (!prob.isFinite()) return 0

            val rawTier = when {
                prob < 0.5 -> 0   // OK (alvo 0.0)
                prob < 1.5 -> 1   // UNCERTAIN (alvo 1.0)
                else -> 2         // BAD (alvo 2.0)
            }

            BgConfidenceGuard.applySafety(rawTier, bg, delta, prevDelta, shortAvgDelta)
        } catch (e: Exception) {
            recordFailure()
            Log.w(TAG, "classify() exception: ${e.message}")
            0
        }
    }

    fun recordSample(
        csvFile: File,
        bg: Double, delta: Double, shortAvgDelta: Double, longAvgDelta: Double,
        saltoAnterior: Double, reversao: Double, idadeSensorMin: Long,
        compressaoAtiva: Double, iob: Double, cob: Double,
        tdd7DaysPerHour: Double, isNight: Double, faseSensor: Double,
        direcaoDivergencia: Double, emJanelaRefeicao: Double,
        divergenciaDedo: Double
    ) {
        try {
            val target = when {
                divergenciaDedo < GABARITO_OK_MAX -> 0
                divergenciaDedo < GABARITO_UNCERTAIN_MAX -> 1
                delta > 1.0 && divergenciaDedo < 40.0 -> 1
                else -> 2
            }

            val line = listOf(
                bg, delta, shortAvgDelta, longAvgDelta,
                saltoAnterior, reversao, idadeSensorMin.toDouble(), compressaoAtiva,
                iob, cob, tdd7DaysPerHour, isNight, faseSensor,
                direcaoDivergencia, emJanelaRefeicao, target.toDouble(),
                System.currentTimeMillis().toString()
            ).joinToString(",")

            val header = (FEATURE_COLUMNS + TARGET_COLUMN + "dateLong").joinToString(",")

            val writeHeader = !csvFile.exists() || csvFile.length() == 0L
            csvFile.appendText((if (writeHeader) "$header\n" else "") + line + "\n")

            maybeTrainAsync(csvFile)
        } catch (e: Exception) {
            Log.w(TAG, "recordSample() failed: ${e.message}")
        }
    }

    fun capturarSuspensaoAutomatica(
        csvFile: File,
        bgAnterior: Double, bgAtual: Double, bgFutura: Double,
        delta: Double, idadeSensorMin: Long,
        iob: Double, cob: Double, tdd7DaysPerHour: Double,
        isNight: Double, faseSensor: Double, emJanelaRefeicao: Double
    ) {
        try {
            val subiu = bgAtual - bgAnterior > 20.0
            val caiu = bgAnterior - bgAtual > 20.0
            val reverteu = (subiu && (bgFutura < bgAtual - 20.0)) ||
                           (caiu && (bgFutura > bgAtual + 20.0))

            val xdripError = bgAtual <= 39.0
            val sensorNovoOscilando = idadeSensorMin < 6 * 60 && abs(delta) > 10.0

            if (!reverteu && !xdripError && !sensorNovoOscilando) return

            val agora = System.currentTimeMillis()
            if (agora - ultimaCapturaAutomaticaMs.get() < 15 * 60 * 1000L) return
            ultimaCapturaAutomaticaMs.set(agora)

            val divergenciaHeuristica = when {
                xdripError -> 35.0
                reverteu -> 30.0
                else -> 20.0
            }

            recordSample(
                csvFile = csvFile,
                bg = bgAtual, delta = delta,
                shortAvgDelta = 0.0, longAvgDelta = 0.0,
                saltoAnterior = (bgAtual - bgAnterior).coerceAtLeast(0.0),
                reversao = if (reverteu) 1.0 else 0.0,
                idadeSensorMin = idadeSensorMin,
                compressaoAtiva = 0.0,
                iob = iob, cob = cob,
                tdd7DaysPerHour = tdd7DaysPerHour,
                isNight = isNight, faseSensor = faseSensor,
                direcaoDivergencia = 0.0,
                emJanelaRefeicao = emJanelaRefeicao,
                divergenciaDedo = divergenciaHeuristica
            )
            Log.i(TAG, "Captura automática: reversão=$reverteu, xdrip=$xdripError, sensorNovo=$sensorNovoOscilando")
        } catch (e: Exception) {
            Log.w(TAG, "capturarSuspensaoAutomatica() failed: ${e.message}")
        }
    }

    private fun isCircuitOpen(now: Long): Boolean {
        val coolUntil = cbCoolingUntilMs.get()
        if (coolUntil == 0L) return false
        if (now >= coolUntil) {
            cbCoolingUntilMs.set(0L)
            cbFailures.set(0)
            return false
        }
        return true
    }

    private fun recordFailure() {
        val failures = cbFailures.incrementAndGet()
        if (failures >= CB_MAX_FAILURES) {
            cbCoolingUntilMs.set(System.currentTimeMillis() + CB_COOLDOWN_MS)
            Log.w(TAG, "Circuit breaker opened for 6h ($failures consecutive failures)")
        }
    }

    private fun maybeTrainAsync(csvFile: File) {
        val dir = modelDir ?: return
        val now = System.currentTimeMillis()

        if (now - lastTrainMs.get() < TRAIN_INTERVAL_MS) return
        if (isCircuitOpen(now)) return

        scope.launch {
            // Melhoria 5 Fix: tryLock() previne TOCTOU race condition
            if (!trainMutex.tryLock()) return@launch
            try {
                trainNow(dir, csvFile)
            } catch (e: Exception) {
                recordFailure()
                Log.e(TAG, "Training failed: ${e.message}")
            } finally {
                trainMutex.unlock()
            }
        }
    }

    private suspend fun trainNow(dir: File, csvFile: File) {
        if (!csvFile.exists()) return

        val lines = csvFile.readLines()
        if (lines.size < 2) return

        val totalRows = lines.size.toLong()
        val newRows = totalRows - rowsAtLastTrain.get()
        if (newRows < MIN_NEW_ROWS_TO_RETRAIN) {
            Log.d(TAG, "Only $newRows new rows (need $MIN_NEW_ROWS_TO_RETRAIN) — skip training")
            return
        }

        val headers = lines.first().split(",").map { it.trim() }
        val featureIndices = FEATURE_COLUMNS.map { headers.indexOf(it) }
        val targetIndex = headers.indexOf(TARGET_COLUMN)

        if (featureIndices.any { it == -1 } || targetIndex == -1) return

        data class RawSample(val features: FloatArray, val target: Double, val weight: Int)
        val sampleList = mutableListOf<RawSample>()

        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val cols = line.split(",").map { it.trim() }
            if (cols.size <= targetIndex) continue

            val rawFeatures = featureIndices.map { idx -> cols.getOrNull(idx)?.toFloatOrNull() }
            if (rawFeatures.any { it == null }) continue

            val raw = rawFeatures.map { it!! }.toFloatArray()
            val trendIndicator = computeTrendIndicator(raw.getOrElse(1) { 0f }, raw.getOrElse(2) { 0f }, raw.getOrElse(3) { 0f })
            val enhanced = raw.copyOf(raw.size + 1).also { it[raw.size] = trendIndicator }
            val targetVal = cols[targetIndex].toDoubleOrNull() ?: continue

            val peso = when {
                targetVal == 1.0 && raw[1] > 1.0f -> 1
                raw[0] in 70.0f..140.0f && abs(raw[1]) < 2.0f -> 3
                abs(raw[1]) > 5.0f || raw[0] !in 70.0f..140.0f -> 1
                else -> 2
            }
            sampleList.add(RawSample(enhanced, targetVal, peso))
        }

        if (sampleList.size < 10) return

        // 1. Embaralhamento ÚNICO das amostras originais (sem duplicadas) antes do split
        val seed = 42L
        val rng = java.util.Random(seed)
        val shuffledSamples = sampleList.shuffled(rng)

        val splitIdx = (shuffledSamples.size * 0.8).toInt()
        val rawTrainList = shuffledSamples.take(splitIdx)
        val rawValList = shuffledSamples.drop(splitIdx)

        // 2. BUG-4: Média e Desvio Padrão Z-Score calculados APENAS no train split.
        //    Antes usavam o dataset inteiro (leakage de estatísticas da validação).
        val means = DoubleArray(INPUT_SIZE)
        val stds = DoubleArray(INPUT_SIZE)
        val trainSize = rawTrainList.size
        for (i in 0 until INPUT_SIZE) {
            val sum = rawTrainList.sumOf { it.features[i].toDouble() }
            means[i] = sum / trainSize
            val variance = rawTrainList.sumOf { (it.features[i] - means[i]).let { diff -> diff * diff } } / trainSize
            stds[i] = Math.sqrt(variance).coerceAtLeast(1e-4)
        }

        // 3. Melhoria 3 Fix: Duplicação por peso realizada APENAS no grupo de treino!
        val trainInputs = mutableListOf<FloatArray>()
        val trainTargets = mutableListOf<DoubleArray>()
        for (sample in rawTrainList) {
            repeat(sample.weight) {
                trainInputs.add(sample.features)
                trainTargets.add(doubleArrayOf(sample.target))
            }
        }

        // Validação sem duplicadas (distribuição pura e isolada)
        val valInputs = rawValList.map { it.features }
        val valTargets = rawValList.map { doubleArrayOf(it.target) }

        val net = AimiNeuralNetwork(
            inputSize = INPUT_SIZE,
            hiddenSize = 8,
            outputSize = 1,
            config = TrainingConfig(
                learningRate = 0.001,
                epochs = 300,
                useDropout = false
            )
        )

        net.featureMeans = means
        net.featureStds = stds

        net.trainWithValidation(
            trainInputs = trainInputs,
            trainTargets = trainTargets,
            valInputs = valInputs,
            valTargets = valTargets
        )

        val valLoss = net.validate(valInputs, valTargets)
        Log.i(TAG, "BG confidence training complete. Validation loss = $valLoss")

        if (AimiBgModelStore.save(dir, net)) {
            modelRef.set(net)
            rowsAtLastTrain.set(totalRows)
            lastTrainMs.set(System.currentTimeMillis())
            Log.i(TAG, "BG confidence model saved and activated.")
        } else {
            Log.e(TAG, "Model save failed — keeping previous model.")
        }
    }
}
