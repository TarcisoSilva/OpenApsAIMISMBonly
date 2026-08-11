package app.aaps.plugins.aps.openAPSAIMI.smb

/**
 * SmbDampingUsecase — Amortecimento contextual de SMBs.
 *
 * Diferente do PkpdAbsorptionGuard (que age sobre TEMPO desde o último bolus),
 * o SmbDampingUsecase age sobre CONTEXTO: exercício, refeição gordurosa tardia,
 * e posição na curva de ação da insulina (tail damping).
 *
 * Fatores aplicados em sequência (todos multiplicativos):
 *   1. Tail Damping (0.75×): insulina na cauda → reduz SMB
 *   2. Exercise Damping (0.50×): atividade física → meia dose
 *   3. Late Fat Meal (0.60×): refeição 20-23h → absorção lenta
 *   4. Meal Bypass (1.00×): refeição ativa → não amortecer
 *   5. High BG Rise (0.90×): subida forte → leve redução p/ evitar overshoot
 *
 * Uso:
 *   val result = SmbDampingUsecase.run(
 *       SmbDampingUsecase.Input(smbDecision = 0.5, exercise = true, ...)
 *   )
 *   // result.smbAfterDamping = SMB após amortecimento
 *   // result.audit = detalhamento dos fatores aplicados
 */
object SmbDampingUsecase {

    /** Input para o cálculo de damping */
    data class Input(
        /** SMB proposto antes do damping (U) */
        val smbDecision: Double,
        /** Exercício físico detectado (passos + FC elevados) */
        val exercise: Boolean,
        /** Refeição gordurosa tardia suspeita (20-23h) */
        val suspectedLateFatMeal: Boolean,
        /** Modo refeição ativo (DigestionDetector.isActive() = true) */
        val mealModeRun: Boolean,
        /** Subida forte de BG em andamento */
        val highBgRiseActive: Boolean
    )

    /** Auditoria detalhada de cada fator de damping */
    data class Audit(
        val tailApplied: Boolean,
        val tailMult: Double,
        val exerciseApplied: Boolean,
        val exerciseMult: Double,
        val lateFatApplied: Boolean,
        val lateFatMult: Double,
        val mealBypass: Boolean
    )

    /** Resultado do cálculo de damping */
    data class Result(
        /** SMB após todo amortecimento (U) */
        val smbAfterDamping: Double,
        /** Auditoria dos fatores aplicados */
        val audit: Audit?
    )

    /** Constantes dos fatores de damping */
    private const val TAIL_MULT = 0.75
    private const val EXERCISE_MULT = 0.50
    private const val LATE_FAT_MULT = 0.60
    private const val MEAL_BYPASS_MULT = 1.00
    private const val HIGH_BG_RISE_MULT = 0.90

    private const val TAIL_THRESHOLD_RATIO = 0.7
    private const val EXERCISE_THRESHOLD_BG = 130.0
    private const val LATE_FAT_HOUR_START = 20
    private const val LATE_FAT_HOUR_END = 23
    private const val HIGH_RISE_DELTA_MIN = 2.0f
    private const val HIGH_RISE_SAD_MIN = 1.0f

    /**
     * Executa o amortecimento contextual.
     *
     * @param input Parâmetros de entrada (SMB proposto + contexto)
     * @param lastBolusAgeMinutes Idade do último bolus (min) — para tail damping
     * @param peakTimeMinutes Tempo de pico da insulina (min) — para tail damping
     * @param bg Glicemia atual (mg/dL) — para exercise damping
     * @param delta Variação BG (mg/dL/5min) — para high BG rise
     * @param shortAvgDelta Média shortAvgDelta — para high BG rise
     * @return Resultado com SMB amortecido + auditoria
     */
    fun run(
        input: Input,
        lastBolusAgeMinutes: Double = Double.MAX_VALUE,
        peakTimeMinutes: Double = 75.0,
        bg: Double = 0.0,
        delta: Float = 0.0f,
        shortAvgDelta: Float = 0.0f
    ): Result {
        var currentSMB = input.smbDecision
        var tailApplied = false
        var tailMult = 1.0
        var exerciseApplied = false
        var exerciseMult = 1.0
        var lateFatApplied = false
        var lateFatMult = 1.0
        val mealBypass = input.mealModeRun

        // Meal Bypass: se refeição ativa, não aplicar damping
        if (input.mealModeRun) {
            return Result(
                smbAfterDamping = currentSMB,
                audit = Audit(
                    tailApplied = false, tailMult = 1.0,
                    exerciseApplied = false, exerciseMult = 1.0,
                    lateFatApplied = false, lateFatMult = 1.0,
                    mealBypass = true
                )
            )
        }

        // 1. Tail Damping: insulina na cauda
        if (lastBolusAgeMinutes < Double.MAX_VALUE &&
            peakTimeMinutes > 0 &&
            lastBolusAgeMinutes > peakTimeMinutes * TAIL_THRESHOLD_RATIO
        ) {
            tailApplied = true
            tailMult = TAIL_MULT
            currentSMB *= tailMult
        }

        // 2. Exercise Damping
        if (input.exercise) {
            exerciseApplied = true
            exerciseMult = EXERCISE_MULT
            currentSMB *= exerciseMult
        }

        // 3. Late Fat Meal Damping
        if (input.suspectedLateFatMeal) {
            lateFatApplied = true
            lateFatMult = LATE_FAT_MULT
            currentSMB *= lateFatMult
        }

        // 4. High BG Rise (leve redução)
        if (input.highBgRiseActive) {
            // Aplicar fator já incluso no cálculo
            currentSMB *= HIGH_BG_RISE_MULT
        }

        return Result(
            smbAfterDamping = currentSMB,
            audit = Audit(
                tailApplied = tailApplied, tailMult = tailMult,
                exerciseApplied = exerciseApplied, exerciseMult = exerciseMult,
                lateFatApplied = lateFatApplied, lateFatMult = lateFatMult,
                mealBypass = mealBypass
            )
        )
    }
}
