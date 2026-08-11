package app.aaps.plugins.aps.openAPSAIMI.ml

import android.util.Log
import app.aaps.plugins.aps.openAPSAIMI.AimiNeuralNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.min

/**
 * AimiSmbTrainer — Singleton managing the ML model lifecycle for SMB refinement.
 *
 * Safety contracts:
 *  - refine() is always O(1): fallback to predictedSmb on any error
 *  - training runs on Dispatchers.IO, never on the hot-path thread
 *  - circuit breaker disables ML for 6h after 3 consecutive failures
 *  - ML correction is clamped to ±min(0.05U, 25% of predictedSmb)
 */
object AimiSmbTrainer {

    private const val TAG = "AimiSmbTrainer"

    /** Input dimension: 10 physio features + 1 trendIndicator = 11 */
    const val INPUT_SIZE = 11

    /** Feature column names in the CSV (must match full oapsaimi_records.csv header) */
    private val FEATURE_COLUMNS = listOf(
        "bg", "iob", "cob", "delta", "shortAvgDelta", "longAvgDelta",
        "tdd7DaysPerHour", "tdd2DaysPerHour", "tddPerHour", "tdd24HrsPerHour"
    )
    private const val TARGET_COLUMN = "smbGiven"

    // Circuit breaker
    private const val CB_MAX_FAILURES = 3
    private const val CB_COOLDOWN_MS = 6 * 60 * 60 * 1000L  // 6h

    // Training rate limit
    private const val TRAIN_INTERVAL_MS = 6 * 60 * 60 * 1000L  // 6h
    private const val MIN_NEW_ROWS_TO_RETRAIN = 200

    // ---- State ---------------------------------------------------------------
    private val modelRef = AtomicReference<AimiNeuralNetwork?>(null)
    private val trainMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Circuit breaker state
    private val cbFailures = AtomicInteger(0)
    private val cbCoolingUntilMs = AtomicLong(0L)

    // Training rate limit state
    private val lastTrainMs = AtomicLong(0L)
    private val rowsAtLastTrain = AtomicLong(0L)

    // Model directory (lazily set)
    private var modelDir: File? = null

    /** Model directory — used by [loadModel] and [maybeTrainAsync]. */
    private val resolvedModelDir: File?
        get() = modelDir

    // ---- Public API ----------------------------------------------------------

    /**
     * Load previously saved model from disk.
     * Call this once from plugin initialization (e.g., setData).
     */
    fun loadModel(dir: File) {
        modelDir = dir
        scope.launch {
            val net = AimiSmbModelStore.load(dir, INPUT_SIZE)
            modelRef.set(net)
            if (net != null) {
                Log.i(TAG, "ML model loaded from $dir (${INPUT_SIZE} inputs)")
            } else {
                Log.i(TAG, "No pre-trained ML model found — refinement inactive until first training")
            }
        }
    }

    /**
     * Fire-and-forget training trigger.
     * Respects rate limit (6h) and minimum new-rows requirement (200).
     * Never blocks the caller.
     */
    fun maybeTrainAsync(csvFile: File) {
        val dir = resolvedModelDir ?: return
        val now = System.currentTimeMillis()

        // Fast path: rate limit guard
        if (now - lastTrainMs.get() < TRAIN_INTERVAL_MS) return
        // Circuit breaker guard
        if (isCircuitOpen(now)) return

        scope.launch {
            if (trainMutex.isLocked) return@launch
            trainMutex.withLock {
                try {
                    trainNow(dir, csvFile)
                } catch (e: Exception) {
                    recordFailure()
                    Log.e(TAG, "Training failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Refine [predictedSmb] using the in-memory model.
     *
     * Features order (11 elements):
     *   [0]=bg, [1]=iob, [2]=cob, [3]=delta,
     *   [4]=shortAvgDelta, [5]=longAvgDelta,
     *   [6]=tdd7DaysPerHour, [7]=tdd2DaysPerHour,
     *   [8]=tddPerHour, [9]=tdd24HrsPerHour,
     *   [10]=trendIndicator (computed from delta/short/long)
     *
     * - Returns [predictedSmb] unchanged if model is null, circuit is open,
     *   or any exception is thrown.
     * - Clamps ML correction to ±min(0.05U, 25% of predictedSmb).
     */
    fun refine(predictedSmb: Float, features: FloatArray): Float {
        if (features.size != INPUT_SIZE) return predictedSmb

        val now = System.currentTimeMillis()
        if (isCircuitOpen(now)) return predictedSmb

        val model = modelRef.get() ?: return predictedSmb

        return try {
            val out = model.predict(features)
            val mlOut = out.firstOrNull()?.toFloat() ?: return predictedSmb
            if (!mlOut.isFinite()) return predictedSmb

            // Clamp: max correction is the smaller of 0.05U or 25% of predictedSmb
            val maxDelta = min(0.05f, predictedSmb * 0.25f).coerceAtLeast(0f)
            val delta = (mlOut - predictedSmb).coerceIn(-maxDelta, maxDelta)
            val refined = predictedSmb + delta

            if (!refined.isFinite() || refined < 0f) predictedSmb else refined
        } catch (e: Exception) {
            recordFailure()
            Log.w(TAG, "refine() exception: ${e.message}")
            predictedSmb
        }
    }

    // ---- Internal: Circuit breaker ------------------------------------------

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

    // ---- Internal: Training -------------------------------------------------

    private fun computeTrendIndicator(features: FloatArray): Float {
        val delta = features.getOrElse(3) { 0f }
        val shortAvg = features.getOrElse(4) { 0f }
        val longAvg = features.getOrElse(5) { 0f }
        val shortVsLong = shortAvg - longAvg
        return when {
            delta > 2 && shortVsLong > 1 -> 2f    // accelerating up
            delta < -2 && shortVsLong < -1 -> -2f // accelerating down
            delta > 1 -> 1f                       // going up
            delta < -1 -> -1f                     // going down
            else -> 0f                            // stable
        }
    }

    private suspend fun trainNow(dir: File, csvFile: File) {
        if (!csvFile.exists()) {
            Log.d(TAG, "CSV not found — skip training")
            return
        }

        // Ler CSV atual + backup (.bak) se existir, preservando o histórico
        val currentLines = csvFile.readLines()
        val backupFile = File(csvFile.absolutePath + ".bak")
        val backupLines = if (backupFile.exists()) backupFile.readLines() else emptyList()

        // Headers e data lines: usa headers do CSV atual (mais recente)
        val headers = currentLines.firstOrNull()?.split(",")?.map { it.trim() }
            ?: backupLines.firstOrNull()?.split(",")?.map { it.trim() }
            ?: return

        // Ordem cronológica: backup (mais antigo) + current (mais recente)
        val currentData = currentLines.drop(1).filter { it.isNotBlank() }
        val backupData = backupLines.drop(1).filter { it.isNotBlank() }
        val dataLines = backupData + currentData

        val totalRows = dataLines.size.toLong()

        val newRows = totalRows - rowsAtLastTrain.get()
        if (newRows < MIN_NEW_ROWS_TO_RETRAIN) {
            Log.d(TAG, "Only $newRows new rows (need $MIN_NEW_ROWS_TO_RETRAIN) — skip training")
            return
        }

        // Find feature column indices
        val featureIndices = FEATURE_COLUMNS.map { headers.indexOf(it) }
        val targetIndex = headers.indexOf(TARGET_COLUMN)

        if (featureIndices.any { it == -1 } || targetIndex == -1) {
            Log.w(TAG, "CSV missing required columns — expected $FEATURE_COLUMNS and $TARGET_COLUMN")
            return
        }

        // Parse CSV rows into training data
        val inputs = mutableListOf<FloatArray>()
        val targets = mutableListOf<DoubleArray>()

        for (line in dataLines) {
            val cols = line.split(",").map { it.trim() }
            if (cols.size <= targetIndex) continue

            val rawFeatures = featureIndices.map { idx -> cols.getOrNull(idx)?.toFloatOrNull() }
            if (rawFeatures.any { it == null }) continue

            val raw = rawFeatures.map { it!! }.toFloatArray()
            val trendIndicator = computeTrendIndicator(raw)
            val enhanced = raw.copyOf(raw.size + 1).also { it[raw.size] = trendIndicator }

            val targetVal = cols[targetIndex].toDoubleOrNull() ?: continue
            targets.add(doubleArrayOf(targetVal))
            inputs.add(enhanced)
        }

        if (inputs.size < 10) {
            Log.w(TAG, "Insufficient training samples (${inputs.size}) — skip")
            return
        }

        Log.i(TAG, "Training ML model on ${inputs.size} samples…")

        // Split 80/20 train/validation
        val splitIdx = (inputs.size * 0.8).toInt()
        val trainInputs = inputs.take(splitIdx)
        val trainTargets = targets.take(splitIdx)
        val valInputs = inputs.drop(splitIdx)
        val valTargets = targets.drop(splitIdx)

        val net = AimiNeuralNetwork(
            inputSize = INPUT_SIZE,
            hiddenSize = 8,
            outputSize = 1,
            config = app.aaps.plugins.aps.openAPSAIMI.TrainingConfig(
                learningRate = 0.001,
                epochs = 300
            ),
            regularizationLambda = 0.01
        )

        net.trainWithValidation(trainInputs, trainTargets, valInputs, valTargets)

        val valLoss = net.validate(valInputs, valTargets)
        Log.i(TAG, "Training complete. Validation loss = $valLoss")

        // Save model
        if (AimiSmbModelStore.save(dir, net)) {
            modelRef.set(net)
            rowsAtLastTrain.set(totalRows)
            lastTrainMs.set(System.currentTimeMillis())
            Log.i(TAG, "Model saved and activated.")
        } else {
            Log.e(TAG, "Model save failed — keeping previous model.")
        }
    }

    /** Expose whether ML has a model loaded (for logging/debug). */
    fun isModelLoaded(): Boolean = modelRef.get() != null

    /** Expose whether circuit breaker is open (for logging/debug). */
    fun isCircuitBreakerOpen(): Boolean {
        val coolUntil = cbCoolingUntilMs.get()
        return coolUntil != 0L && System.currentTimeMillis() < coolUntil
    }
}
