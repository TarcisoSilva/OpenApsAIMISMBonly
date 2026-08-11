package app.aaps.plugins.aps.openAPSAIMI

/**
 * BgConfidenceGuard — Travas de segurança da rede neural de confiança do BG.
 *
 * A rede pode errar. Estas regras LIMITAM o impacto de um erro:
 * fail-safe: quando em dúvida, o sistema assume que o BG é CONFIÁVEL
 * (não mascarar queda real — o erro perigoso é tratar hipo como ruído).
 *
 * TRAVAS (Tarciso, 08/Ago/2026):
 *   Trava 1 — Nunca mascarar hipo: BG < 80 é sempre OK (confiável).
 *             O sensor é menos preciso abaixo de 70-80, mas por segurança
 *             o valor lido é considerado verdadeiro (hipo real prevalece).
 *   Trava 2 — Nunca mascarar queda rápida: delta < -4 é sempre OK.
 *             Uma queda real não pode ser classificada como ruído.
 *   Trava 3 — Hiper sempre tratada: BG > 250 e BAD → vira UNCERTAIN.
 *             REMOVÍVEL no futuro: se a rede funcionar bem em testes,
 *             pode-se remover esta trava para maior benefício (Tarciso
 *             observa que o sensor erra mais acima de 160-180 também).
 *             Para remover: descomente a linha em applySafety().
 *   Trava 4 — Sensor < 6h nunca BAD (já protegido pela trava de troca
 *             de sensor — não punir 2x).
 */
object BgConfidenceGuard {

    /** Trava 1: abaixo deste BG, nunca marcar como BAD/UNCERTAIN. */
    private const val SAFETY_BG_LOW = 80.0

    /** Trava 2: abaixo deste delta, nunca marcar como BAD/UNCERTAIN. */
    private const val SAFETY_DELTA_NEG = -4.0

    /** Trava 3: acima deste BG, BAD vira UNCERTAIN (não zera hiper). */
    private const val SAFETY_BG_HIGH = 250.0

    /** Trava 3 ativa? TRUE = proteger hiper; FALSE = rede decide livremente.
     *  Marque FALSE após validação se a rede se mostrar confiável na hiper. */
    private const val TRAVA_3_HIPER_ATIVA = true

    /** Trava 5 (Melhoria B, 11/Ago/2026): acima deste delta, subida nunca BAD. */
    private const val SAFETY_RISE_DELTA = 1.0

    /** Trava 6 (Melhoria F, 11/Ago/2026): acima deste delta, leitura é RUÍDO provável.
     *  Calibrado com o verão NS (30/11/25-31/01/26): deltas >10 = 1,5% dos ciclos;
     *  máx +58,8 é fisiologicamente impossível → erro de sensor no calor. */
    private const val SAFETY_DELTA_RUIDO = 10.0

    /** Trava 7 (Melhoria F, 11/Ago/2026): acima deste delta, zona de SUSPEITA.
     *  Calibrado: deltas 7-10 = 3,75% dos ciclos no verão (~11/dia). */
    private const val SAFETY_DELTA_SUSPEITO = 7.0

    /**
     * Aplica as travas de segurança sobre o veredito bruto da rede.
     * @param rawTier veredito da rede (0=OK, 1=UNCERTAIN, 2=BAD)
     * @param bg glicemia atual (mg/dL)
     * @param delta variação 5min (mg/dL)
     * @return tier final após travas (0=OK, 1=UNCERTAIN, 2=BAD)
     */
    fun applySafety(rawTier: Int, bg: Double, delta: Double): Int {
        var tier = rawTier

        // TRAVA 1 — Nunca mascarar hipo: BG < 80 é sempre confiável
        if (bg < SAFETY_BG_LOW) return 0

        // TRAVA 2 — Nunca mascarar queda rápida: delta < -4 é sempre confiável
        if (delta < SAFETY_DELTA_NEG) return 0

        // TRAVA 3 — Hiper sempre tratada: BG > 250, BAD vira UNCERTAIN
        if (TRAVA_3_HIPER_ATIVA && bg > SAFETY_BG_HIGH && tier == 2) {
            tier = 1
        }

        // TRAVA 5 (Melhoria B) — Subida sustentada nunca BAD: divergência moderada
        // com delta > 1 é LAG fisiológico de refeição, não ruído.
        if (delta > SAFETY_RISE_DELTA && tier == 2) {
            tier = 1
        }

        // TRAVA 6 (Melhoria F) — delta > 10 é RUÍDO provável. Por REGRA, não depende
        // da rede (treino tem 0 exemplos de delta > 6). Vence a Trava 5 (ruído puro,
        // mesmo em subida).
        if (delta > SAFETY_DELTA_RUIDO) return 2

        // TRAVA 7 (Melhoria F) — delta 7-10: zona de SUSPEITA → no mínimo UNCERTAIN.
        if (delta > SAFETY_DELTA_SUSPEITO && tier == 0) {
            tier = 1
        }

        return tier
    }

    /**
     * Multiplicador de SMB por tier de confiança.
     * BAD → × 0.5 (cautela, NÃO zera) | UNCERTAIN → × 0.8 | OK → × 1.0
     *
     * Melhoria D (REVISADA 11/Ago/2026): o usuário NÃO usa COB (não anuncia refeições),
     * então o "lag esperado" é detectado pelo DigestionDetector (isDigesting) ou por
     * subida real (delta > 1). Nesses casos BAD é elevado a ×0.8 (piso) — lag fisiológico
     * de refeição nunca é punido como ruído.
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
