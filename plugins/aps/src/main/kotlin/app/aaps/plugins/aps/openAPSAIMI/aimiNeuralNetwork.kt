package app.aaps.plugins.aps.openAPSAIMI

import java.io.File
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * AimiNeuralNetwork — Feedforward neural network with 1 hidden layer and ADAM optimizer.
 *
 * Versão Refatorada (12/Ago/2026):
 *  - Treinamento completo de Biases (biasHidden e biasOutput) via ADAM
 *  - Padronização Z-Score das entradas (featureMeans e featureStds persistidos em JSON)
 *  - Remoção do pseudo-BatchNorm em prol da normalização de entrada
 *  - Derivada exata da Hybrid Loss (0.3 * MAE + 0.7 * MSE)
 *  - Early stopping com restauração garantida dos melhores pesos (bestValLoss)
 *  - Treinamento por Minibatch real com acumulação de gradientes
 */
class AimiNeuralNetwork(
    val inputSize: Int,
    private val hiddenSize: Int,
    private val outputSize: Int,
    private val config: TrainingConfig = TrainingConfig()
) {

    // ---- Pesos & Biases ----------------------------------------------------
    private var weightsInputHidden = Array(inputSize) {
        DoubleArray(hiddenSize) { Random.nextDouble(-sqrt(2.0 / inputSize), sqrt(2.0 / inputSize)) }
    }
    private var biasHidden = DoubleArray(hiddenSize) { 0.01 }

    private var weightsHiddenOutput = Array(hiddenSize) {
        DoubleArray(outputSize) { Random.nextDouble(-sqrt(2.0 / hiddenSize), sqrt(2.0 / hiddenSize)) }
    }
    private var biasOutput = DoubleArray(outputSize) { 0.01 }

    // ---- Z-Score Feature Normalization --------------------------------------
    var featureMeans = DoubleArray(inputSize) { 0.0 }
    var featureStds = DoubleArray(inputSize) { 1.0 }

    // Histórico de treinamento
    private val trainingLossHistory = mutableListOf<Double>()
    private var bestValLoss = Double.MAX_VALUE

    // ---- Estados do ADAM Optimizer -----------------------------------------
    private val mInputHidden = Array(inputSize) { DoubleArray(hiddenSize) { 0.0 } }
    private val vInputHidden = Array(inputSize) { DoubleArray(hiddenSize) { 0.0 } }
    private val mBiasHidden = DoubleArray(hiddenSize) { 0.0 }
    private val vBiasHidden = DoubleArray(hiddenSize) { 0.0 }

    private val mHiddenOutput = Array(hiddenSize) { DoubleArray(outputSize) { 0.0 } }
    private val vHiddenOutput = Array(hiddenSize) { DoubleArray(outputSize) { 0.0 } }
    private val mBiasOutput = DoubleArray(outputSize) { 0.0 }
    private val vBiasOutput = DoubleArray(outputSize) { 0.0 }

    private var adamStep = 0

    // ---- Normalização de Entrada --------------------------------------------
    fun normalizeInput(raw: FloatArray): FloatArray {
        return FloatArray(inputSize) { i ->
            val std = if (featureStds[i] < 1e-6) 1.0 else featureStds[i]
            ((raw[i] - featureMeans[i]) / std).toFloat()
        }
    }

    // ---- Forward Pass -------------------------------------------------------
    private data class ForwardResult(
        val normInput: FloatArray,
        val hidden: DoubleArray,
        val output: DoubleArray,
        val dropoutMask: BooleanArray?
    )

    private fun forwardPass(
        input: FloatArray,
        inferenceMode: Boolean = false
    ): ForwardResult {
        // Z-score padronização aplicada SEMPRE (treino e inferência) para evitar
        // skew entre o que o modelo aprende e o que recebe em produção.
        val normInput = normalizeInput(input)

        // Hidden layer: affine + activation
        val hidden = DoubleArray(hiddenSize)
        for (h in 0 until hiddenSize) {
            var sum = 0.0
            for (i in normInput.indices) {
                sum += normInput[i] * weightsInputHidden[i][h]
            }
            hidden[h] = sum + biasHidden[h]
        }

        // LeakyReLU activation
        for (h in 0 until hiddenSize) {
            val v = hidden[h]
            hidden[h] = if (v >= 0) v else config.leakyReluAlpha * v
        }

        // Dropout (apenas durante treinamento, se ativado).
        // A máscara é devolvida para o backpropagation zerar o gradiente das
        // unidades dropadas e para a loss usar a MESMA máscara dos gradientes.
        var dropoutMask: BooleanArray? = null
        if (!inferenceMode && config.useDropout) {
            val keepProb = 1.0 - config.dropoutRate
            dropoutMask = BooleanArray(hiddenSize)
            for (h in 0 until hiddenSize) {
                if (Random.nextDouble() < config.dropoutRate) {
                    hidden[h] = 0.0
                    dropoutMask[h] = false
                } else {
                    hidden[h] /= keepProb
                    dropoutMask[h] = true
                }
            }
        }

        // Output layer: affine
        val output = DoubleArray(outputSize)
        for (o in 0 until outputSize) {
            var sum = 0.0
            for (h in 0 until hiddenSize) {
                sum += hidden[h] * weightsHiddenOutput[h][o]
            }
            output[o] = sum + biasOutput[o]
        }
        return ForwardResult(normInput, hidden, output, dropoutMask)
    }

    /** Predição em inferência (modo produção). */
    fun predict(input: FloatArray): DoubleArray {
        // Guarda MEL-6: entrada inválida (NaN/Inf) → saída NaN para os chamadores
        // caírem no fallback seguro (refine retorna predictedSmb; classify retorna OK).
        if (input.any { !it.isFinite() }) return DoubleArray(outputSize) { Double.NaN }
        return forwardPass(input, inferenceMode = true).output
    }

    // ---- Loss & Backpropagation ---------------------------------------------
    private fun hybridLoss(output: DoubleArray, target: DoubleArray): Double {
        val alpha = 0.3
        val mae = output.zip(target).sumOf { (o, t) -> abs(o - t) } / output.size
        val mse = output.zip(target).sumOf { (o, t) -> (o - t).pow(2.0) } / output.size
        return alpha * mae + (1 - alpha) * mse
    }

    /**
     * Backpropagation com derivada exata da Loss Híbrida:
     * dLoss/dOutput = 0.3 * sign(output - target) + 1.4 * (output - target)
     * Usa o ForwardResult já computado (mesma máscara de dropout) e zera o
     * gradiente de unidades dropadas.
     */
    private fun backpropagation(fr: ForwardResult, target: DoubleArray): GradContainer {
        val hidden = fr.hidden
        val output = fr.output

        val gradOutput = DoubleArray(outputSize) { o ->
            val err = output[o] - target[o]
            0.3 * sign(err) + 1.4 * err
        }

        val gradBiasOutput = gradOutput.copyOf()
        val gradHiddenOutput = Array(hiddenSize) { h ->
            DoubleArray(outputSize) { o -> gradOutput[o] * hidden[h] }
        }

        val gradHidden = DoubleArray(hiddenSize) { h ->
            if (fr.dropoutMask != null && !fr.dropoutMask[h]) {
                0.0 // unidade dropada: gradiente zero
            } else {
                val sum = gradOutput.indices.sumOf { o -> gradOutput[o] * weightsHiddenOutput[h][o] }
                if (hidden[h] >= 0) sum else sum * config.leakyReluAlpha
            }
        }

        val gradBiasHidden = gradHidden.copyOf()
        val gradInputHidden = Array(inputSize) { i ->
            DoubleArray(hiddenSize) { h -> gradHidden[h] * fr.normInput[i] }
        }

        return GradContainer(gradInputHidden, gradBiasHidden, gradHiddenOutput, gradBiasOutput)
    }

    // ---- Atualização ADAM ----------------------------------------------------
    private fun updateAdam1D(param: DoubleArray, grad: DoubleArray, m: DoubleArray, v: DoubleArray) {
        val beta1 = config.beta1
        val beta2 = config.beta2
        val eps = config.epsilon
        val lr = config.learningRate
        for (i in param.indices) {
            m[i] = beta1 * m[i] + (1 - beta1) * grad[i]
            v[i] = beta2 * v[i] + (1 - beta2) * grad[i] * grad[i]
            val mHat = m[i] / (1 - beta1.pow(adamStep.toDouble()))
            val vHat = v[i] / (1 - beta2.pow(adamStep.toDouble()))
            param[i] -= lr * (mHat / (sqrt(vHat) + eps))
        }
    }

    private fun updateAdam2D(
        param: Array<DoubleArray>,
        grad: Array<DoubleArray>,
        m: Array<DoubleArray>,
        v: Array<DoubleArray>
    ) {
        val beta1 = config.beta1
        val beta2 = config.beta2
        val eps = config.epsilon
        val lr = config.learningRate
        val wd = config.weightDecay
        for (i in param.indices) {
            for (j in param[i].indices) {
                m[i][j] = beta1 * m[i][j] + (1 - beta1) * grad[i][j]
                v[i][j] = beta2 * v[i][j] + (1 - beta2) * grad[i][j] * grad[i][j]
                val mHat = m[i][j] / (1 - beta1.pow(adamStep.toDouble()))
                val vHat = v[i][j] / (1 - beta2.pow(adamStep.toDouble()))
                param[i][j] -= lr * (mHat / (sqrt(vHat) + eps))
                param[i][j] -= lr * wd * param[i][j] // L2 weight decay escalado por lr (AdamW)
            }
        }
    }

    // ---- Loop de Treinamento ------------------------------------------------
    fun trainWithValidation(
        trainInputs: List<FloatArray>,
        trainTargets: List<DoubleArray>,
        valInputs: List<FloatArray>,
        valTargets: List<DoubleArray>
    ) {
        if (trainInputs.isEmpty()) return
        trainingLossHistory.clear()
        bestValLoss = Double.MAX_VALUE
        adamStep = 0

        var bestW_IH = Array(inputSize) { weightsInputHidden[it].copyOf() }
        var bestB_H = biasHidden.copyOf()
        var bestW_HO = Array(hiddenSize) { weightsHiddenOutput[it].copyOf() }
        var bestB_O = biasOutput.copyOf()

        val totalEpochs = if (config.epochs <= 0) 300 else config.epochs
        val batchSize = if (config.batchSize <= 0) 32 else config.batchSize
        var epochsWithoutImprovement = 0

        for (epoch in 1..totalEpochs) {
            val indices = trainInputs.indices.shuffled()
            var totalTrainLoss = 0.0

            indices.chunked(batchSize).forEach { batchIdx ->
                // BUG-3: adamStep conta batches (updates reais do Adam), não epochs.
                // A correção de viés (1 - beta^t) deve usar o número real de updates.
                adamStep++
                val accGradIH = Array(inputSize) { DoubleArray(hiddenSize) }
                val accGradBH = DoubleArray(hiddenSize)
                val accGradHO = Array(hiddenSize) { DoubleArray(outputSize) }
                val accGradBO = DoubleArray(outputSize)

                batchIdx.forEach { idx ->
                    val input = trainInputs[idx]
                    val target = trainTargets[idx]
                    val fr = forwardPass(input, inferenceMode = false)
                    val grads = backpropagation(fr, target)

                    for (i in 0 until inputSize)
                        for (h in 0 until hiddenSize)
                            accGradIH[i][h] += grads.gIH[i][h] / batchIdx.size
                    for (h in 0 until hiddenSize)
                        accGradBH[h] += grads.gBH[h] / batchIdx.size
                    for (h in 0 until hiddenSize)
                        for (o in 0 until outputSize)
                            accGradHO[h][o] += grads.gHO[h][o] / batchIdx.size
                    for (o in 0 until outputSize)
                        accGradBO[o] += grads.gBO[o] / batchIdx.size

                    totalTrainLoss += hybridLoss(fr.output, target)
                }

                updateAdam2D(weightsInputHidden, accGradIH, mInputHidden, vInputHidden)
                updateAdam1D(biasHidden, accGradBH, mBiasHidden, vBiasHidden)
                updateAdam2D(weightsHiddenOutput, accGradHO, mHiddenOutput, vHiddenOutput)
                updateAdam1D(biasOutput, accGradBO, mBiasOutput, vBiasOutput)
            }

            val avgTrainLoss = totalTrainLoss / trainInputs.size
            trainingLossHistory.add(avgTrainLoss)

            val valLoss = validate(valInputs, valTargets)
            if (valLoss < bestValLoss) {
                bestValLoss = valLoss
                epochsWithoutImprovement = 0
                bestW_IH = Array(inputSize) { weightsInputHidden[it].copyOf() }
                bestB_H = biasHidden.copyOf()
                bestW_HO = Array(hiddenSize) { weightsHiddenOutput[it].copyOf() }
                bestB_O = biasOutput.copyOf()
            } else {
                epochsWithoutImprovement++
                if (epochsWithoutImprovement >= config.patience) break
            }
        }

        // Restaura os melhores pesos (Early Stopping seguro)
        weightsInputHidden = bestW_IH
        biasHidden = bestB_H
        weightsHiddenOutput = bestW_HO
        biasOutput = bestB_O
    }

    fun validate(valInputs: List<FloatArray>, valTargets: List<DoubleArray>): Double {
        if (valInputs.isEmpty()) return 0.0
        var totalLoss = 0.0
        for (i in valInputs.indices) {
            val out = forwardPass(valInputs[i], inferenceMode = true).output
            totalLoss += hybridLoss(out, valTargets[i])
        }
        return totalLoss / valInputs.size
    }

    // ---- Persistência JSON --------------------------------------------------
    // MELHORIA-2: versão de arquitetura do modelo. Incrementar CURRENT_MODEL_VERSION
    // sempre que a arquitetura/features mudarem para forçar retreino automático.

    fun saveToFile(file: File) {
        val root = JSONObject()
        root.put("version", CURRENT_MODEL_VERSION)
        root.put("inputSize", inputSize)
        root.put("hiddenSize", hiddenSize)
        root.put("outputSize", outputSize)

        fun DoubleArray.toJsonArray(): JSONArray {
            val arr = JSONArray()
            this.forEach { arr.put(it) }
            return arr
        }
        fun Array<DoubleArray>.toJsonArray(): JSONArray {
            val arr = JSONArray()
            this.forEach { arr.put(it.toJsonArray()) }
            return arr
        }

        root.put("weightsInputHidden", weightsInputHidden.toJsonArray())
        root.put("biasHidden", biasHidden.toJsonArray())
        root.put("weightsHiddenOutput", weightsHiddenOutput.toJsonArray())
        root.put("biasOutput", biasOutput.toJsonArray())
        root.put("featureMeans", featureMeans.toJsonArray())
        root.put("featureStds", featureStds.toJsonArray())
        file.writeText(root.toString())
    }

    companion object {
        const val CURRENT_MODEL_VERSION = 2

        fun loadFromFile(file: File): AimiNeuralNetwork? {
            if (!file.exists()) return null
            return try {
                val root = JSONObject(file.readText())
                // MELHORIA-2: rejeita modelos de versão de arquitetura incompatível
                // (ausente/1 = formato antigo) → retreino automático na próxima janela.
                val version = if (root.has("version")) root.getInt("version") else 1
                if (version != CURRENT_MODEL_VERSION) return null
                val nn = AimiNeuralNetwork(
                    root.getInt("inputSize"),
                    root.getInt("hiddenSize"),
                    root.getInt("outputSize")
                )
                fun parseDoubleArray(jsonArr: JSONArray): DoubleArray {
                    return DoubleArray(jsonArr.length()) { i -> jsonArr.getDouble(i) }
                }
                fun parseArrayOfDoubleArray(jsonArr: JSONArray): Array<DoubleArray> {
                    return Array(jsonArr.length()) { i -> parseDoubleArray(jsonArr.getJSONArray(i)) }
                }
                nn.weightsInputHidden = parseArrayOfDoubleArray(root.getJSONArray("weightsInputHidden"))
                nn.biasHidden = parseDoubleArray(root.getJSONArray("biasHidden"))
                nn.weightsHiddenOutput = parseArrayOfDoubleArray(root.getJSONArray("weightsHiddenOutput"))
                nn.biasOutput = parseDoubleArray(root.getJSONArray("biasOutput"))
                if (root.has("featureMeans")) nn.featureMeans = parseDoubleArray(root.getJSONArray("featureMeans"))
                if (root.has("featureStds")) nn.featureStds = parseDoubleArray(root.getJSONArray("featureStds"))
                nn
            } catch (e: Exception) {
                null
            }
        }
    }

    fun copyWeightsFrom(other: AimiNeuralNetwork) {
        for (i in weightsInputHidden.indices)
            for (j in weightsInputHidden[i].indices)
                weightsInputHidden[i][j] = other.weightsInputHidden[i][j]
        for (i in biasHidden.indices)
            biasHidden[i] = other.biasHidden[i]
        for (i in weightsHiddenOutput.indices)
            for (j in weightsHiddenOutput[i].indices)
                weightsHiddenOutput[i][j] = other.weightsHiddenOutput[i][j]
        for (i in biasOutput.indices)
            biasOutput[i] = other.biasOutput[i]
        featureMeans = other.featureMeans.copyOf()
        featureStds = other.featureStds.copyOf()
    }

    private data class GradContainer(
        val gIH: Array<DoubleArray>,
        val gBH: DoubleArray,
        val gHO: Array<DoubleArray>,
        val gBO: DoubleArray
    )
}
