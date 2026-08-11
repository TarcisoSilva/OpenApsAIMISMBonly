package app.aaps.plugins.aps.openAPSAIMI

import android.os.Environment
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import app.aaps.database.ValueWrapper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * HourlyAdjustWorker — Worker isolado que analisa as últimas 4h de glicemia
 * e SMBs, e ajusta persistentemente os hourly factors (24 valores horários)
 * para corrigir desvios recorrentes.
 *
 * Princípios de segurança:
 * - Roda em background (fire-and-forget), NUNCA bloqueia o ciclo APS
 * - Toggle on/off nas preferências (default OFF)
 * - Decaimento gradual de ajustes após 3 dias sem eventos
 * - Limite de ±30 pontos de ajuste acumulado por hora
 * - Piso absoluto de 9 (nunca reduz below)
 * - Proteção contra bouncing (hipo seguida de hiper = ignora ambos)
 * - Log JSON de cada execução para auditoria
 */
@Singleton
class HourlyAdjustWorker @Inject constructor(
    private val sp: SP,
    private val repository: AppRepository,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
) {

    companion object {
        // Constantes de análise
        /** Intervalo de retrospecto nas últimas 4h. */
        private const val ANALYSIS_LOOKBACK_MS = 4 * 60 * 60 * 1000L    // 4 horas de retrospecto
        private const val HYPOGLYCEMIA_THRESHOLD = 69.0                   // mg/dL
        private const val HYPERGLYCEMIA_THRESHOLD = 155.0                 // mg/dL
        private const val SMB_LOOKBACK_WINDOW_MS = 120 * 60 * 1000L      // 2h antes do evento
        
        // ════════════════════════════════════════════════════════════════
        // Overshoot detection constants (Tarciso, Jul/2026)
        // ════════════════════════════════════════════════════════════════
        /** Janela expandida para detectar overshoot: SMBs até 4h antes da hipo. */
        private const val OVERSHOOT_LOOKBACK_WINDOW_MS = 240 * 60 * 1000L     // 4h antes do evento
        /** Threshold: se total SMB > 1.5U no período, é considerado overshoot. */
        private const val OVERSHOOT_SMB_THRESHOLD = 1.5                        // unidades de insulina
        // ════════════════════════════════════════════════════════════════
        
        // ════════════════════════════════════════════════════════════════
        // Morning hypo extended overshoot (Tarciso, Jul/2026)
        // ════════════════════════════════════════════════════════════════
        /** Janela estendida (8h) para overshoot matinal (hipo entre 4-10h BRT). */
        private const val OVERSHOOT_LOOKBACK_WINDOW_MORNING_MS = 480 * 60 * 1000L    // 8h
        // ════════════════════════════════════════════════════════════════
        
        private const val REBOUND_WINDOW_MS = 120 * 60 * 1000L            // 2h após hipo
        
        // ════════════════════════════════════════════════════════════════
        // Cannula protection constants (Tarciso, Jul/2026)
        // ════════════════════════════════════════════════════════════════
        /** Se a canula foi trocada ha menos que este tempo, o auto-adjust e bloqueado. */
        private const val CANNULA_AGE_THRESHOLD_MINUTES = 300L             // 5 horas
        /** Threshold de BG para bloqueio: so bloqueia se BG < 170 (canula problematica causa hiper). */
        private const val CANNULA_BG_THRESHOLD = 170.0                     // mg/dL
        // ════════════════════════════════════════════════════════════════
        // Sensor change protection constants (Tarciso, Ago/2026)
        // ════════════════════════════════════════════════════════════════
        /** Se o sensor foi trocado ha menos que este tempo, o auto-adjust e bloqueado.
         *  12h inicial — evoluir para 24h se necessario. */
        private const val SENSOR_AGE_THRESHOLD_MINUTES = 720L              // 12 horas
        // ════════════════════════════════════════════════════════════════
        
        // ════════════════════════════════════════════════════════════════
        // Prolonged elevation detection constants (Tarciso, Jul/2026)
        // ════════════════════════════════════════════════════════════════
        /** Threshold para considerar leitura elevada. */
        private const val PROLONGED_ELEVATION_THRESHOLD = 140.0             // mg/dL
        /** Fração mínima de leituras elevadas para disparar (>50%). */
        private const val PROLONGED_ELEVATION_MIN_RATIO = 0.50              // 50%
        /** Número mínimo de leituras elevadas para evitar falso positivo. */
        private const val PROLONGED_ELEVATION_MIN_COUNT = 3                 // mínimo de leituras
        // ════════════════════════════════════════════════════════════════
        
        // Constantes de ajuste
        private const val ADJUSTMENT_STEP = 15.0                          // pontos por evento (aumentado de 10→15 Jul/2026)
        private const val ABSOLUTE_FLOOR = 9.0                             // piso absoluto
        private const val MAX_CUMULATIVE_ADJUSTMENT = 30.0                 // ±30 pts acumulados totais
        /** 
         * Fração do valor atual permitida como ajuste por execução (por hora). 
         * 25% do valor atual garante que o ajuste escale com a magnitude:
         *   - Valor 250 → cap de ±63 (proporcional, não precisa de ±30 cumulativo aqui)
         *   - Valor 95  → cap de ±24 
         *   - Valor 10  → cap de ±2.5 (suave, evita overshoot perto do piso)
         * Se o padrão se repetir em dias seguidos, o ajuste acumula gradualmente.
         */
        private const val PER_RUN_ADJUSTMENT_PERCENT = 0.25               // 25% do valor atual por execução
        private const val MAX_JUMP_PERCENT_FROM_CURRENT = 0.5             // salvaguarda: novo valor ≤ ±50% do valor REAL da SP (Correção C)
        private const val DECAY_DAYS_THRESHOLD = 3                         // dias sem evento para decair
        private const val DECAY_RATE = 0.5                                 // 50% do ajuste acumulado
        private const val DECAY_ZERO_THRESHOLD = 5.0                       // abaixo disto, zera
        private const val MIN_CONFIDENCE_EVENTS = 2                        // 2+ eventos = ajuste cheio
        
        // SharedPreferences keys (hardcoded para consistência — os R.string equivalents existem)
        private const val SP_AUTO_ADJUST_ENABLED = "key_aimi_auto_adjust"
        private const val SP_ADJUST_STATE = "key_aimi_auto_adjust_state"
        private const val SP_ADJUST_LOG = "key_aimi_auto_adjust_log"
        private const val SP_MAGNITUDE_COLLECT = "key_aimi_magnitude_collect"
        /** Seasonal offset key (%): positivo = menos agressivo (verão), negativo = mais agressivo (inverno). */
        private const val SP_SEASONAL_OFFSET = "key_aimi_seasonal_offset"
        /** Hypo trigger threshold (mg/dL) — configurável pelo usuário. */
        private const val SP_HYPO_THRESHOLD = "key_aimi_auto_adjust_hypo_threshold"
        /** Hyper trigger threshold (mg/dL) — configurável pelo usuário. */
        private const val SP_HYPER_THRESHOLD = "key_aimi_auto_adjust_hyper_threshold"

        // CSV de coleta de magnitudes
        private val MAGNITUDE_CSV = File(
            Environment.getExternalStorageDirectory(),
            "AAPS/oapsaimi_magnitude.csv"
        )
        private const val MAGNITUDE_CSV_HEADER =
            "timestamp,hour,oldValue,newValue,delta,eventType,rawDelta," +
            "effectiveDelta,appliedDelta,totalDelta,confidence,eventCount"
        
        private fun getHourlyPreferenceKey(hourOfDay: Int): String {
            val suffix = hourOfDay.toString().padStart(2, '0')
            return "key_oaps_aimi_hourly_percentage_$suffix"
        }
    }

    /**
     * Estado persistido por hora do dia.
     * Salvo em JSON na SharedPreferences (chave SP_ADJUST_STATE).
     */
    data class HourlyState(
        val originalValue: Double = 50.0,
        val totalDelta: Double = 0.0,
        val lastEventTimestamp: Long = 0L,
        val eventCount: Int = 0
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("originalValue", originalValue)
            put("totalDelta", totalDelta)
            put("lastEventTimestamp", lastEventTimestamp)
            put("eventCount", eventCount)
        }

        companion object {
            fun fromJson(json: JSONObject): HourlyState = HourlyState(
                originalValue = json.optDouble("originalValue", 50.0),
                totalDelta = json.optDouble("totalDelta", 0.0),
                lastEventTimestamp = json.optLong("lastEventTimestamp", 0L),
                eventCount = json.optInt("eventCount", 0)
            )

            fun empty() = HourlyState()
        }
    }

    /**
     * Evento de glicemia detectado (hipo ou hiper).
     */
    data class BgEvent(
        val timestamp: Long,
        val value: Double,
        val type: EventType,
        val isProlonged: Boolean = false
    ) {
        enum class EventType { HYPO, HYPER }
    }

    /**
     * Resultado de uma rodada de análise — usado para log.
     */
    data class AnalysisReport(
        val timestamp: Long,
        val lookbackStart: Long,
        val lookbackEnd: Long,
        val hypoEvents: List<BgEvent>,
        val hyperEvents: List<BgEvent>,
        val bounceIgnored: Int,
        val adjustments: List<AdjustmentLog>,
        val decays: List<DecayLog>
    ) {
        data class AdjustmentLog(
            val hour: Int,
            val oldValue: Double,
            val newValue: Double,
            val delta: Double,
            val reason: String
        )

        data class DecayLog(
            val hour: Int,
            val previousDelta: Double,
            val decayedDelta: Double
        )

        fun toJson(dateUtil: DateUtil): String = JSONObject().apply {
            put("timestamp", dateUtil.dateAndTimeString(timestamp))
            put("period_start", dateUtil.dateAndTimeString(lookbackStart))
            put("period_end", dateUtil.dateAndTimeString(lookbackEnd))
            put("hypo_events", JSONArray().apply {
                hypoEvents.forEach { e ->
                    put(JSONObject().apply {
                        put("time", dateUtil.timeString(e.timestamp))
                        put("value", e.value)
                        put("type", e.type.name)
                    })
                }
            })
            put("hyper_events", JSONArray().apply {
                hyperEvents.forEach { e ->
                    put(JSONObject().apply {
                        put("time", dateUtil.timeString(e.timestamp))
                        put("value", e.value)
                        put("type", e.type.name)
                    })
                }
            })
            put("bounce_ignored", bounceIgnored)
            put("adjustments", JSONArray().apply {
                adjustments.forEach { a ->
                    put(JSONObject().apply {
                        put("hour", a.hour)
                        put("old_value", a.oldValue)
                        put("new_value", a.newValue)
                        put("delta", a.delta)
                        put("reason", a.reason)
                    })
                }
            })
            put("decays", JSONArray().apply {
                decays.forEach { d ->
                    put(JSONObject().apply {
                        put("hour", d.hour)
                        put("previous_delta", d.previousDelta)
                        put("decayed_delta", d.decayedDelta)
                    })
                }
            })
        }.toString(2)
    }

    /**
     * Registro de um ajuste com contexto — usado para treinar o modelo de magnitude.
     *
     * Correção A (31/Jul/2026): o campo `cappedDelta` (que gravava o totalDelta
     * ACUMULADO, enganoso para análise — parecia amplificação/sinal invertido)
     * foi substituído por dois campos semânticos claros:
     *   - appliedDelta: incremento REAL aplicado ao fator NESTA execução
     *     (= newValue - currentValue, já sujeito a perRunCap + MAX_CUMULATIVE)
     *   - totalDelta:    totalDelta acumulado do estado (para diagnóstico)
     */
    data class MagnitudeRecord(
        val timestamp: Long,
        val hour: Int,
        val oldValue: Double,
        val newValue: Double,
        val delta: Double,
        val eventType: String,        // "HYPO", "HYPER", "BOTH"
        val rawDelta: Double,         // soma dos ±15 sem confidence (pré-nightFactor)
        val effectiveDelta: Double,   // após confidence factor (pós-nightFactor)
        val appliedDelta: Double,     // incremento REAL aplicado nesta execução
        val totalDelta: Double,       // totalDelta acumulado do estado
        val confidenceFactor: Double,
        val eventCount: Int
    ) {
        fun toCsvRow(): String =
            "$timestamp,$hour,$oldValue,$newValue,$delta," +
            "$eventType,$rawDelta,$effectiveDelta,$appliedDelta,$totalDelta," +
            "$confidenceFactor,$eventCount"
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Executa uma rodada completa de análise e ajuste.
     * Seguro para chamar de qualquer thread (roda em fire-and-forget).
     * Se o toggle estiver OFF, retorna imediatamente sem fazer nada.
     */
    fun runAnalysis(): String? {
        if (!isEnabled()) {
            aapsLogger.debug(LTag.APS, "Auto-Adjust: disabled by preference, skipping")
            return null
        }

        val now = System.currentTimeMillis()
        val lookbackStart = now - ANALYSIS_LOOKBACK_MS
        val hypoEvents = mutableListOf<BgEvent>()
        val hyperEvents = mutableListOf<BgEvent>()
        val adjustments = mutableListOf<AnalysisReport.AdjustmentLog>()
        val decays = mutableListOf<AnalysisReport.DecayLog>()

        try {
            // 1. Buscar dados glicêmicos e SMBs do banco Room
            val bgData = fetchBgData(lookbackStart, now)
            val smbData = fetchSmbData(lookbackStart, now)

            if (bgData.isEmpty()) {
                aapsLogger.warn(LTag.APS, "Auto-Adjust: No BG data available for analysis period")
                return null
            }

            // ════════════════════════════════════════════════════════════════
            // Sensor change protection (Tarciso, Ago/2026)
            // ════════════════════════════════════════════════════════════════
            // Após troca de sensor, as leituras podem estar instáveis por até 12h
            // (ex: BG falso 39-100 observado em 07/08 → ajustou fatores errados).
            // O auto-ajuste NÃO deve aprender com dados não confiáveis.
            // Usa o timestamp INFORMADO pelo usuário — se informou data retroativa,
            // a idade já passa de 12h e o bloqueio NÃO ativa (sensor já estável).
            if (isSensorProtectionActive(bgData)) {
                val sensorAge = getSensorAgeMinutes()
                val msg = "Auto-Adjust: Bloqueado por idade do sensor " +
                    "(sensor ha ${sensorAge}min, estabilizando)"
                aapsLogger.debug(LTag.APS, msg)
                try {
                    repository.runTransaction(
                        InsertIfNewByTimestampTherapyEventTransaction(
                            timestamp = now,
                            type = TherapyEvent.Type.ANNOUNCEMENT,
                            note = msg,
                            enteredBy = "OpenApsAIMI",
                            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                        )
                    ).subscribe()
                } catch (_: Exception) {}
                return msg
            }

            // ════════════════════════════════════════════════════════════════
            // Cannula protection (Tarciso, Jul/2026)
            // ════════════════════════════════════════════════════════════════
            // Se a canula foi trocada ha < 5h e BG < 170, bloqueia o ajuste:
            // o problema é mecânico (canula), não de tuning.
            if (isCannulaProtectionActive(bgData)) {
                val cannulaAge = getCannulaAgeMinutes()
                val msg = "Auto-Adjust: Bloqueado por idade da canula " +
                    "(canula ha ${cannulaAge}min, BG < ${CANNULA_BG_THRESHOLD.toInt()})"
                aapsLogger.debug(LTag.APS, msg)
                try {
                    repository.runTransaction(
                        InsertIfNewByTimestampTherapyEventTransaction(
                            timestamp = now,
                            type = TherapyEvent.Type.ANNOUNCEMENT,
                            note = msg,
                            enteredBy = "OpenApsAIMI",
                            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                        )
                    ).subscribe()
                } catch (_: Exception) {}
                return msg
            }

            // 2. Detectar eventos de hipo (<65) e hiper (>150)
            hypoEvents.addAll(detectHypoEvents(bgData))
            hyperEvents.addAll(detectHyperEvents(bgData))

            // 2a. Detectar near-miss (quase-hipo): BG 70-85 com delta negativo
            // (Tarciso, Jul/2026 — complementa detectHypoEvents para capturar
            //  situações onde o alarme de low glucose dispararia mas o threshold
            //  fixo de 69 não é atingido)
            hypoEvents.addAll(detectNearMissEvents(bgData))

            // 2b. Detectar elevação prolongada (Tarciso, Jul/2026)
            // Se >50% das leituras > 140mg/dL nas últimas 4h, adiciona
            // um evento de hiper com ajuste reduzido (tiers de severidade).
            detectProlongedElevation(bgData)?.let { prolongedEvent ->
                hyperEvents.add(prolongedEvent)
                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Prolonged elevation detected " +
                    "(${(bgData.count { it.value > PROLONGED_ELEVATION_THRESHOLD }.toDouble() / bgData.size * 100).roundToInt()}% > 140)")
            }

            aapsLogger.debug(LTag.APS,
                "Auto-Adjust: ${hypoEvents.size} hypo(s), ${hyperEvents.size} hyper(s) in last 4h")

            // 3. Proteção contra bouncing: remover pares hipo→hiper em janela de 2h
            val bounceCount = removeBounceEvents(hypoEvents, hyperEvents)
            if (bounceCount > 0) {
                aapsLogger.debug(LTag.APS, "Auto-Adjust: $bounceCount bounce event(s) ignored")
            }

            // 4. Correlacionar eventos com SMBs e gerar ajustes propostos
            val hypoAdjustments = correlateHypoAdjustments(hypoEvents, smbData)
            val hyperAdjustments = correlateHyperAdjustments(hyperEvents, smbData)

            // 5. Aplicar decaimento e ajustes com limites de segurança
            val magnitudeRecords = mutableListOf<MagnitudeRecord>()
            applyDecayAndAdjustments(
                hypoAdjustments, hyperAdjustments,
                adjustments, decays, magnitudeRecords
            )

            // 5b. Flush CSV de magnitudes (se toggle ON e houver registros)
            if (magnitudeRecords.isNotEmpty()) {
                try {
                    val csvHeaderWritten = sp.getBoolean("mag_csv_header_written", false)
                    if (!csvHeaderWritten) {
                        MAGNITUDE_CSV.appendText(MAGNITUDE_CSV_HEADER + "\n")
                        sp.putBoolean("mag_csv_header_written", true)
                    }
                    magnitudeRecords.forEach { record ->
                        MAGNITUDE_CSV.appendText(record.toCsvRow() + "\n")
                    }
                } catch (e: Exception) {
                    aapsLogger.warn(LTag.APS, "Auto-Adjust: Failed to write magnitude CSV", e)
                }
            }

            // 6. Persistir log da execução
            val report = AnalysisReport(
                timestamp = now,
                lookbackStart = lookbackStart,
                lookbackEnd = now,
                hypoEvents = hypoEvents,
                hyperEvents = hyperEvents,
                bounceIgnored = bounceCount,
                adjustments = adjustments,
                decays = decays
            )
            saveReport(report)

            aapsLogger.debug(LTag.APS,
                "Auto-Adjust: Completed — ${adjustments.size} adjustment(s), ${decays.size} decay(s) applied")

            // 7. Gerar texto de notificação se houver alterações
            val notificationText = buildNotificationText(adjustments, decays)

            // 8. Criar entrada no Careportal com resumo completo da execução
            // (registra TODAS as execuções, não apenas quando há ajustes)
            val careportalMessage = buildString {
                append("Auto-Adjust: ")
                if (hypoEvents.isEmpty() && hyperEvents.isEmpty()) {
                    append("Executado — sem eventos nas últimas 4h")
                } else {
                    append("${hypoEvents.size} hipo(s), ${hyperEvents.size} hiper(s)")
                    if (bounceCount > 0) append(" (${bounceCount} bounce(s) ignorado(s))")
                    if (adjustments.isNotEmpty()) {
                        append(" → ${adjustments.size} ajuste(s)")
                        adjustments.take(3).forEach { a ->
                            append(" h${a.hour} ${a.oldValue.toInt()}→${a.newValue.toInt()}")
                        }
                        if (adjustments.size > 3) append(" +${adjustments.size - 3}")
                    } else {
                        append(" — sem ajustes (sem SMB ou confiança < threshold)")
                    }
                    if (decays.isNotEmpty()) {
                        append(" · ${decays.size} decay(ies)")
                    }
                }
            }
            try {
                repository.runTransaction(
                    InsertIfNewByTimestampTherapyEventTransaction(
                        timestamp = now,
                        type = TherapyEvent.Type.ANNOUNCEMENT,
                        note = careportalMessage,
                        enteredBy = "OpenApsAIMI",
                        glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                    )
                ).subscribe()
            } catch (e: Exception) {
                aapsLogger.warn(LTag.APS, "Auto-Adjust: Failed to create Careportal entry", e)
            }

            return notificationText

        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Auto-Adjust: Analysis failed", e)
            return null
        }
    }

    /**
     * Gera um texto resumido de notificação a partir dos ajustes/decaimentos aplicados.
     * Ex: "Auto-Adjust: h13 50→60, h18 45→55 · 1 decay"
     */
    internal fun buildNotificationText(
        adjustments: List<AnalysisReport.AdjustmentLog>,
        decays: List<AnalysisReport.DecayLog>
    ): String? {
        if (adjustments.isEmpty() && decays.isEmpty()) return null

        val sb = StringBuilder("Auto-Adjust:")
        adjustments.take(3).forEach { a ->
            sb.append(" h${a.hour} ${a.oldValue.toInt()}→${a.newValue.toInt()}")
        }
        if (adjustments.size > 3) {
            sb.append(" +${adjustments.size - 3}")
        }
        if (decays.isNotEmpty()) {
            sb.append(" · ${decays.size} decay")
            if (decays.size > 1) sb.append('s')
        }
        return sb.toString()
    }

    /**
     * Verifica se o auto-adjust está habilitado nas preferências.
     */
    fun isEnabled(): Boolean = sp.getBoolean(SP_AUTO_ADJUST_ENABLED, false)

    /**
     * Inicializa o CSV de magnitude (header) se toggle Collect estiver ON.
     * Pode ser chamado a cada ciclo (~5 min) — é idempotente.
     */
    fun initMagnitudeLog() {
        if (sp.getBoolean(SP_MAGNITUDE_COLLECT, false)) {
            val csvHeaderWritten = sp.getBoolean("mag_csv_header_written", false)
            if (!csvHeaderWritten) {
                try {
                    MAGNITUDE_CSV.parentFile?.mkdirs()
                    MAGNITUDE_CSV.appendText(MAGNITUDE_CSV_HEADER + "\n")
                    sp.putBoolean("mag_csv_header_written", true)
                    aapsLogger.debug(LTag.APS, "Magnitude CSV initialized")
                } catch (e: Exception) {
                    aapsLogger.warn(LTag.APS, "Failed to init magnitude CSV", e)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DATA FETCH
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Busca leituras de glicemia do banco Room no período.
     * Timeout de 5s para não travar caso o banco esteja sob lock.
     */
    private fun fetchBgData(from: Long, to: Long): List<GlucoseValue> {
        return try {
            repository.compatGetBgReadingsDataFromTime(from, to, ascending = true)
                .timeout(5, TimeUnit.SECONDS)
                .blockingGet()
                .filter { it.isValid }
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "Auto-Adjust: BG data fetch timed out or failed", e)
            emptyList()
        }
    }

    /**
     * Busca registros de SMB do banco Room no período.
     * Timeout de 5s.
     */
    private fun fetchSmbData(from: Long, to: Long): List<Bolus> {
        return try {
            repository.getBolusesDataFromTimeToTime(from, to, ascending = true)
                .timeout(5, TimeUnit.SECONDS)
                .blockingGet()
                .filter { it.isValid && it.type == Bolus.Type.SMB }
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "Auto-Adjust: SMB data fetch timed out or failed", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SENSOR CHANGE PROTECTION (Tarciso, Ago/2026)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Verifica se a proteção por idade do sensor está ativa.
     * Condição: sensor trocado há < 12h (independente do BG — leituras instáveis
     * causam tanto falsos baixos quanto falsos altos; bloquear sempre).
     * Usa o timestamp INFORMADO pelo usuário — data retroativa = idade alta = sem bloqueio.
     */
    private fun isSensorProtectionActive(bgData: List<GlucoseValue>): Boolean {
        val sensorAge = getSensorAgeMinutes()
        return sensorAge < SENSOR_AGE_THRESHOLD_MINUTES && sensorAge > 0L  // 0 = acabou de trocar, nao bloqueia
    }

    /**
     * Retorna a idade do sensor em minutos desde a última troca registrada.
     * @return Long.MAX_VALUE se não houver registro de troca ou erro
     */
    private fun getSensorAgeMinutes(): Long {
        return try {
            val result = repository.getLastTherapyRecordUpToNow(TherapyEvent.Type.SENSOR_CHANGE)
                .timeout(3, TimeUnit.SECONDS)
                .blockingGet()
            val sensorTimestamp = when (result) {
                is ValueWrapper.Existing -> result.value.timestamp
                else -> return Long.MAX_VALUE
            }
            (System.currentTimeMillis() - sensorTimestamp) / (60 * 1000)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "Auto-Adjust: Failed to query sensor change record", e)
            Long.MAX_VALUE
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CANNULA PROTECTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Verifica se a proteção por idade da canula está ativa.
     * Condicoes: canula < 5h E BG atual < 170.
     * Se a canula está causando hiper, o BG estara > 170 e o ajuste prossegue (nao e problema de tuning).
     * Se BG < 170 e canula recente, bloqueia — o problema pode ser a canula.
     */
    private fun isCannulaProtectionActive(bgData: List<GlucoseValue>): Boolean {
        val currentBg = bgData.maxByOrNull { it.timestamp }?.value
            ?: return false  // sem BG disponivel, nao bloqueia
        if (currentBg >= CANNULA_BG_THRESHOLD) return false  // ja hiper, problema pode ser canula ou nao

        val cannulaAge = getCannulaAgeMinutes()
        return cannulaAge < CANNULA_AGE_THRESHOLD_MINUTES && cannulaAge > 0L  // 0 = acabou de trocar, nao bloqueia
    }

    /**
     * Retorna a idade da canula em minutos desde a ultima troca registrada.
     * @return Long.MAX_VALUE se nao houver registro de troca ou erro
     */
    private fun getCannulaAgeMinutes(): Long {
        return try {
            val result = repository.getLastTherapyRecordUpToNow(TherapyEvent.Type.CANNULA_CHANGE)
                .timeout(3, TimeUnit.SECONDS)
                .blockingGet()
            val cannulaTimestamp = when (result) {
                is ValueWrapper.Existing -> result.value.timestamp
                else -> return Long.MAX_VALUE
            }
            (System.currentTimeMillis() - cannulaTimestamp) / (60 * 1000)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "Auto-Adjust: Failed to query cannula change record", e)
            Long.MAX_VALUE
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CONFIGURABLE THRESHOLDS
    //  ═══════════════════════════════════════════════════════════════════════

    /**
     * Retorna o threshold de hipoglicemia configurado pelo usuário nas preferências.
     * Padrão: 69 mg/dL. Range seguro: 40-100 mg/dL.
     * Usado como gatilho para detectHypoEvents().
     */
    private fun getHypoThreshold(): Double {
        return sp.getDouble(SP_HYPO_THRESHOLD, HYPOGLYCEMIA_THRESHOLD)
            .coerceIn(40.0, 100.0)
    }

    /**
     * Retorna o threshold de hiperglicemia configurado pelo usuário nas preferências.
     * Padrão: 155 mg/dL. Range seguro: 120-300 mg/dL.
     * Usado como gatilho para detectHyperEvents().
     */
    private fun getHyperThreshold(): Double {
        return sp.getDouble(SP_HYPER_THRESHOLD, HYPERGLYCEMIA_THRESHOLD)
            .coerceIn(120.0, 300.0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EVENT DETECTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Detecta eventos de hipoglicemia (<65 mg/dL).
     * Deduplica: múltiplas leituras consecutivas abaixo do threshold
     * em intervalo de 15 minutos contam como um único evento.
     */
    private fun detectHypoEvents(bgData: List<GlucoseValue>): List<BgEvent> {
        val events = mutableListOf<BgEvent>()
        var lastEventTimestamp = 0L
        val hypoThreshold = getHypoThreshold()

        for (gv in bgData) {
            if (gv.value <= hypoThreshold) {
                // Evita duplicatas no mesmo "mergulho" (15 min window)
                if (gv.timestamp - lastEventTimestamp > 15 * 60 * 1000L) {
                    events.add(BgEvent(gv.timestamp, gv.value, BgEvent.EventType.HYPO))
                    lastEventTimestamp = gv.timestamp
                }
            }
        }
        return events
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  NEAR-MISS HYPOGLYCEMIA DETECTION (Tarciso, Jul/2026)
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * Detecta "quase-hipos": BG entre 70-85 com delta negativo.
     * Estas situações frequentemente disparam alarmes de low glucose
     * (LocalAlertUtils: BG < limite + delta < limite + IOB > limite)
     * mas não atingem o threshold fixo de 69 mg/dL.
     *
     * O evento gerado tem severidade reduzida (usa lastEventTimestamp
     * compartilhado com detectHypoEvents para evitar duplicação).
     *
     * @return lista de eventos de quase-hipo para ajuste preventivo
     */
    private fun detectNearMissEvents(bgData: List<GlucoseValue>): List<BgEvent> {
        val events = mutableListOf<BgEvent>()
        if (bgData.size < 3) return events  // precisa de mínimo 3 leituras para calcular delta

        var lastEventTimestamp = 0L

        for (i in 1 until bgData.size) {
            val current = bgData[i]
            val previous = bgData[i - 1]

            // Calcula delta: diferença entre leitura atual e anterior (mg/dL por 5min)
            val bgDelta = current.value - previous.value

            // Near-miss: BG entre 70-85, caindo (delta < -1)
            if (current.value in 70.0..85.0 && bgDelta < -1.0) {
                if (current.timestamp - lastEventTimestamp > 15 * 60 * 1000L) {
                    events.add(BgEvent(current.timestamp, current.value, BgEvent.EventType.HYPO))
                    lastEventTimestamp = current.timestamp
                }
            }
        }
        return events
    }

    /**
     * Detecta eventos de hiperglicemia (>150 mg/dL).
     * Deduplica com janela de 15 min.
     */
    private fun detectHyperEvents(bgData: List<GlucoseValue>): List<BgEvent> {
        val events = mutableListOf<BgEvent>()
        var lastEventTimestamp = 0L
        val hyperThreshold = getHyperThreshold()

        for (gv in bgData) {
            if (gv.value > hyperThreshold) {
                if (gv.timestamp - lastEventTimestamp > 15 * 60 * 1000L) {
                    events.add(BgEvent(gv.timestamp, gv.value, BgEvent.EventType.HYPER))
                    lastEventTimestamp = gv.timestamp
                }
            }
        }
        return events
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PROLONGED ELEVATION DETECTION (Tarciso, Jul/2026)
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * Detecta elevação prolongada de glicemia.
     * Se >50% das leituras nas últimas 4h estão acima de 140 mg/dL
     * (com mínimo de 3 leituras), dispara um evento de hiper com
     * flag isProlonged=true, que resulta em ajuste reduzido (50% do step).
     *
     * Isso captura o padrão de BG sustentado em 145-155 sem picos
     * isolados acima de 155, comum no período noturno do usuário.
     */
    private fun detectProlongedElevation(bgData: List<GlucoseValue>): BgEvent? {
        if (bgData.size < 6) return null  // mínimo 30min de dados

        val aboveThreshold = bgData.filter { it.value > PROLONGED_ELEVATION_THRESHOLD }
        if (aboveThreshold.size < PROLONGED_ELEVATION_MIN_COUNT) return null

        val ratio = aboveThreshold.size.toDouble() / bgData.size
        if (ratio < PROLONGED_ELEVATION_MIN_RATIO) return null

        // Usa a média das leituras elevadas como valor representativo
        val avgValue = aboveThreshold.sumOf { it.value } / aboveThreshold.size
        val lastTimestamp = bgData.last().timestamp

        return BgEvent(
            timestamp = lastTimestamp,
            value = avgValue,
            type = BgEvent.EventType.HYPER,
            isProlonged = true
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BOUNCE PROTECTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Remove pares hipo→hiper em janela de 2h (rebote glicêmico).
     * Quando uma hipo é seguida de hiper dentro de 2h, ambos são removidos
     * porque o ajuste para ambos criaria um loop contraditório.
     *
     * @return número de eventos removidos por bouncing
     */
    private fun removeBounceEvents(hypoEvents: MutableList<BgEvent>, hyperEvents: MutableList<BgEvent>): Int {
        val hypoToRemove = mutableSetOf<BgEvent>()
        val hyperToRemove = mutableSetOf<BgEvent>()

        for (hypo in hypoEvents) {
            val rebound = hyperEvents.any { hyper ->
                hyper.timestamp > hypo.timestamp &&
                    hyper.timestamp <= hypo.timestamp + REBOUND_WINDOW_MS
            }
            if (rebound) {
                hypoToRemove.add(hypo)
                // Marca a primeira hiper depois desta hipo
                hyperEvents.firstOrNull { hyper ->
                    hyper.timestamp > hypo.timestamp &&
                        hyper.timestamp <= hypo.timestamp + REBOUND_WINDOW_MS
                }?.let { hyperToRemove.add(it) }
            }
        }

        hypoEvents.removeAll(hypoToRemove)
        hyperEvents.removeAll(hyperToRemove)

        return hypoToRemove.size + hyperToRemove.size
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CORRELATION → ADJUSTMENT PROPOSAL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Correlaciona eventos de hipo com SMBs administrados nas 2h anteriores.
     *
     * Para cada hipo:
     * 1. Busca SMBs na janela de 2h antes do evento
     * 2. Pega o SMB mais recente antes da hipo
     * 3. Extrai a hora do dia (0-23) daquele SMB
     * 4. Propõe +ADJUSTMENT_STEP para aquela hora (menos agressivo)
     *
     * Se não encontrar SMB correlacionável, o evento é ignorado.
     *
     * ═══════════════════════════════════════════════════════════════════
     * Overshoot Detection (Tarciso, Jul/2026):
     * Se o VOLUME TOTAL de SMBs nas 4h antes da hipo exceder 3.0U,
     * em vez de ajustar apenas 1 hora (último SMB), ajusta TODAS
     * as horas com SMBs no período, proporcionalmente ao volume
     * de SMB em cada hora. Isso ataca a CAUSA (SMBs excessivos
     * distribuídos em várias horas), não o efeito.
     * ═══════════════════════════════════════════════════════════════════
     *
     * @return mapa de hourOfDay → delta proposto (sempre positivo para hipo)
     */
    private fun correlateHypoAdjustments(
        hypoEvents: List<BgEvent>,
        smbData: List<Bolus>
    ): Map<Int, Double> {
        val adjustments = mutableMapOf<Int, Double>()

        for (event in hypoEvents) {
            // ════════════════════════════════════════════════════════════════
            // Overshoot window: 4h padrão, 8h para hipo matinal (4-10h BRT)
            // (Tarciso, Jul/2026 — hipo matinal estendida para capturar
            //  SMBs da noite anterior, ex: 20-23h → hipo 07-09h)
            // ════════════════════════════════════════════════════════════════
            val horaDaHipo = getHourOfDay(event.timestamp)
            val overshootWindow = if (horaDaHipo in 4..10) {
                OVERSHOOT_LOOKBACK_WINDOW_MORNING_MS  // 8h para hipo matinal
            } else {
                OVERSHOOT_LOOKBACK_WINDOW_MS           // 4h para outros horários
            }
            val overshootWindowStart = event.timestamp - overshootWindow
            val overshootSmbs = smbData.filter { smb ->
                smb.timestamp in overshootWindowStart..event.timestamp
            }

            if (overshootSmbs.isEmpty()) {
                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Hypo at ${dateUtil.timeString(event.timestamp)} " +
                        "has no correlatable SMB — skipping")
                continue
            }

            // Calcula volume total de SMB no período
            val totalSMB = overshootSmbs.sumOf { it.amount }

            // ════════════════════════════════════════════════════════════════
            // Fator de severidade (Tarciso, Jul/2026)
            // ════════════════════════════════════════════════════════════════
            // Hipo mais grave = ajuste maior para evitar recorrência
            val severityFactor = when {
                event.value < 50.0 -> 1.5   // hipo severa: 50% mais ajuste
                event.value < 60.0 -> 1.25  // hipo moderada: 25% mais ajuste
                else -> 1.0                  // hipo leve: ajuste normal
            }

            // ════════════════════════════════════════════════════════════════
            // Overshoot detection: total SMB > 1.5U → overshoot pattern
            // ════════════════════════════════════════════════════════════════
            if (totalSMB > OVERSHOOT_SMB_THRESHOLD) {
                // Overshoot! Agrupa SMBs por hora e ajusta proporcionalmente
                val smbByHour = overshootSmbs.groupBy { getHourOfDay(it.timestamp) }
                val totalAdjustmentPerEvent = ADJUSTMENT_STEP * 1.5 * severityFactor  // overshoot = ajuste maior

                for ((hour, smbs) in smbByHour) {
                    val hourTotal = smbs.sumOf { it.amount }
                    // Fator proporcional: hora com mais SMB recebe mais ajuste
                    val hourWeight = hourTotal / totalSMB
                    val hourAdjustment = totalAdjustmentPerEvent * hourWeight
                    adjustments[hour] = (adjustments[hour] ?: 0.0) + hourAdjustment

                    aapsLogger.debug(LTag.APS,
                        "Auto-Adjust: OVERSHOOT at ${dateUtil.timeString(event.timestamp)} " +
                            "totalSMB=${"%.1f".format(totalSMB)}U → h${hour} " +
                            "(${"%.2f".format(hourTotal)}U, weight=${"%.2f".format(hourWeight)}) " +
                            "→ +${"%.1f".format(hourAdjustment)}")
                }
            } else {
                // Comportamento normal (sem overshoot): usa o último SMB
                val relevantSmbs = overshootSmbs.filter { smb ->
                    smb.timestamp >= event.timestamp - SMB_LOOKBACK_WINDOW_MS
                }

                if (relevantSmbs.isEmpty()) continue

                val latestSmb = relevantSmbs.maxByOrNull { it.timestamp } ?: continue
                val hourOfDay = getHourOfDay(latestSmb.timestamp)

                // Hipo → aumentar hourly factor (menos agressivo) → delta positivo
                adjustments[hourOfDay] = (adjustments[hourOfDay] ?: 0.0) + ADJUSTMENT_STEP * severityFactor

                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Hypo at ${dateUtil.timeString(event.timestamp)} " +
                        "→ SMB at ${dateUtil.timeString(latestSmb.timestamp)} " +
                        "(h${hourOfDay}) → +$ADJUSTMENT_STEP")
            }
        }

        return adjustments
    }

    /**
     * Correlaciona eventos de hiper (>150) com SMBs nas 2h anteriores.
     *
     * ═══════════════════════════════════════════════════════════════════
     * v2 (Tarciso, Jul/2026) — Duas melhorias:
     * 1. Distribuição proporcional: se múltiplos SMBs, ajusta TODAS
     *    as horas com SMB proporcionalmente ao volume (mesma lógica
     *    do overshoot para hipo).
     * 2. Step reduzido para elevação prolongada: eventos detectados
     *    por >50% de BG >140 recebem 50% do ajuste normal.
     * ═══════════════════════════════════════════════════════════════════
     *
     * @return mapa de hourOfDay → delta proposto (sempre negativo para hiper)
     */
    private fun correlateHyperAdjustments(
        hyperEvents: List<BgEvent>,
        smbData: List<Bolus>
    ): Map<Int, Double> {
        val adjustments = mutableMapOf<Int, Double>()

        for (event in hyperEvents) {
            val smbWindowStart = event.timestamp - SMB_LOOKBACK_WINDOW_MS
            val relevantSmbs = smbData.filter { smb ->
                smb.timestamp in smbWindowStart..event.timestamp
            }

            if (relevantSmbs.isEmpty()) {
                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Hyper at ${dateUtil.timeString(event.timestamp)} " +
                        "has no correlatable SMB — skipping")
                continue
            }

            // ════════════════════════════════════════════════════════════════
            // Fator de ajuste para elevação prolongada (Tarciso, Jul/2026)
            // v2 — Tiers de severidade baseados na média do BG elevado:
            //   avg > 160 (severa):   fator = 1.0 (step completo 10)
            //   avg > 150 (moderada): fator = 0.75 (step 7.5)
            //   avg <= 150 (leve):    fator = 0.5 (step 5, original)
            // ════════════════════════════════════════════════════════════════
            val prolongedFactor = if (event.isProlonged) {
                when {
                    event.value > 160.0 -> 1.0     // severa
                    event.value > 150.0 -> 0.75    // moderada
                    else -> 0.5                     // leve (original)
                }
            } else 1.0

            // ════════════════════════════════════════════════════════════════
            // Fator de severidade para hiper
            // ════════════════════════════════════════════════════════════════
            val hyperThreshold = getHyperThreshold()
            val excess = event.value - hyperThreshold
            val severityFactor = when {
                excess > 80.0 -> 1.5   // hiper severa (>threshold+80): 50% mais ajuste
                excess > 40.0 -> 1.25  // hiper moderada (>threshold+40): 25% mais ajuste
                else -> 1.0             // hiper leve: ajuste normal
            }

            // ════════════════════════════════════════════════════════════════
            // Fator de confiança reduzido para hiper com SMBs prévios
            // (Tarciso, Jul/2026 — Proposta #5)
            // ════════════════════════════════════════════════════════════════
            // Se múltiplos SMBs foram dados sem conter a subida, a causa
            // pode ser absorção de refeição ou resistência, não falta de
            // agressividade. Reduz a confiança no ajuste.
            //   0-1 SMB:  confiança normal (1.0)
            //   2 SMBs:   confiança moderada (0.75)
            //   3+ SMBs:  confiança baixa (0.50)
            // ════════════════════════════════════════════════════════════════
            val confidenceMultiplier = when {
                relevantSmbs.size >= 3 -> 0.5
                relevantSmbs.size >= 2 -> 0.75
                else -> 1.0
            }

            val totalAdjustment = -ADJUSTMENT_STEP * severityFactor * prolongedFactor * confidenceMultiplier

            // ════════════════════════════════════════════════════════════════
            // Distribuição proporcional se múltiplos SMBs
            // ════════════════════════════════════════════════════════════════
            if (relevantSmbs.size >= 2) {
                // Mesma lógica do overshoot: distribuir por hora proporcionalmente
                val totalSMB = relevantSmbs.sumOf { it.amount }
                if (totalSMB > 0.0) {
                    val smbByHour = relevantSmbs.groupBy { getHourOfDay(it.timestamp) }
                    for ((hour, smbs) in smbByHour) {
                        val hourTotal = smbs.sumOf { it.amount }
                        val weight = hourTotal / totalSMB
                        val hourAdjustment = totalAdjustment * weight
                        adjustments[hour] = (adjustments[hour] ?: 0.0) + hourAdjustment

                        aapsLogger.debug(LTag.APS,
                            "Auto-Adjust: Hyper at ${dateUtil.timeString(event.timestamp)} " +
                                "(${"%.0f".format(event.value)}) → h${hour} " +
                                "(${"%.2f".format(hourTotal)}U, weight=${"%.2f".format(weight)}) " +
                                "→ ${"%.1f".format(hourAdjustment)}" +
                                if (event.isProlonged) " [PROLONGED]" else "")
                    }
                }
            } else {
                // Comportamento original: último SMB apenas
                val latestSmb = relevantSmbs.maxByOrNull { it.timestamp } ?: continue
                val hourOfDay = getHourOfDay(latestSmb.timestamp)

                adjustments[hourOfDay] = (adjustments[hourOfDay] ?: 0.0) + totalAdjustment

                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Hyper at ${dateUtil.timeString(event.timestamp)} " +
                        "(${"%.0f".format(event.value)}) → SMB at " +
                        "${dateUtil.timeString(latestSmb.timestamp)} " +
                        "(h${hourOfDay}) → ${"%.1f".format(totalAdjustment)}" +
                        if (event.isProlonged) " [PROLONGED]" else "")
            }
        }

        return adjustments
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ADJUSTMENT APPLICATION (with Decay + Limits)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Aplica decaimento e ajustes para cada hora afetada.
     *
     * Fluxo por hora:
     * 1. Lê valor atual da SharedPreferences
     * 2. Lê estado persistido (originalValue, totalDelta, lastEventTimestamp)
     * 3. Se passaram DECAY_DAYS_THRESHOLD sem evento → decai 50%
     * 4. Calcula novo delta (hipo + | hiper -) com fator de confiança
     * 5. Aplica limite de 25% do valor atual por execução
     * 6. Aplica limite de ±MAX_CUMULATIVE_ADJUSTMENT
     * 7. Aplica piso ABSOLUTE_FLOOR
     * 8. Persiste novo valor e estado
     */
    private fun applyDecayAndAdjustments(
        hypoAdjustments: Map<Int, Double>,
        hyperAdjustments: Map<Int, Double>,
        outAdjustments: MutableList<AnalysisReport.AdjustmentLog>,
        outDecays: MutableList<AnalysisReport.DecayLog>,
        outMagnitudes: MutableList<MagnitudeRecord> = mutableListOf()
    ) {
        val now = System.currentTimeMillis()
        val affectedHours = (hypoAdjustments.keys + hyperAdjustments.keys).toSet()

        for (hour in affectedHours) {
            val prefKey = getHourlyPreferenceKey(hour)

            // 1. Estado atual
            val currentValue = readCurrentValue(prefKey)
            val state = readState(hour)

            // 🔧 FIX: Se o estado está vazio (primeira vez para esta hora),
            // sincroniza originalValue com o valor real da SP.
            // Isso impede que ajustes manuais do usuário (ex: definir 6%)
            // sejam sobrescritos pelo default de 50.
            // Apenas aplica quando eventCount==0 e totalDelta==0
            // (nunca houve ajuste automático para esta hora ainda).
            val recalibratedState = if (state.eventCount == 0 && state.totalDelta == 0.0
                && state.originalValue != currentValue) {
                state.copy(originalValue = currentValue)
            } else {
                state
            }

            // ════════════════════════════════════════════════════════════════
            // v2: Recalibração do estado quando há discrepância com a SP
            // ════════════════════════════════════════════════════════════════
            // Correção B (31/Jul/2026): removida a condição `totalDelta != 0.0`.
            // A brecha: se o usuário edita o fator manualmente e o estado tem
            // totalDelta==0 (ex: decay zerou) com eventCount>0, a v1 (eventCount==0)
            // e a v2 (totalDelta!=0) não disparavam → baseline interno divergente
            // → salto real observado 10→35 e 15→60 (07/28, build pré-v2).
            // Agora recalibra SEMPRE que |currentValue - expectedValue| > 1.0.
            val expectedValue = recalibratedState.originalValue + recalibratedState.totalDelta
            val finalState = if (kotlin.math.abs(currentValue - expectedValue) > 1.0) {
                aapsLogger.warn(LTag.APS,
                    "Auto-Adjust: State-SP mismatch h$hour — " +
                    "expected=${"%.0f".format(expectedValue)}, actual=${"%.0f".format(currentValue)}. " +
                    "Recalibrating (original=${"%.0f".format(recalibratedState.originalValue)}→${"%.0f".format(currentValue)}, " +
                    "delta=${"%.0f".format(recalibratedState.totalDelta)}→0)")
                recalibratedState.copy(
                    originalValue = currentValue,
                    totalDelta = 0.0,
                    eventCount = 0
                )
            } else {
                recalibratedState
            }

            // 2. Decaimento (se aplicável)
            val daysSinceLastEvent = if (finalState.lastEventTimestamp > 0L) {
                (now - finalState.lastEventTimestamp) / (24.0 * 60 * 60 * 1000)
            } else {
                Double.MAX_VALUE  // nunca teve evento → não decai
            }

            var postDecayDelta = finalState.totalDelta
            var postDecayOriginal = finalState.originalValue

            if (daysSinceLastEvent >= DECAY_DAYS_THRESHOLD && finalState.totalDelta != 0.0) {
                val decayAmount = finalState.totalDelta * DECAY_RATE
                postDecayDelta = finalState.totalDelta - decayAmount

                // Se o delta residual é pequeno, zera completamente
                if (kotlin.math.abs(postDecayDelta) < DECAY_ZERO_THRESHOLD) {
                    postDecayDelta = 0.0
                }

                // Aplica decaimento no valor atual
                val decayedValue = (finalState.originalValue + postDecayDelta)
                    .coerceAtLeast(ABSOLUTE_FLOOR)
                    .roundToInt()
                sp.putString(prefKey, decayedValue.toString())

                // Atualiza o originalValue para refletir a nova baseline pós-decaimento
                postDecayOriginal = finalState.originalValue + (finalState.totalDelta - postDecayDelta)

                outDecays.add(AnalysisReport.DecayLog(
                    hour = hour,
                    previousDelta = finalState.totalDelta,
                    decayedDelta = postDecayDelta
                ))

                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Decay h$hour: ${finalState.totalDelta.roundToInt()} → ${
                        postDecayDelta.roundToInt()
                    } (${daysSinceLastEvent.toInt()}d without event)")
            }

            // 3. Novo ajuste proposto
            val hypoDelta = hypoAdjustments[hour] ?: 0.0
            val hyperDelta = hyperAdjustments[hour] ?: 0.0
            val rawDelta = hypoDelta + hyperDelta  // hipo = +, hiper = -

            // ════════════════════════════════════════════════════════════════
            // Fator noturno (Tarciso, Jul/2026)
            // ════════════════════════════════════════════════════════════════
            // Ajustes noturnos são reduzidos em 30% porque hipos noturnas
            // são mais perigosas e o consumo de insulina é menor (≈44% do diurno)
            val isNightHour = hour in 22..23 || hour in 0..5
            val nightFactor = if (isNightHour) 0.7 else 1.0
            val adjustedDelta = rawDelta * nightFactor

            if (rawDelta == 0.0) {
                // Decaimento já foi aplicado ao SP acima, mas precisamos
                // salvar o estado para evitar re-decaimento no próximo ciclo
                if (postDecayDelta != finalState.totalDelta || postDecayOriginal != finalState.originalValue) {
                    saveState(hour, HourlyState(
                        originalValue = postDecayOriginal,
                        totalDelta = postDecayDelta,
                        lastEventTimestamp = now,
                        eventCount = finalState.eventCount
                    ))
                }
                continue
            }

            // 4. Fator de confiança
            val newEvents = (if (hypoAdjustments.containsKey(hour)) 1 else 0) +
                (if (hyperAdjustments.containsKey(hour)) 1 else 0)
            val totalEventCount = finalState.eventCount + newEvents
            val confidenceFactor = if (totalEventCount >= MIN_CONFIDENCE_EVENTS) 1.0 else 0.5
            val effectiveDelta = adjustedDelta * confidenceFactor

            // 5. Limite de ajuste POR EXECUÇÃO (25% do valor atual)
            // O cap é dinâmico: proporcional ao valor atual da hora.
            // Valores altos permitem ajustes maiores; valores baixos ajustam finamente.
            // Ex: valor=100 → cap=±25 | valor=10 → cap=±2.5
            val perRunCap = abs(postDecayOriginal) * PER_RUN_ADJUSTMENT_PERCENT
            val perRunDelta = effectiveDelta.coerceIn(
                -perRunCap,
                perRunCap
            )

            if (perRunDelta != effectiveDelta) {
                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Per-run cap applied h$hour (25%=${"%.1f".format(perRunCap)}): effectiveDelta=$effectiveDelta → perRunDelta=${"%.1f".format(perRunDelta)}")
            }

            // 6. Limite de ajuste acumulado total
            val proposedDelta = postDecayDelta + perRunDelta
            val cappedDelta = proposedDelta.coerceIn(
                -MAX_CUMULATIVE_ADJUSTMENT,
                MAX_CUMULATIVE_ADJUSTMENT
            )

            if (cappedDelta != proposedDelta) {
                aapsLogger.debug(LTag.APS,
                    "Auto-Adjust: Cap applied h$hour: $proposedDelta → $cappedDelta")
            }

            // 6. Calcular novo valor
            // Correção C (31/Jul/2026): salvaguarda contra salto — o novo valor
            // nunca pode divergir mais que ±50% do valor REAL da SP (currentValue),
            // mesmo que o baseline interno (postDecayOriginal) esteja divergente.
            // Ex: current=10, postDecayOriginal=44.82, cappedDelta=-9.82 →
            //     raw = 44.82-9.82 = 35 → salvaguarda: coerceIn(5, 15) → 15.
            // O perRunCap (25%) já limita o incremento; esta é a segunda camada
            // (defesa em profundidade) contra qualquer salto residual.
            val maxJumpFromCurrent = abs(currentValue) * MAX_JUMP_PERCENT_FROM_CURRENT
            val newValue = (postDecayOriginal + cappedDelta)
                .coerceIn(currentValue - maxJumpFromCurrent, currentValue + maxJumpFromCurrent)
                .coerceAtLeast(ABSOLUTE_FLOOR)
                .roundToInt()
            // Delta efetivo aplicado em relação ao baseline (pode diferir do
            // cappedDelta proposto quando a salvaguarda C clamp o newValue).
            val appliedTotalDelta = newValue - postDecayOriginal

            // 7. Persistir
            sp.putString(prefKey, newValue.toString())
            saveState(hour, HourlyState(
                originalValue = postDecayOriginal,
                totalDelta = appliedTotalDelta,
                lastEventTimestamp = now,
                eventCount = totalEventCount
            ))

            outAdjustments.add(AnalysisReport.AdjustmentLog(
                hour = hour,
                oldValue = currentValue,
                newValue = newValue.toDouble(),
                delta = newValue - currentValue,
                reason = when {
                    hypoDelta > 0 && hyperDelta < 0 -> "hypo+hyper"
                    hypoDelta > 0 -> "hypo"
                    else -> "hyper"
                }
            ))

            // Coleta de magnitude (Fase 1 — modelo de magnitude)
            if (sp.getBoolean(SP_MAGNITUDE_COLLECT, false)) {
                outMagnitudes.add(MagnitudeRecord(
                    timestamp = now,
                    hour = hour,
                    oldValue = currentValue,
                    newValue = newValue.toDouble(),
                    delta = newValue - currentValue,
                    eventType = when {
                        hypoDelta > 0 && hyperDelta < 0 -> "BOTH"
                        hypoDelta > 0 -> "HYPO"
                        else -> "HYPER"
                    },
                    rawDelta = rawDelta,
                    effectiveDelta = effectiveDelta,
                    appliedDelta = newValue - currentValue,
                    totalDelta = appliedTotalDelta,
                    confidenceFactor = confidenceFactor,
                    eventCount = totalEventCount
                ))
            }

            aapsLogger.debug(LTag.APS,
                "Auto-Adjust: Applied h$hour: $currentValue → $newValue " +
                    "(delta=${newValue - currentValue}, " +
                    "confidence=$confidenceFactor, events=$totalEventCount)")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSISTENCE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lê o valor atual de um hourly factor da SharedPreferences.
     * Usa SafeParse para garantir que o valor é um número válido.
     * Aplica o offset sazonal configurado pelo usuário (SP_SEASONAL_OFFSET),
     * que desloca todos os fatores horários em X% para cima (menos agressivo,
     * verão) ou para baixo (mais agressivo, inverno).
     */
    private fun readCurrentValue(prefKey: String): Double {
        val raw = sp.getString(prefKey, "50")
        val baseValue = raw.toDoubleOrNull()?.coerceIn(1.0, 300.0) ?: 50.0

        // ════════════════════════════════════════════════════════════════
        // Seasonal offset (Tarciso, Jul/2026)
        // ════════════════════════════════════════════════════════════════
        // Lê o offset configurado pelo usuário em % (ex: +20 para verão)
        // e aplica ao valor base. O offset é limitado a ±30% por segurança.
        // Exemplo: valor=50, offset=+20% → 50 * 1.20 = 60.0 (menos insulina)
        //          valor=50, offset=-15% → 50 * 0.85 = 42.5 (mais insulina)
        // ════════════════════════════════════════════════════════════════
        val seasonalOffset = sp.getDouble(SP_SEASONAL_OFFSET, 0.0)
            .coerceIn(-30.0, 30.0)  // segurança: ±30% máx

        return if (seasonalOffset != 0.0) {
            val adjusted = baseValue * (1.0 + seasonalOffset / 100.0)
            adjusted.coerceIn(ABSOLUTE_FLOOR, 300.0)
        } else {
            baseValue
        }
    }

    /**
     * Lê o estado persistido para uma hora específica.
     */
    private fun readState(hourOfDay: Int): HourlyState {
        val stateJson = sp.getStringOrNull(SP_ADJUST_STATE, null) ?: return HourlyState.empty()
        return try {
            val root = JSONObject(stateJson)
            if (root.has(hourOfDay.toString())) {
                HourlyState.fromJson(root.getJSONObject(hourOfDay.toString()))
            } else {
                HourlyState.empty()
            }
        } catch (e: Exception) {
            HourlyState.empty()
        }
    }

    /**
     * Salva o estado persistido para uma hora específica.
     * Faz merge com o JSON existente para preservar outras horas.
     */
    private fun saveState(hourOfDay: Int, state: HourlyState) {
        val existingJson = sp.getStringOrNull(SP_ADJUST_STATE, null) ?: "{}"
        try {
            val root = JSONObject(existingJson)
            root.put(hourOfDay.toString(), state.toJson())
            sp.putString(SP_ADJUST_STATE, root.toString())
        } catch (_: Exception) {
            // Se falhar, cria novo estado
            val root = JSONObject()
            root.put(hourOfDay.toString(), state.toJson())
            sp.putString(SP_ADJUST_STATE, root.toString())
        }
    }

    /**
     * Salva o relatório de análise para auditoria.
     */
    private fun saveReport(report: AnalysisReport) {
        sp.putString(SP_ADJUST_LOG, report.toJson(dateUtil))
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Extrai a hora do dia (0-23) de um timestamp.
     */
    private fun getHourOfDay(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Retorna um resumo legível do último relatório de auto-ajuste.
     * Usado pelo plugin para popular o summary na tela de preferências.
     */
    fun getLastReportSummary(): String {
        val json = sp.getStringOrNull(SP_ADJUST_LOG, null) ?: return "No report yet"
        return try {
            val report = JSONObject(json)
            val ts = report.optString("timestamp", "?")
            val hypos = report.optJSONArray("hypo_events")?.length() ?: 0
            val hypers = report.optJSONArray("hyper_events")?.length() ?: 0
            val adj = report.optJSONArray("adjustments")?.length() ?: 0
            val decays = report.optJSONArray("decays")?.length() ?: 0
            "$ts · ${hypos}H+${hypers}H→ ${adj}adj ${decays}decay"
        } catch (e: Exception) {
            "Parse error: ${e.message}"
        }
    }

    /**
     * Retorna o intervalo de análise configurado (lê da SharedPreferences).
     * Padrão: 240 min (4h). Opções: 120, 180, 240.
     */
    fun getAnalysisInterval(): Long {
        val minutes = sp.getInt("key_aimi_auto_adjust_interval", 240)
        return (minutes * 60 * 1000L).coerceAtLeast(60 * 60 * 1000L)  // mínimo 1h segurança
    }

}