package app.aaps.plugins.aps.openAPSAIMI.ml

import android.util.Log
import app.aaps.plugins.aps.openAPSAIMI.AimiNeuralNetwork
import java.io.File

/**
 * AimiBgModelStore — Crash-safe persistence for the AimiNeuralNetwork BG confidence model.
 *
 * Adaptado de AimiSmbModelStore (mesmo padrão): salva a rede em JSON com
 * escrita atômica (tmp + rename + bak) para não corromper o modelo em crash.
 */
object AimiBgModelStore {

    private const val TAG = "AimiBgModelStore"

    private fun mainFile(dir: File) = File(dir, "bg_confidence_model.json")
    private fun tmpFile(dir: File)  = File(dir, "bg_confidence_model.json.tmp")
    private fun bakFile(dir: File)  = File(dir, "bg_confidence_model.json.bak")

    /** Atomically save [network] to [dir]. Returns true on success. */
    fun save(dir: File, network: AimiNeuralNetwork): Boolean {
        return try {
            dir.mkdirs()
            val tmp = tmpFile(dir)
            val main = mainFile(dir)
            val bak = bakFile(dir)

            network.saveToFile(tmp)

            if (main.exists()) {
                bak.delete()
                main.renameTo(bak)
            }

            val ok = tmp.renameTo(main)
            if (!ok) Log.e(TAG, "Atomic rename failed — model may be stale.")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "save() failed: ${e.message}")
            false
        }
    }

    /** Load model from [dir], trying main then backup. Returns null if none valid. */
    fun load(dir: File, expectedInputSize: Int): AimiNeuralNetwork? {
        val candidates = listOf(mainFile(dir), bakFile(dir))
        for (file in candidates) {
            if (!file.exists()) continue
            try {
                val net = AimiNeuralNetwork.loadFromFile(file) ?: continue
                if (validate(net, expectedInputSize)) {
                    Log.d(TAG, "BG confidence model loaded from ${file.name}")
                    return net
                } else {
                    Log.w(TAG, "BG confidence model ${file.name} failed validation — skipping.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load ${file.name}: ${e.message}")
            }
        }
        return null
    }

    /** Valida a arquitetura da rede contra o tamanho esperado de entrada. */
    private fun validate(net: AimiNeuralNetwork, expectedInputSize: Int): Boolean {
        return try {
            // Validação REAL (Fase 2, 11/Ago/2026): modelo com INPUT_SIZE diferente do
            // esperado é INCOMPATÍVEL (features desalinhadas → classificação errada).
            if (net.inputSize != expectedInputSize) {
                Log.w(TAG, "BG confidence model inputSize=${net.inputSize} != esperado $expectedInputSize — rejeitado")
                return false
            }
            // MEL-2: mesmo padrão do AimiSmbModelStore — rejeita modelo com NaN/Inf
            // nos pesos (probe de predict) em vez do antigo copyWeightsFrom no-op.
            val probe = FloatArray(expectedInputSize) { 0f }
            net.predict(probe).all { it.isFinite() }
        } catch (e: Exception) {
            Log.e(TAG, "BG confidence model validation failed: ${e.message}")
            false
        }
    }
}
