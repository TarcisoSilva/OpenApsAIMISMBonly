package app.aaps.plugins.aps.openAPSAIMI.pkpd

import app.aaps.plugins.aps.openAPSAIMI.pkpd.InsulinActionState.*

/**
 * PkpdAbsorptionGuard — Guard de segurança anti-stacking.
 *
 * Reduz SMBs quando a insulina anterior ainda não fez pico.
 * É a proteção mais importante para perfil SMB-puro (basal ≈ 0 U/h)
 * porque previne o principal mecanismo de hipo por stacking.
 *
 * Baseado no DetermineBasalAIMI2.kt (MTR93600), adaptado para
 * a arquitetura do Dev2 sem depender de interfaces AAPS externas.
 *
 * Regras:
 * - INACTIVE/RESIDUAL → 1.0 (insulina já agiu)
 * - PRE_ACTIVATION → 0.5 (risco máximo)
 * - PEAK → 0.7 (ainda há risco)
 * - TAIL → 0.85 (pouco risco)
 * - isMealMode=true → min 0.7 (refeição precisa de SMB)
 * - isConfirmedHighRise=true → min 0.8
 * - bg < targetBg+20 && delta < 0 → ×0.8 adicional (hipo iminente)
 *
 * Uso:
 *   val guard = PkpdAbsorptionGuard.compute(
 *       pkpdRuntime = pkpdRuntime,
 *       windowSinceLastDoseMin = lastBolusAgeMinutes,
 *       bg = bg, delta = delta, ...
 *   )
 *   smbToGive *= guard.factor
 */
object PkpdAbsorptionGuard {

    /** Resultado do guard */
    data class GuardResult(
        /** Fator multiplicativo de segurança (0.0 a 1.0) */
        val factor: Double,
        /** Razão legível para logging */
        val reason: String
    )

    // Fatores base por estado
    private const val FACTOR_INACTIVE = 1.0
    private const val FACTOR_RESIDUAL = 1.0
    private const val FACTOR_PRE_ACTIVATION = 0.5
    private const val FACTOR_PEAK = 0.7
    private const val FACTOR_TAIL = 0.85
    private const val FACTOR_NULL_RUNTIME = 1.0

    // Mínimos para modos especiais
    private const val MEAL_MODE_MIN_FACTOR = 0.7
    private const val HIGH_RISE_MIN_FACTOR = 0.8

    // Hipo iminente
    private const val HYPO_MARGIN = 20.0  // mg/dL acima do target
    private const val HYPO_EXTRA_FACTOR = 0.8

    // Janela mínima sem dose para considerar "insulina ativa"
    private const val MIN_WINDOW_FOR_ACTIVE = 5.0  // minutos

    /**
     * Computa o fator de segurança baseado no estado PK/PD.
     *
     * @param pkpdRuntime Estado PK/PD do ciclo atual (pode ser null)
     * @param windowSinceLastDoseMin Minutos desde o último SMB/bolus
     * @param bg Glicemia atual (mg/dL)
     * @param delta Variação BG (mg/dL/5min)
     * @param shortAvgDelta Média shortAvgDelta (mg/dL/5min)
     * @param targetBg Target BG (mg/dL)
     * @param predBg BG predito eventual (mg/dL)
     * @param isMealMode Digestão ativa? (DigestionDetector.isActive())
     * @param isConfirmedHighRise Subida forte confirmada?
     * @return GuardResult com factor e reason
     */
    fun compute(
        pkpdRuntime: PkPdRuntime?,
        windowSinceLastDoseMin: Double,
        bg: Double,
        delta: Double,
        shortAvgDelta: Double,
        targetBg: Double,
        predBg: Double,
        isMealMode: Boolean,
        isConfirmedHighRise: Boolean
    ): GuardResult {
        // Se sem runtime ou sem insulina ativa recente, não restringir
        if (pkpdRuntime == null || windowSinceLastDoseMin < MIN_WINDOW_FOR_ACTIVE) {
            return GuardResult(FACTOR_NULL_RUNTIME, "No PK/PD data")
        }

        val state = pkpdRuntime.insulinActionState
        val baseReason: String

        // Fator base pelo estado
        val baseFactor: Double = when (state) {
            INACTIVE -> {
                baseReason = "INACTIVE — no active insulin"
                FACTOR_INACTIVE
            }
            RESIDUAL -> {
                baseReason = "RESIDUAL — only residual insulin"
                FACTOR_RESIDUAL
            }
            PRE_ACTIVATION -> {
                baseReason = "PRE-ACTIVATION — insulin has not peaked yet"
                FACTOR_PRE_ACTIVATION
            }
            PEAK -> {
                baseReason = "PEAK — insulin near peak, stacking risk"
                FACTOR_PEAK
            }
            TAIL -> {
                baseReason = "TAIL — insulin declining, low risk"
                FACTOR_TAIL
            }
        }

        var factor = baseFactor
        val reasons = mutableListOf(baseReason)

        // Meal mode: refeição precisa de SMBs mais generosos
        if (isMealMode && factor < MEAL_MODE_MIN_FACTOR) {
            factor = MEAL_MODE_MIN_FACTOR
            reasons.add("Meal mode: min ${"%.1f".format(MEAL_MODE_MIN_FACTOR)}")
        }

        // High rise: subida forte precisa de ação
        if (isConfirmedHighRise && factor < HIGH_RISE_MIN_FACTOR) {
            factor = HIGH_RISE_MIN_FACTOR
            reasons.add("High rise: min ${"%.1f".format(HIGH_RISE_MIN_FACTOR)}")
        }

        // ── HIPER-MIN HIPO 0 (24/08/2026) ──
        val hyperMin = when {
            bg > 180 && delta > 2.5 && shortAvgDelta > 2.0 && predBg > targetBg + 40 -> 0.90
            bg > 160 && delta > 1.5 && predBg > targetBg + 40 -> 0.85
            bg > 150 && delta > 0.5 -> 0.80
            else -> factor
        }
        if (hyperMin > factor && bg >= targetBg + 40 && delta > 0 && !(bg < targetBg + HYPO_MARGIN && delta < 0)) {
            factor = hyperMin
            reasons.add("Hyper min $hyperMin")
        }
        // Hipo iminente: bg próximo do target e caindo
        if (bg < targetBg + HYPO_MARGIN && delta < 0) {
            factor *= HYPO_EXTRA_FACTOR
            reasons.add("Hypo imminent: ×${"%.1f".format(HYPO_EXTRA_FACTOR)}")
        }

        return GuardResult(
            factor = factor.coerceIn(0.0, 1.0),
            reason = reasons.joinToString("; ")
        )
    }
}
