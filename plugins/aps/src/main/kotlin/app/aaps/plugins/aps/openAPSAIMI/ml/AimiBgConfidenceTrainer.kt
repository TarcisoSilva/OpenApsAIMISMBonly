package app.aaps.plugins.aps.openAPSAIMI.ml

import android.util.Log
import app.aaps.plugins.aps.openAPSAIMI.AimiNeuralNetwork
import app.aaps.plugins.aps.openAPSAIMI.BgConfidenceGuard
import kotlin.math.abs
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

/**
 * AimiBgConfidenceTrainer — Rede neural que aprende a classificar o nível de
 * ruído das leituras de BG, usando FINGER_STICK_BG_VALUE (ponta de dedo)
 * como verdade de referência.
 *
 * O sistema é 100% refém das leituras de BG. Esta rede é o "revisor" que
 * aprende quais leituras são confiáveis (OK), duvidosas (UNCERTAIN) ou
 * ruído (BAD), com travas de segurança em BgConfidenceGuard.
 *
 * FASE 1 (atual): classifica e LOG apenas — NÃO afeta o SMB ainda.
 * FASE 2 (futura): aplicar smbMultiplier(tier) no DetermineBasalAdapterAIMI.
 *
 * Safety contracts (mesmo padrão do AimiSmbTrainer):
 *  - classify() é sempre O(1): fallback para OK(0) em qualquer erro
 *  - training roda em Dispatchers.IO, nunca no hot-path
 *  - circuit breaker desliga ML por 6h após 3 falhas consecutivas
 *  - sem modelo → OK(0) → SMB normal (fail-safe: não punir sem dados)
 */
object AimiBgConfidenceTrainer {

    private const val TAG = "AimiBgConfidenceTrainer"

    /** Input dimension: 15 physio features + 1 trendIndicator = 16 (Fase 2, 11/Ago/2026) */
    const val INPUT_SIZE = 16

    /** Feature column names no CSV de treino dedicado. */
    private val FEATURE_COLUMNS = listOf(
        "bg", "delta", "shortAvgDelta", "longAvgDelta",
        "saltoAnterior", "reversao", "idadeSensorMin", "compressaoAtiva",
        "iob", "cob", "tdd7DaysPerHour", "isNight", "faseSensor",
        "direcaoDivergencia", "emJanelaRefeicao"
    )
    private const val TARGET_COLUMN = "bgConfidenceTarget"   // 0=OK, 1=UNCERTAIN, 2=BAD

    // Circuit breaker (mesmo padrão do SMB)
    private const val CB_MAX_FAILURES = 3
    private const val CB_COOLDOWN_MS = 6 * 60 * 60 * 1000L  // 6h

    // Training rate limit
    private const val TRAIN_INTERVAL_MS = 6 * 60 * 60 * 1000L  // 6h
    private const val MIN_NEW_ROWS_TO_RETRAIN = 100

    // Gabarito — thresholds da ponta de dedo
    private const val GABARITO_OK_MAX = 15.0        // divergência < 15 → OK
    private const val GABARITO_UNCERTAIN_MAX = 30.0 // 15-30 → UNCERTAIN; >= 30 → BAD

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

    // Tarciso_BG_CONFIDENCE_V2 (09/Ago/2026) — cooldown da captura automática
    private val ultimaCapturaAutomaticaMs = AtomicLong(0L)

    // Model directory (lazily set)
    private var modelDir: File? = null

    // ---- Public API ----------------------------------------------------------

    /** Carrega modelo salvo. Chame uma vez na inicialização. */
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

    /**
     * Classifica a confiança da leitura atual: 0=OK, 1=UNCERTAIN, 2=BAD.
     * O(1) — nunca bloqueia. Sem modelo ou erro → 0 (OK, fail-safe).
     */
    fun classify(
        bg: Double, delta: Double, shortAvgDelta: Double, longAvgDelta: Double,
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
            val trend = computeTrendIndicator(features)
            val enhanced = features.copyOf(INPUT_SIZE).also { it[INPUT_SIZE - 1] = trend }

            val out = model.predict(enhanced)
            val prob = out.firstOrNull() ?: return 0
            if (!prob.isFinite()) return 0

            val rawTier = when {
                prob < 0.33 -> 0   // OK
                prob < 0.66 -> 1   // UNCERTAIN
                else -> 2          // BAD
            }

            // Aplicar travas de segurança (fail-safe)
            BgConfidenceGuard.applySafety(rawTier, bg, delta)
        } catch (e: Exception) {
            recordFailure()
            Log.w(TAG, "classify() exception: ${e.message}")
            0
        }
    }

    /**
     * Registra uma amostra de treino com gabarito da ponta de dedo.
     * Chamado quando um FINGER_STICK_BG_VALUE chega (em setData).
     * FASE 1: apenas coleta + salva no CSV dedicado; dispara treino async.
     */
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
            // Gabarito pela divergência com a ponta de dedo
            // Melhoria B (11/Ago/2026): subida sustentada (delta>1) com divergência < 40
            // é LAG fisiológico de refeição (dedo sobe antes do sensor), NUNCA ruído.
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
                System.currentTimeMillis().toString()  // dateLong (epoch ms) — permite recalcular idade do sensor e depurar amostras
            ).joinToString(",")

            // dateLong no FIM: o treino localiza colunas por nome (FEATURE_COLUMNS),
            // então a coluna extra é ignorada e linhas antigas (14 cols) seguem válidas.
            val header = (FEATURE_COLUMNS + TARGET_COLUMN + "dateLong").joinToString(",")

            val writeHeader = !csvFile.exists() || csvFile.readText().isBlank()
            csvFile.appendText((if (writeHeader) "$header\n" else "") + line + "\n")

            maybeTrainAsync(csvFile)
        } catch (e: Exception) {
            Log.w(TAG, "recordSample() failed: ${e.message}")
        }
    }

    /**
     * Tarciso_BG_CONFIDENCE_V2 (09/Ago/2026) — Captura automática de suspeitas.
     *
     * Registra amostras com gabarito HEURÍSTICO quando o sensor mostra padrões
     * de ruído conhecidos — sem precisar da ponta de dedo. Acelera o aprendizado
     * para dezenas de amostras/dia. Protegido pelas travas de segurança
     * (BgConfidenceGuard) no uso real.
     *
     * Padrões detectados:
     *   1. Reversão de salto: subiu >20 e caiu >20 (clássico de ruído)
     *   2. 39 constante: xDrip error state
     *   3. Sensor novo oscilando: idade < 6h e delta > 10
     *
     * Cooldown: máx 1 captura automática a cada 15 min (evita spam por ciclo de 5min).
     */
    fun capturarSuspensaoAutomatica(
        csvFile: File,
        bgAnterior: Double, bgAtual: Double, bgFutura: Double,
        delta: Double, idadeSensorMin: Long,
        iob: Double, cob: Double, tdd7DaysPerHour: Double,
        isNight: Double, faseSensor: Double, emJanelaRefeicao: Double
    ) {
        try {
            // Padrão 1: reversão de salto (subiu >20 e caiu >20)
            val subiu = bgAtual - bgAnterior > 20.0
            val caiu = bgAnterior - bgAtual > 20.0
            val reverteu = (subiu && (bgFutura < bgAtual - 20.0)) ||
                           (caiu && (bgFutura > bgAtual + 20.0))

            // Padrão 2: 39 constante (xDrip error state)
            val xdripError = bgAtual <= 39.0

            // Padrão 3: sensor novo com oscilação forte
            val sensorNovoOscilando = idadeSensorMin < 6 * 60 && abs(delta) > 10.0

            if (!reverteu && !xdripError && !sensorNovoOscilando) return

            // Cooldown: máx 1 captura automática a cada 15 min
            val agora = System.currentTimeMillis()
            if (agora - ultimaCapturaAutomaticaMs.get() < 15 * 60 * 1000L) return
            ultimaCapturaAutomaticaMs.set(agora)

            // Gabarito heurístico: força BAD (2) para reversão/erro, UNCERTAIN (1) p/ sensor novo
            val divergenciaHeuristica = when {
                xdripError -> 35.0    // ≥30 → BAD
                reverteu -> 30.0      // ≥30 → BAD
                else -> 20.0          // 15-30 → UNCERTAIN
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
                direcaoDivergencia = 0.0,   // captura automática não tem dedo (heurística)
                emJanelaRefeicao = emJanelaRefeicao,
                divergenciaDedo = divergenciaHeuristica
            )
            Log.i(TAG, "Captura automática: reversão=$reverteu, xdrip=$xdripError, sensorNovo=$sensorNovoOscilando")
        } catch (e: Exception) {
            Log.w(TAG, "capturarSuspensaoAutomatica() failed: ${e.message}")
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
        val delta = features.getOrElse(1) { 0f }
        val shortAvg = features.getOrElse(2) { 0f }
        val longAvg = features.getOrElse(3) { 0f }
        val shortVsLong = shortAvg - longAvg
        return when {
            delta > 2 && shortVsLong > 1 -> 2f
            delta < -2 && shortVsLong < -1 -> -2f
            delta > 1 -> 1f
            delta < -1 -> -1f
            else -> 0f
        }
    }

    /** Fire-and-forget training trigger (mesmo padrão do SMB). */
    private fun maybeTrainAsync(csvFile: File) {
        val dir = modelDir ?: return
        val now = System.currentTimeMillis()

        if (now - lastTrainMs.get() < TRAIN_INTERVAL_MS) return
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

    private suspend fun trainNow(dir: File, csvFile: File) {
        if (!csvFile.exists()) {
            Log.d(TAG, "CSV not found — skip training")
            return
        }

        val lines = csvFile.readLines()
        if (lines.size < 2) {
            Log.d(TAG, "Insufficient training samples")
            return
        }

        val headers = lines.first().split(",").map { it.trim() }
        val featureIndices = FEATURE_COLUMNS.map { headers.indexOf(it) }
        val targetIndex = headers.indexOf(TARGET_COLUMN)

        if (featureIndices.any { it == -1 } || targetIndex == -1) {
            Log.w(TAG, "CSV missing required columns")
            return
        }

        val inputs = mutableListOf<FloatArray>()
        val targets = mutableListOf<DoubleArray>()

        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val cols = line.split(",").map { it.trim() }
            if (cols.size <= targetIndex) continue

            val rawFeatures = featureIndices.map { idx -> cols.getOrNull(idx)?.toFloatOrNull() }
            if (rawFeatures.any { it == null }) continue

            val raw = rawFeatures.map { it!! }.toFloatArray()
            val trendIndicator = computeTrendIndicator(raw)
            val enhanced = raw.copyOf(raw.size + 1).also { it[raw.size] = trendIndicator }

            val targetVal = cols[targetIndex].toDoubleOrNull() ?: continue

            // Tarciso_BG_CONFIDENCE_V2 (09/Ago/2026) — PESO por qualidade do gabarito.
            // bg=raw[0], delta=raw[1], shortAvg=raw[2], longAvg=raw[3] (índices das features).
            // Amostras ideais (BG 70-140, delta pequeno) são as mais confiáveis → ×3.
            // Extremas (delta > 5, fora da faixa) → ×1 (úteis para detectar ruído, mas
            // gabarito menos certo).
            // Melhoria C (11/Ago/2026): amostra de LAG (target UNCERTAIN + subindo, delta>1)
            // entra com peso 1 — a rede não deve aprender forte que "subida = não confiável"
            // (o lag fisiológico de refeição não é ruído).
            val peso = when {
                targetVal == 1.0 && raw[1] > 1.0f -> 1               // lag plausível (subindo)
                raw[0] in 70.0f..140.0f && abs(raw[1]) < 2.0f -> 3   // ideal (delta pequeno)
                abs(raw[1]) > 5.0f || raw[0] !in 70.0f..140.0f -> 1  // extremo
                else -> 2                                            // normal
            }
            repeat(peso) {
                targets.add(doubleArrayOf(targetVal))
                inputs.add(enhanced)
            }
        }

        if (inputs.size < 10) {
            Log.w(TAG, "Insufficient training samples (${inputs.size}) — skip")
            return
        }

        Log.i(TAG, "Training BG confidence model on ${inputs.size} samples…")

        // Split 80/20
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
        Log.i(TAG, "BG confidence training complete. Validation loss = $valLoss")

        // Salvar modelo
        if (AimiBgModelStore.save(dir, net)) {
            modelRef.set(net)
            rowsAtLastTrain.set(lines.size.toLong())
            lastTrainMs.set(System.currentTimeMillis())
            Log.i(TAG, "BG confidence model saved and activated.")
        } else {
            Log.e(TAG, "Model save failed — keeping previous model.")
        }
    }
}
