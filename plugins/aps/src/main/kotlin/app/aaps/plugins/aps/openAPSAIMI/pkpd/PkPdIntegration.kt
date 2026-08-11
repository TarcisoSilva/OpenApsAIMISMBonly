package app.aaps.plugins.aps.openAPSAIMI.pkpd

import app.aaps.plugins.aps.openAPSAIMI.pkpd.InsulinActionState.*
import kotlin.math.*

/**
 * PkPdIntegration — Orquestrador do modelo PK/PD.
 *
 * Agrega dados de BG, delta, IOB, COB, TDD e contexto para computar
 * o estado do modelo PK/PD a cada ciclo APS (~5min).
 *
 * Fluxo:
 *   1. Chama InsulinActionProfiler.calculate() → fusedISF, DIA, peak
 *   2. Calcula iobActivityNow (atividade insulinica atual)
 *   3. Determina insulinActionState (PRE_ACTIVATION/PEAK/TAIL/RESIDUAL/INACTIVE)
 *   4. Empacota em PkPdRuntime
 *
 * Não depende de interfaces AAPS externas — recebe apenas primitivos.
 * O caller (invoke() no DetermineBasalAdapterAIMI.kt) extrai os dados
 * das interfaces AAPS e passa para este método.
 *
 * Uso:
 *   val runtime = PkPdIntegration.computeRuntime(
 *       epochMillis = dateUtil.now(),
 *       bg = bg, iobU = iob.toDouble(), ...
 *   )
 */
object PkPdIntegration {

    // Constantes para estimativa de estado
    private const val PRE_ACTIVATION_THRESHOLD = 0.30  // 30% do tempo até pico
    private const val PEAK_THRESHOLD = 0.70             // 70%
    private const val RESIDUAL_THRESHOLD = 0.90         // 90%
    private const val INACTIVE_IOB_MIN = 0.05            // U
    private const val DIA_DEFAULT_HRS = 5.0
    private const val PEAK_DEFAULT_MIN = 75.0

    /**
     * Computa o runtime PK/PD para o ciclo atual.
     *
     * @param epochMillis Timestamp atual (epoch ms)
     * @param bg Glicemia atual (mg/dL)
     * @param deltaMgDlPer5 Variação BG (mg/dL/5min)
     * @param iobU Insulin on Board atual (U)
     * @param windowSinceLastDoseMin Minutos desde o último SMB/bolus
     * @param exerciseFlag Exercício detectado?
     * @param profileIsf ISF do perfil (mg/dL/U)
     * @param tdd24h TDD das últimas 24h (U)
     * @param iobValues Histórico de valores de IOB (mais antigo → mais recente)
     * @return PkPdRuntime ou null se dados insuficientes
     */
    fun computeRuntime(
        epochMillis: Long,
        bg: Double,
        deltaMgDlPer5: Double,
        iobU: Double,
        windowSinceLastDoseMin: Double,
        exerciseFlag: Boolean,
        profileIsf: Double,
        tdd24h: Double,
        iobValues: List<Double> = emptyList()
    ): PkPdRuntime? {
        // Dados mínimos necessários
        if (profileIsf <= 0) return null

        // 1. InsulinActionProfiler
        val profiler = InsulinActionProfiler.calculate(
            iobValues = iobValues,
            profileIsf = profileIsf,
            tdd24h = tdd24h,
            snsDominance = 0.3
        )

        val fusedIsf: Double = profiler.fusedIsf
        val diaHrs: Double = profiler.diaHrs
        val peakMin: Double = profiler.peakMin

        // 2. Atividade IOB
        val iobActivityNow: Double = if (fusedIsf > 0) {
            (abs(deltaMgDlPer5) / fusedIsf).coerceIn(0.0, 2.0)
        } else {
            0.0
        }

        // 3. Estado da ação da insulina
        val insulinActionState: InsulinActionState = estimateInsulinActionState(
            iobU = iobU,
            windowSinceLastDoseMin = windowSinceLastDoseMin,
            diaHrs = diaHrs,
            peakMin = peakMin
        )

        // 4. Parâmetros internos
        val peakProgress: Double = if (peakMin > 0 && windowSinceLastDoseMin > 0) {
            (windowSinceLastDoseMin / peakMin).coerceIn(0.0, 3.0)
        } else {
            0.0
        }
        val absorptionRate: Double = if (iobU > 0 && diaHrs > 0) {
            (iobU / diaHrs).coerceIn(0.0, 5.0)
        } else {
            0.0
        }
        val remainingDuration: Double = max(0.0, diaHrs * 60 - windowSinceLastDoseMin)

        val params = PkPdParams(
            absorptionRate = absorptionRate,
            remainingDuration = remainingDuration,
            peakProgress = peakProgress
        )

        return PkPdRuntime(
            fusedIsf = fusedIsf,
            diaHrs = diaHrs,
            peakMin = peakMin,
            iobActivityNow = iobActivityNow,
            insulinActionState = insulinActionState,
            params = params,
            computedAt = epochMillis
        )
    }

    /**
     * Estima o estado da ação da insulina baseado no progresso temporal
     * e no IOB residual.
     */
    private fun estimateInsulinActionState(
        iobU: Double,
        windowSinceLastDoseMin: Double,
        diaHrs: Double,
        peakMin: Double
    ): InsulinActionState {
        // Sem insulina ativa
        if (iobU < INACTIVE_IOB_MIN) return INACTIVE

        // Sem dados de janela
        if (windowSinceLastDoseMin <= 0 || peakMin <= 0) {
            return if (iobU > 0) PEAK else INACTIVE
        }

        val effectivePeakMin: Double = peakMin
        val progress: Double = windowSinceLastDoseMin / effectivePeakMin

        return when {
            progress < PRE_ACTIVATION_THRESHOLD -> PRE_ACTIVATION
            progress < PEAK_THRESHOLD -> PEAK
            progress < RESIDUAL_THRESHOLD -> TAIL
            else -> RESIDUAL
        }
    }
}
