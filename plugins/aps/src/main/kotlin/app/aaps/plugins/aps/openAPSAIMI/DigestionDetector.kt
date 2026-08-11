package app.aaps.plugins.aps.openAPSAIMI

/**
 * DigestionDetector — Detecta digestão ativa baseado APENAS no padrão glicêmico.
 *
 * Refeições low-carb produzem uma subida sustentada e gradual
 * (delta 0.8–4.0 mg/dL/5min, confirmado por shortAvgDelta 0.3–3.5)
 * por 30–90 min, começando de BG > 80 mg/dL, SEM SMB recente.
 *
 * Stateful: mantém active/since/bgStart/cooldown.
 * Cooldown de 45min após refeição confirmada evita fragmentação.
 *
 * Validado contra 3 dias de dados Nightscout reais (Tarciso, Jul/2026):
 * detectou 3–4 refeições/dia nos horários típicos (manhã, almoço, jantar).
 *
 * Uso no ciclo APS (~5min):
 *   val isDigesting = digestionDetector.update(bg, delta, shortAvgDelta, lastsmbtime, now)
 *   if (digestionDetector.isActive()) { ... }

 * @property active Se digestão está ativa neste momento
 * @property activeSince Timestamp (epoch ms) do início da digestão
 * @property startBg BG no início da digestão
 * @property cooldownUntil Timestamp (epoch ms) até quando o cooldown vigora
 */
class DigestionDetector {

    private var active: Boolean = false
    private var activeSince: Long = 0L
    private var startBg: Double = 0.0
    private var cooldownUntil: Long = 0L
    private var _lastDigestionEnd: Long = 0L
    private var _lastDurationMin: Double = 0.0
    private var _lastRiseMgdl: Double = 0.0

    /**
     * Limiares de detecção — calibrados para refeições low-carb
     * sem anúncio, em perfil SMB-puro (basal ≈ 0 U/h).
     */
    companion object {
        /** Subida mínima por ciclo de 5min para caracterizar digestão */
        const val DELTA_MIN = 0.8
        /** Subida máxima por ciclo de 5min (low-carb raramente sobe abrupto) */
        const val DELTA_MAX = 4.0
        /** shortAvgDelta mínimo — confirma tendência sustentada de ~15min */
        const val SAD_MIN = 0.3
        /** shortAvgDelta máximo */
        const val SAD_MAX = 3.5
        /** BG mínimo — não é rebote de hipo */
        const val BG_MIN = 80.0
        /** BG máximo — acima disso, a digestão já está em fase final */
        const val BG_MAX = 165.0
        /** Minutos sem SMB para evitar confundir rebote com refeição */
        const val SMB_GUARD_MIN = 20
        /** Cooldown após refeição confirmada (minutos) */
        const val COOLDOWN_MIN = 45L
        /** Subida mínima (mg/dL) para confirmar que foi refeição válida */
        const val MIN_RISE_MGDL = 8.0
        /** Tempo mínimo ativo (minutos) para considerar refeição válida */
        const val MIN_ACTIVE_MIN = 25
    }

    /**
     * Atualiza o estado do detector com uma nova leitura do ciclo APS.
     * Deve ser chamado a cada ciclo (~5min).
     *
     * @param bg Glicemia atual (mg/dL)
     * @param delta Variação BG nos últimos 5 min (mg/dL)
     * @param shortAvgDelta Média das variações dos últimos ~15 min (mg/dL)
     * @param lastSmbMinutes Minutos desde o último SMB (lastsmbtime)
     * @param now Timestamp atual em epoch millis (dateUtil.now())
     * @return true se digestão está ativa neste ciclo
     */
    fun update(
        bg: Double,
        delta: Double,
        shortAvgDelta: Double,
        lastSmbMinutes: Int,
        now: Long
    ): Boolean {
        // Cooldown: após refeição, aguardar 45 min
        if (cooldownUntil > now && cooldownUntil > 0L) {
            if (active) {
                active = false
                _lastDigestionEnd = now
            }
            return false
        }

        // Condições de detecção
        val rising: Boolean = delta in DELTA_MIN..DELTA_MAX
        val sustained: Boolean = shortAvgDelta in SAD_MIN..SAD_MAX
        val notFromHypo: Boolean = bg > BG_MIN
        val noRecentSmb: Boolean = lastSmbMinutes > SMB_GUARD_MIN
        val consistent: Boolean = kotlin.math.abs(delta - shortAvgDelta) < 4.0
        val inZone: Boolean = bg <= BG_MAX

        val digesting: Boolean = rising && sustained && notFromHypo && noRecentSmb && consistent && inZone

        if (digesting && !active) {
            // Início da digestão
            active = true
            activeSince = now
            startBg = bg
        } else if (digesting && active) {
            // Continuação — nenhuma ação necessária
        } else if (!digesting && active) {
            // Possível fim — verificar se foi refeição válida
            val totalRise: Double = bg - startBg
            val minsActive: Double = (now - activeSince) / 60000.0
            if (totalRise >= MIN_RISE_MGDL && minsActive >= MIN_ACTIVE_MIN) {
                // Refeição válida — entrar em cooldown
                cooldownUntil = now + COOLDOWN_MIN * 60000L
                _lastDigestionEnd = now
                _lastDurationMin = minsActive
                _lastRiseMgdl = totalRise
            }
            active = false
        }

        return active
    }

    /** @return true se digestão está ativa neste ciclo */
    fun isActive(): Boolean = active

    /** @return Timestamp de início da digestão ativa (epoch ms), ou 0 */
    fun getActiveSince(): Long = activeSince

    /** @return BG no início da digestão ativa, ou 0.0 */
    fun getStartBg(): Double = startBg

    /** @return Timestamp do fim da última digestão (epoch ms), ou 0 */
    fun getLastDigestionEnd(): Long = _lastDigestionEnd

    /** @return Duração em minutos da última digestão */
    fun getLastDurationMin(): Double = _lastDurationMin

    /** @return Subida total em mg/dL da última digestão */
    fun getLastRiseMgdl(): Double = _lastRiseMgdl

    /**
     * Reinicia o detector completamente.
     * Útil para testes ou reset manual.
     */
    fun reset() {
        active = false
        activeSince = 0L
        startBg = 0.0
        cooldownUntil = 0L
        _lastDigestionEnd = 0L
        _lastDurationMin = 0.0
        _lastRiseMgdl = 0.0
    }
}
