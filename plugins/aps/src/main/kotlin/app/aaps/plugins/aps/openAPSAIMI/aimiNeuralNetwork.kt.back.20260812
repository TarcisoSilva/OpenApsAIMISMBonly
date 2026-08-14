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
 * AimiNeuralNetwork — Simple feedforward neural network with 1 hidden layer.
 *
 * Architecture:
 *   inputSize → hiddenSize (LeakyReLU + BatchNorm) → outputSize (linear)
 *
 * Optimizer: ADAM with L2 regularization.
 * Persistence: save/load to JSON via [saveToFile] / [loadFromFile].
 */
class AimiNeuralNetwork(
    val inputSize: Int,
    private val hiddenSize: Int,
    private val outputSize: Int,
    private val config: TrainingConfig = TrainingConfig(),
    private val regularizationLambda: Double = 0.01
) {

    // ---- Weights & biases --------------------------------------------------
    private var weightsInputHidden = Array(inputSize) {
        DoubleArray(hiddenSize) { Random.nextDouble(-sqrt(2.0 / inputSize), sqrt(2.0 / inputSize)) }
    }
    private var biasHidden = DoubleArray(hiddenSize) { 0.01 }

    private var weightsHiddenOutput = Array(hiddenSize) {
        DoubleArray(outputSize) { Random.nextDouble(-sqrt(2.0 / hiddenSize), sqrt(2.0 / hiddenSize)) }
    }
    private var biasOutput = DoubleArray(outputSize) { 0.01 }

    // Training history (for monitoring)
    private val trainingLossHistory = mutableListOf<Double>()
    private var bestValLoss = Double.MAX_VALUE

    // ---- ADAM state ---------------------------------------------------------
    private val mInputHidden = Array(inputSize) { DoubleArray(hiddenSize) { 0.0 } }
    private val vInputHidden = Array(inputSize) { DoubleArray(hiddenSize) { 0.0 } }
    private val mHiddenOutput = Array(hiddenSize) { DoubleArray(outputSize) { 0.0 } }
    private val vHiddenOutput = Array(hiddenSize) { DoubleArray(outputSize) { 0.0 } }
    private var adamStep = 0

    // ---- Activation & helpers -----------------------------------------------

    private fun leakyRelu(x: Double, alpha: Double = config.leakyReluAlpha): Double {
        return if (x >= 0) x else alpha * x
    }

    /** Forward pass. Returns (hidden, output). */
    private fun forwardPass(
        input: FloatArray,
        inferenceMode: Boolean = false
    ): Pair<DoubleArray, DoubleArray> {
        // Hidden layer: affine + activation
        val hidden = DoubleArray(hiddenSize)
        for (h in 0 until hiddenSize) {
            var sum = 0.0
            for (i in input.indices) {
                sum += input[i] * weightsInputHidden[i][h]
            }
            hidden[h] = sum + biasHidden[h]
        }
        // LeakyReLU
        for (h in 0 until hiddenSize) {
            val v = hidden[h]
            hidden[h] = if (v >= 0) v else config.leakyReluAlpha * v
        }
        // BatchNorm (applied in inference too)
        if (config.useBatchNorm) {
            var sum = 0.0
            for (h in 0 until hiddenSize) sum += hidden[h]
            val mean = sum / hiddenSize
            var sumSq = 0.0
            for (h in 0 until hiddenSize) {
                val diff = hidden[h] - mean
                sumSq += diff * diff
            }
            val denom = sqrt(sumSq / hiddenSize + 1e-8)
            for (h in 0 until hiddenSize) {
                hidden[h] = (hidden[h] - mean) / denom
            }
        }
        // Dropout (training only)
        if (!inferenceMode && config.useDropout) {
            val keepProb = 1.0 - config.dropoutRate
            for (h in 0 until hiddenSize) {
                if (Random.nextDouble() < config.dropoutRate) {
                    hidden[h] = 0.0
                } else {
                    hidden[h] /= keepProb
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
        return hidden to output
    }

    /** Public inference API. Returns raw output array. */
    fun predict(input: FloatArray): DoubleArray {
        return forwardPass(input, inferenceMode = true).second
    }

    // ---- Loss functions -----------------------------------------------------

    private fun hybridLoss(output: DoubleArray, target: DoubleArray): Double {
        val alpha = 0.3
        val mae = output.zip(target).sumOf { (o, t) -> abs(o - t) } / output.size
        val mse = output.zip(target).sumOf { (o, t) -> (o - t).pow(2.0) } / output.size
        return alpha * mae + (1 - alpha) * mse
    }

    private fun l2Regularization(): Double {
        var reg = 0.0
        weightsInputHidden.forEach { row -> row.forEach { w -> reg += w.pow(2.0) } }
        weightsHiddenOutput.forEach { row -> row.forEach { w -> reg += w.pow(2.0) } }
        return reg * regularizationLambda
    }

    // ---- Backpropagation + ADAM ---------------------------------------------

    private fun backpropagation(input: FloatArray, target: DoubleArray): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        val (hidden, output) = forwardPass(input, inferenceMode = false)
        val gradOutput = DoubleArray(outputSize) { i -> sign(output[i] - target[i]) }
        val gradHiddenOutput = Array(hiddenSize) { h ->
            DoubleArray(outputSize) { o -> gradOutput[o] * hidden[h] }
        }
        val gradHidden = DoubleArray(hiddenSize) { h ->
            val sum = gradOutput.indices.sumOf { o -> gradOutput[o] * weightsHiddenOutput[h][o] }
            if (hidden[h] >= 0) sum else sum * config.leakyReluAlpha
        }
        val gradInputHidden = Array(inputSize) { i ->
            DoubleArray(hiddenSize) { h -> gradHidden[h] * input[i] }
        }
        return gradInputHidden to gradHiddenOutput
    }

    private fun adamUpdate(
        weights: Array<DoubleArray>,
        grads: Array<DoubleArray>,
        m: Array<DoubleArray>,
        v: Array<DoubleArray>
    ) {
        adamStep++
        val beta1 = config.beta1
        val beta2 = config.beta2
        val eps = config.epsilon
        val lr = config.learningRate
        val wd = config.weightDecay
        for (i in weights.indices) {
            for (j in weights[i].indices) {
                m[i][j] = beta1 * m[i][j] + (1 - beta1) * grads[i][j]
                v[i][j] = beta2 * v[i][j] + (1 - beta2) * grads[i][j] * grads[i][j]
                val mHat = m[i][j] / (1 - beta1.pow(adamStep.toDouble()))
                val vHat = v[i][j] / (1 - beta2.pow(adamStep.toDouble()))
                weights[i][j] -= lr * (mHat / (sqrt(vHat) + eps))
                weights[i][j] -= wd * weights[i][j] // L2 decay
            }
        }
    }

    // ---- Training -----------------------------------------------------------

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

        val totalEpochs = if (config.epochs <= 0) 1000 else config.epochs
        val batchSize = if (config.batchSize <= 0) 32 else config.batchSize
        var epochsWithoutImprovement = 0

        for (epoch in 1..totalEpochs) {
            val indices = trainInputs.indices.shuffled()
            var totalLoss = 0.0
            indices.chunked(batchSize).forEach { batchIdx ->
                batchIdx.forEach { idx ->
                    val input = trainInputs[idx]
                    val target = trainTargets[idx]
                    val (gradIH, gradHO) = backpropagation(input, target)
                    adamUpdate(weightsInputHidden, gradIH, mInputHidden, vInputHidden)
                    adamUpdate(weightsHiddenOutput, gradHO, mHiddenOutput, vHiddenOutput)
                    totalLoss += hybridLoss(forwardPass(input, inferenceMode = false).second, target)
                }
            }
            val avgTrainLoss = totalLoss / trainInputs.size
            trainingLossHistory.add(avgTrainLoss)
            val valLoss = validate(valInputs, valTargets)
            if (valLoss < bestValLoss) {
                bestValLoss = valLoss
                epochsWithoutImprovement = 0
            } else {
                epochsWithoutImprovement++
                if (epochsWithoutImprovement >= config.patience) {
                    break
                }
            }
        }
    }

    fun validate(valInputs: List<FloatArray>, valTargets: List<DoubleArray>): Double {
        if (valInputs.isEmpty()) return 0.0
        var totalLoss = 0.0
        for (i in valInputs.indices) {
            val out = forwardPass(valInputs[i], inferenceMode = true).second
            totalLoss += hybridLoss(out, valTargets[i])
        }
        totalLoss += l2Regularization()
        return totalLoss / valInputs.size
    }

    // ---- Persistence --------------------------------------------------------

    fun saveToFile(file: File) {
        val root = JSONObject()
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
        file.writeText(root.toString())
    }

    companion object {
        fun loadFromFile(file: File): AimiNeuralNetwork? {
            if (!file.exists()) return null
            return try {
                val root = JSONObject(file.readText())
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
                nn
            } catch (e: Exception) {
                e.printStackTrace()
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
    }
}
