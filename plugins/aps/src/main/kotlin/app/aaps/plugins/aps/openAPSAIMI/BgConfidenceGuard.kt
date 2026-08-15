package app.aaps.plugins.aps.openAPSAIMI

import kotlin.math.abs

/**
 * BgConfidenceGuard — Travas de segurança da rede neural de confiança do BG.
 *
 * Versão Refatorada (12/Ago/2026):
 *  - Suporte total ao Item 7 (Opção A-v2: BG Corrigido com Âncora por Delta)
 *  - Aplicação de âncora na leitura anterior para picos de subida anômalos em tier 1 e tier 2
 *  - Fail-safe preservado: BG < 80 e delta < -4 continuam forçando Tier 0 (nunca mascaram hipo)
 *
 * TRAVA 8 (14/Ago/2026, Decisão T7=7+V5):
 *  - Recuperação anômala pós-queda (SEQ A): prevDelta < 0 + delta >= 5 + delta descolado
 *    do shortAvgDelta (|delta| > 2 * |shortAvg|) força no mínimo UNCERTAIN (nunca bloqueia)
 *  - Aplica-se a deltas 5-7 onde a TRAVA 7 (delta > 7) não alcança
 */
object BgConfidenceGuard {

    private const val SAFETY_BG_LOW = 80.0
    private const val SAFETY_DELTA_NEG = -4.0
    private const val SAFETY_BG_HIGH = 250.0
    private const val TRAVA_3_HIPER_ATIVA = true
    private const val SAFETY_RISE_DELTA = 1.0
    private const val SAFETY_DELTA_RUIDO = 10.0
    // Delta Suspeito — valor padrão, pode ser sobrescrito por parâmetro
    // (padrão 7.0 mg/dL, configurável via Preferências AIMI)
    const val DEFAULT_DELTA_SUSPEITO = 7.0

    /**
     * Aplica as travas de segurança sobre o veredito bruto da rede.
     */
    fun applySafety(
        rawTier: Int,
        bg: Double,
        delta: Double,
        prevDelta: Double = 0.0,
        shortAvgDelta: Double = 0.0,
        deltaSuspeito: Double = DEFAULT_DELTA_SUSPEITO
    ): Int {
        var tier = rawTier

        // TRAVA 1 — Nunca mascarar hipo: BG < 80 é sempre confiável
        if (bg < SAFETY_BG_LOW) return 0

        // TRAVA 2 — Nunca mascarar queda rápida: delta < -4 é sempre confiável
        if (delta < SAFETY_DELTA_NEG) return 0

        // TRAVA 3 — Hiper sempre tratada: BG > 250, BAD vira UNCERTAIN
        if (TRAVA_3_HIPER_ATIVA && bg > SAFETY_BG_HIGH && tier == 2) {
            tier = 1
        }

        // TRAVA 5 — Subida sustentada nunca BAD: divergência moderada com delta > 1 é LAG de refeição
        if (delta > SAFETY_RISE_DELTA && tier == 2) {
            tier = 1
        }

        // TRAVA 6 — delta > 10 é RUÍDO provável. Por REGRA, não depende da rede
        if (delta > SAFETY_DELTA_RUIDO) return 2

        // TRAVA 7 — delta > limiar configurável: zona de SUSPEITA → no mínimo UNCERTAIN
        // Default: 7.0 mg/dL (configurável em Preferências → AIMI → Delta Suspeito)
        if (delta > deltaSuspeito && tier == 0) {
            tier = 1
        }

        // TRAVA 8 — Recuperação anômala pós-queda (Decisão 14/Ago/2026: T7=7+V5)
        // Rebote após queda (prevDelta < 0) com delta >= 5 descolado da tendência recente:
        // força no mínimo UNCERTAIN. Nunca bloqueia (maxOf). Cobra deltas 5-7 que a TRAVA 7 não vê.
        if (prevDelta < 0.0 && delta >= 5.0 && abs(delta) > abs(shortAvgDelta) * 2.0) {
            tier = maxOf(tier, 1)
        }

        return tier
    }

    /**
     * Item 7 — Opção A-v2 (BG Corrigido com Âncora por Delta)
     *
     * Calcula o BG de trabalho suavizado/corrigido que alimenta o PD Controller
     * e o modelo TFLite antes do cálculo da dose.
     *
     * @param bg Glicemia atual (mg/dL)
     * @param delta Variação de 5 minutos (mg/dL)
     * @param shortAvgDelta Média curta da variação (mg/dL)
     * @param tier Nível de confiança (0=OK, 1=UNCERTAIN, 2=BAD)
     * @return BG Corrigido para ser utilizado na dosagem
     */
    fun calculateCorrectedBg(
        bg: Double,
        delta: Double,
        shortAvgDelta: Double,
        tier: Int
    ): Double {
        // Detecção de pico falso anômalo: delta alto e descolado da tendência recente
        val anomalo = abs(delta) > 6.0 && abs(delta) > abs(shortAvgDelta) * 2.0

        return when {
            // Tier 2 (BAD): Retro-projeta para a leitura anterior (bg - delta)
            tier == 2 -> (bg - delta).coerceAtLeast(39.0)

            // Tier 1 (UNCERTAIN) + Spike Anômalo: Força âncora na leitura anterior (bg - delta)
            tier == 1 && anomalo -> (bg - delta).coerceAtLeast(39.0)

            // Tier 1 (UNCERTAIN) sutil: Suaviza atenuando 50% da tendência
            tier == 1 -> (bg - 0.5 * shortAvgDelta).coerceAtLeast(39.0)

            // Tier 0 (OK): Mantém a leitura de BG intacta
            else -> bg
        }
    }

    /**
     * Multiplicador de SMB por tier de confiança.
     */
    fun smbMultiplier(tier: Int, isDigesting: Boolean = false, delta: Double = 0.0): Double {
        val lagEsperado = isDigesting || delta > 1.0
        return when (tier) {
            2 -> if (lagEsperado) 0.8 else 0.5
            1 -> 0.8
            else -> 1.0
        }
    }
}
