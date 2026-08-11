package app.aaps.plugins.aps.openAPSAIMI.pkpd

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * InsulinActionProfiler — Perfil de ação da insulina.
 *
 * Aprende como a insulina realmente age no corpo, calculando:
 * - fusedIsf: ISF fundido (combina ISF do perfil com TDD real)
 * - diaHrs: DIA aprendido do histórico de IOB
 * - peakMin: Tempo de pico baseado no perfil de decaimento
 * - activityNow: Atividade IOB no momento atual
 *
 * Não depende de interfaces do AAPS — recebe apenas valores primitivos.
 * O caller (PkPdIntegration) extrai os dados do IobTotal e passa como Double.
 *
 * Uso:
 *   val perfil = InsulinActionProfiler.calculate(
 *       iobValues = listOf(0.5, 0.4, 0.3, ...),  // IOB nos últimos N ciclos
 *       profileIsf = 150.0,
 *       tdd24h = 35.0,
 *       snsDominance = 0.3
 *   )
 */
object InsulinActionProfiler {

    /** Perfil calculado de ação da insulina */
    data class Profile(
        /** ISF fundido (mg/dL/U): blend do perfil com TDD real */
        val fusedIsf: Double,
        /** Duração da Insulina Ativa aprendida (horas) */
        val diaHrs: Double,
        /** Tempo de pico estimado (minutos) */
        val peakMin: Double,
        /** Atividade IOB atual (mg/dL/min) */
        val activityNow: Double
    )

    private const val DEFAULT_DIA_HRS = 5.0
    private const val DEFAULT_PEAK_MIN = 75.0
    private const val MIN_VALID_IOB = 0.01
    private const val TDD_TO_ISF_FACTOR = 1800.0
    private const val SNS_DOMINANCE_DEFAULT = 0.3
    private const val DIA_PEAK_RATIO = 0.25  // pico tipicamente em 25% do DIA

    /**
     * Calcula o perfil de ação da insulina.
     *
     * @param iobValues Lista de valores de IOB nos últimos N ciclos (ordenada do mais antigo para o mais recente).
     *                  Mínimo 2 ciclos. Quanto mais ciclos, melhor a estimativa de DIA.
     * @param profileIsf ISF do perfil (mg/dL/U)
     * @param tdd24h TDD das últimas 24h (U). 0.0 = usar apenas ISF do perfil.
     * @param snsDominance Peso do TDD no fusedISF (0.0 = só perfil, 1.0 = só TDD). Default 0.3.
     * @return Profile com fusedIsf, diaHrs, peakMin, activityNow
     */
    fun calculate(
        iobValues: List<Double>,
        profileIsf: Double,
        tdd24h: Double = 0.0,
        snsDominance: Double = SNS_DOMINANCE_DEFAULT
    ): Profile {
        // 1. fusedIsf — blend do ISF do perfil com o TDD real
        val tddIsf: Double = if (tdd24h > 0) {
            (TDD_TO_ISF_FACTOR / tdd24h).coerceIn(20.0, 400.0)
        } else {
            profileIsf
        }
        val fusedIsf: Double = profileIsf * (1.0 - snsDominance) + tddIsf * snsDominance

        // 2. DIA aprendido — baseado no decaimento do IOB
        val diaHrs: Double = estimateDia(iobValues)

        // 3. Tempo de pico — proporcional ao DIA
        val peakMin: Double = (diaHrs * 60 * DIA_PEAK_RATIO)
            .coerceIn(30.0, 120.0)

        // 4. Atividade IOB atual — derivada simples
        val activityNow: Double = calculateActivityNow(iobValues)

        return Profile(
            fusedIsf = fusedIsf,
            diaHrs = diaHrs,
            peakMin = peakMin,
            activityNow = activityNow
        )
    }

    /**
     * Estima o DIA real baseado na taxa de decaimento exponencial do IOB.
     *
     * Método: calcula a meia-vida do IOB na janela de observação e
     * projeta quanto tempo leva para o IOB chegar a <1% do valor atual.
     * Isto dá resultados realistas (DIA 3-6h para decaimento normal).
     */
    private fun estimateDia(iobValues: List<Double>): Double {
        if (iobValues.size < 3) return DEFAULT_DIA_HRS

        val recentIob: Double = iobValues.last()
        if (recentIob < MIN_VALID_IOB) return DEFAULT_DIA_HRS

        // Usa os últimos ~30% dos pontos para calcular a taxa de decaimento
        val n: Int = max(3, iobValues.size / 3)
        val firstInWindow: Double = iobValues[iobValues.size - n]

        if (firstInWindow <= recentIob) return DEFAULT_DIA_HRS  // IOB subindo ou estável

        // Fração restante após a janela
        val ratio: Double = recentIob / firstInWindow
        if (ratio <= 0 || ratio >= 1) return DEFAULT_DIA_HRS

        // Estimativa de horas na janela (12 ciclos/hora ≈ 5min/ciclo)
        val pointsPerHour: Double = 12.0
        val windowHours: Double = n / pointsPerHour

        if (windowHours <= 0) return DEFAULT_DIA_HRS

        // Decaimento por hora: ratio^(1/windowHours)
        val decayPerHour: Double = Math.pow(ratio, 1.0 / windowHours)

        if (decayPerHour >= 1.0) return DEFAULT_DIA_HRS

        // DIA = ln(0.01) / ln(decayPerHour) — tempo para atingir 1% do IOB atual
        val dia: Double = Math.log(0.01) / Math.log(decayPerHour)

        return dia.coerceIn(2.5, 8.0)
    }

    /**
     * Calcula a atividade IOB atual: derivada simples do IOB.
     * Atividade positiva = insulina ainda agindo.
     * Atividade negativa = IOB declinando.
     */
    private fun calculateActivityNow(iobValues: List<Double>): Double {
        if (iobValues.size < 2) return 0.0

        val last: Double = iobValues.last()
        val prev: Double = iobValues[iobValues.size - 2]

        // Atividade = -dIOB/dt (negativo porque IOB diminuindo = atividade positiva)
        return (prev - last).coerceIn(-0.5, 0.5)
    }

    /**
     * Calcula fusedIsf diretamente, sem precisar de iobValues.
     * Versão simplificada para quando não há histórico de IOB.
     */
    fun calculateFusedIsf(
        profileIsf: Double,
        tdd24h: Double,
        snsDominance: Double = SNS_DOMINANCE_DEFAULT
    ): Double {
        val tddIsf: Double = if (tdd24h > 0) {
            (TDD_TO_ISF_FACTOR / tdd24h).coerceIn(20.0, 400.0)
        } else {
            profileIsf
        }
        return profileIsf * (1.0 - snsDominance) + tddIsf * snsDominance
    }
}
