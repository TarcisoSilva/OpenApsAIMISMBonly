package app.aaps.plugins.aps.openAPSAIMI

import android.os.Environment
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.GlucoseStatus
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.iob.IobTotal
import app.aaps.core.interfaces.iob.MealData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.main.extensions.convertedToAbsolute
import app.aaps.core.main.extensions.getPassedDurationToTimeInMinutes
import app.aaps.core.main.extensions.plannedRemainingMinutes
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.aps.APSResultObject
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.ml.AimiSmbTrainer
import app.aaps.plugins.aps.openAPSAIMI.ml.AimiBgConfidenceTrainer
import app.aaps.plugins.aps.openAPSAIMI.ml.computeTrendIndicator
import dagger.android.HasAndroidInjector
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.Float
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.Float as Float1
import app.aaps.core.interfaces.utils.T
import java.util.Date
import app.aaps.plugins.aps.openAPSAIMI.DigestionDetector
import app.aaps.plugins.aps.openAPSAIMI.pkpd.PkPdIntegration
import app.aaps.plugins.aps.openAPSAIMI.pkpd.PkpdAbsorptionGuard
import app.aaps.plugins.aps.openAPSAIMI.pkpd.PkPdRuntime
import app.aaps.plugins.aps.openAPSAIMI.smb.SmbDampingUsecase

//tarciso

@Suppress("DEPRECATED_IDENTITY_EQUALS") class DetermineBasalAdapterAIMI internal constructor(private val injector: HasAndroidInjector) : DetermineBasalAdapter {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var localAlertUtils: LocalAlertUtils

    private var iob = 0.0
    private var cob = 0.0
    private var predictedBg = 0.0
    private var lastCarbAgeMin: Int = 0
    private var futureCarbs = 0.0
    private var recentNotes: List<UserEntry>? = null
    private var tags0to60minAgo = ""
    private var tags60to120minAgo = ""
    private var tags120to180minAgo = ""
    private var tags180to240minAgo = ""
    private var currentTIRLow: Double = 0.0
    private var currentTIRRange: Double = 0.0
    private var currentTIRAbove: Double = 0.0


    // Tarciso DynamicAdjuts - Next line added
    private var lastHourTIRLow: Double = 0.0
    private var lastHourTIRAbove95: Double = 0.0
    private var bg = 0.0



    private var targetBg = 90.0
    private var normalBgThreshold = 120.0
    private var sensorChangeEvents = 0
    private var delta = 0.0
    private var shortAvgDelta = 0.0
    private var longAvgDelta = 0.0
    private var lastsmbtime = 0
    private var lastmealtime = 0
    private var accelerating_up: Int = 0
    private var deccelerating_up: Int = 0
    private var accelerating_down: Int = 0
    private var deccelerating_down: Int = 0
    private var stable: Int = 0
    private var stable2: Int = 0
    private var bgstatus: Int = 0
    // Tarciso_BG_CONFIDENCE (08/Ago/2026) — confiança da leitura de BG.
    // OPÇÃO A (19/Ago/2026): rede neural DESATIVADA — tier vem só das TRAVAS 1-9
    // (0=OK, 1=UNCERTAIN, 2=BAD). Ver plano_2026-08-19_opcao_A_desativar_nn.md.
    private var bgConfidence: Int = 0
    // Pipeline phases last execution — for CSV (9 cols suffix: bruto,ajustes,pkpd,damping,brake,finalize,maxLimits,tier,final)
    private var lastEtapaBruto: Float = 0f
    private var lastEtapaAjustes: Float = 0f
    private var lastEtapaPkpd: Float = 0f
    private var lastEtapaDamping: Float = 0f
    private var lastEtapaBrake: Float = 0f
    private var lastEtapaFinalize: Float = 0f
    private var lastEtapaMaxLimits: Float = 0f
    private var lastEtapaTier: Float = 0f
    private var lastEtapaFinal: Float = 0f
    private var ProfileISF = 0.0
    // Tarciso
    private var DinMaxIob = 0.0
    private var DynMaxSmb = 0.0
    private var maxSMB = 0.0
    private var adjustDynIsf = 0.0
    private var SensorChange: List<TherapyEvent>? = null
    private var TherapyNote: List<TherapyEvent>? = null
    private var BGfinger: List<TherapyEvent>? = null
    private var tdd7DaysPerHour = 0.0
    private var tdd2DaysPerHour = 0.0
    private var tddPerHour = 0.0
    private var tdd24HrsPerHour = 0.0
    private var tddBLast4Hrs = 0.0
    private var hourOfDay: Int = 0
    private var weekend: Int = 0
    private var recentSteps5Minutes: Int = 0
    private var recentSteps10Minutes: Int = 0
    private var recentSteps15Minutes: Int = 0
    private var recentSteps30Minutes: Int = 0
    private var recentSteps60Minutes: Int = 0
    private var recentSteps180Minutes: Int = 0
    private var basalaimi = 0.0
    private var basalSMB = 0.0
    private var aimilimit = 0.0
    private var basaloapsaimirate = 0.0
    private var CI = 0.0
    private var TDDStatus = "Basic Mode"
    private var TirStatus = "Basic Mode"
    private var targetStatus = " "
    // private var deltaChanged = 0.0f
    private var MaxIobProfround = 0.0
    private var MaxSMBProfround = 0.0
    private var protection = "(OFF)"
    // C3 (01/Ago/2026): maxSMB2 removido (só alimentava MaxSMBProfround, derivado de maxSMB)
    // private var TIR120 = 0.0
    // private var TIR140 = 0.0
    // private var TIR160 = 0.0
    private var hourlyfactor: Double = 0.0
    private var hipoTrigger = HIPO_NONE
    private var mealTrigger = 0.0
    private var lowFactor = 0
    private var nightprotection = "OFF"

    private var maxIob = 0.0
    private var variableSensitivity = 0.0
    private var averageBeatsPerMinute = 0.0
    private var averageBeatsPerMinute60 = 0.0
    private var averageBeatsPerMinute180 = 0.0
    private var b30upperbg = 0.0
    private var b30upperdelta = 0.0
    private var profile = JSONObject()
    private var glucoseStatus = JSONObject()
    private var iobData: JSONArray? = null
    private var mealData = JSONObject()
    private var currentTemp = JSONObject()
    private var autosensData = JSONObject()
    private val path = File(Environment.getExternalStorageDirectory().toString())
    private val modelFile = File(path, "AAPS/ml/model.tflite")
    private val modelHBFile = File(path, "AAPS/ml/modelHB.tflite")

    // Cached formatter for TFLite model output (evita recriar a cada ciclo)
    private val smbFormatter = DecimalFormat("#.####", DecimalFormatSymbols(Locale.US))

    // Cached TensorFlow Lite interpreter (evita recarregar modelo a cada ciclo)
    private var modelInterpreter: Interpreter? = null
    private var interpreterIsHB: Boolean = false

    // Cached CSV file references (evita recriar File objects a cada ciclo)
    private val recordsFile = File(path, "AAPS/oapsaimi_records.csv")
    private val recordsHBFile = File(path, "AAPS/oapsaimiHB_records.csv")
    private var smbModelLoaded = false

    private var iobArray: List<IobTotal>? = null
    // ════════════════════════════════════════════════════════════════
    // FASE 1 — PK/PD + DigestionDetector (Jul/2026)
    // ════════════════════════════════════════════════════════════════
    private val digestionDetector = DigestionDetector()
    private var pkpdRuntime: PkPdRuntime? = null
    private var lastBolusAgeMinutes: Double = 0.0
    private var fusedIsf: Double = 0.0
    // ════════════════════════════════════════════════════════════════

    override var currentTempParam: String? = null
    override var iobDataParam: String? = null
    override var glucoseStatusParam: String? = null
    override var profileParam: String? = null
    override var mealDataParam: String? = null
    override var scriptDebug = ""

    private var now: Long = 0
    private var bgTime: Long = 0
    private var bgImpact = 0.0
    private var bgAltoUltimas4h = false
    private var bgAltoRatio = 0.0       // % de leituras > 140 nas últimas 4h (P2, Jul/2026)
    private var lowestBgUltimaHora = 200.0
    private var smbTotalUltimas4h = 0.0

    // ✅ ADICIONE ESTAS VARIÁVEIS OBTER LOW GLUCOSE:
    private var alarmesLowGlucoseUltimaHora = 0
    private var alarmesLowGlucose24Horas = 0
    private var totalAlarmesLowGlucose = 0


    // private var previusbgTime: Long = -1 // Armazena o tempo da leitura anterior
    // private var bgAcceleration: Float = 0.0f // Armazena a aceleração calculada



    @Suppress("SpellCheckingInspection")
    override operator fun invoke(): APSResultObject {
        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal <<<")

        // Guard defense-in-depth: variableSensitivity ainda nao foi calculada (ex: invoke() sem setData())
        // Tarciso_REMOVER_ISF (09/Ago/2026): fallback = hourlyfactor (auto-ajustado), nunca profile.
        if (this.variableSensitivity <= 0.0) {
            this.variableSensitivity = hourlyfactor.coerceAtLeast(1.0)
        }

        // ✅ CONTROLADOR PD (PROPORCIONAL + DERIVADO)
        val kP = KP  // 0.75% do erro por ciclo
        val kD = KD   // 1.5% da velocidade por ciclo
        val error = bg - targetBg
        val proportional = error * kP
        val derivative = delta * kD
        val pdCorrection = proportional + derivative


        val modelResult = calculateSMBFromModel()   // Float? — null = falha real do modelo
        // v3 (01/Ago/2026): tfliteFailed só é true em falha REAL (null).
        // Modelo funcionando que decide 0.0F NÃO é falha — é decisão legítima,
        // e não deve disparar o fallback PD (C2+M6).
        val tfliteFailed = modelResult == null
        val predictedSMB = modelResult ?: 0.0f

        // ML Refinement: ajuste fino do SMB via rede neural on-device
        var mlRefinedSMB = predictedSMB
        if (!tfliteFailed && sp.getBoolean(R.string.key_aimi_smb_refine, false)) {
            // BUG-11: função única compartilhada com os trainers (antes duplicada inline)
            val ti = computeTrendIndicator(
                delta.toFloat(), shortAvgDelta.toFloat(), longAvgDelta.toFloat()
            )
            val features = floatArrayOf(
                bg.toFloat(), iob.toFloat(), cob.toFloat(), delta.toFloat(),
                shortAvgDelta.toFloat(), longAvgDelta.toFloat(),
                tdd7DaysPerHour.toFloat(), tdd2DaysPerHour.toFloat(),
                tddPerHour.toFloat(), tdd24HrsPerHour.toFloat(), ti
            )
            mlRefinedSMB = AimiSmbTrainer.refine(predictedSMB, features)
        }
        var smbToGive: Float1 = if (tfliteFailed) {
            val fallback = pdCorrection.toFloat().coerceIn(0f, 0.5f)
            aapsLogger.debug(LTag.APS, "PD fallback: TFLite=0, pdCorrection=${"%.3f".format(pdCorrection)} → ${"%.2f".format(fallback)}U")
            fallback
        } else {
            mlRefinedSMB
        }
        val hyperfactor = SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hyper_factor, "50")) / 100.0
        val dynISFadjust2 = SafeParse.stringToDouble(sp.getString(R.string.key_DynISFAdjust, "120")) / 100.0


        // ════════════════════════════════════════════════════════════════
        // v2: Coletar alarmes ANTES de adjustFactors (elimina atraso 1 ciclo)
        // v2: Coletar alarmes ANTES de adjustFactors (elimina atraso 1 ciclo)
        // M2 (01/Ago/2026): coleta movida para setData() — aqui os alarmes já estão
        // frescos (populados em setData antes de determineTargetBgInSetData).
        calcularSmbTotalUltimas4h()

        // lowFactor com alarmes frescos
        if (alarmesLowGlucose24Horas > 5 && sp.getBoolean(R.string.low_alert_protection, true) == true) {
            lowFactor = alarmesLowGlucose24Horas * 20
        } else if (alarmesLowGlucoseUltimaHora > 0 && sp.getBoolean(R.string.low_alert_protection, true) == true) {
            lowFactor = alarmesLowGlucoseUltimaHora * 20
        } else {
            lowFactor = 0
        }

        val (adjustedMorningFactor, adjustedAfternoonFactor, adjustedEveningFactor) =
            adjustFactorsBasedOnBgAndHypo(bg.toDouble(), delta.toDouble())

        applySensorProtection()
        calculateTIRMetrics()
        determineHipoTrigger()

        applyStandardAdjustment()
        applySlowAdjustment()

        // ════════════════════════════════════════════════════════════════
        // FASE 1 — PK/PD INTEGRATION + DIGESTION DETECTOR (Jul/2026)
        // ════════════════════════════════════════════════════════════════
        val iobValues: List<Double> = iobArray?.map { it.iob.toDouble() } ?: emptyList()
        // Tarciso_REMOVER_ISF (09/Ago/2026): PK/PD usa hourlyfactor, nunca profile.
        val profileIsf: Double = hourlyfactor.coerceAtLeast(1.0)
        val tdd24hTotal: Double = tddCalculator.calculateDaily(-24, 0)?.totalAmount?.toDouble() ?: 0.0
        val exerciseDetected: Boolean = recentSteps5Minutes > 200 &&
            averageBeatsPerMinute > averageBeatsPerMinute180 && bg < 130

        lastBolusAgeMinutes = if (lastsmbtime > 0) lastsmbtime.toDouble() else 0.0

        val pkpdRuntimeTemp = PkPdIntegration.computeRuntime(
            epochMillis = dateUtil.now(), bg = bg,
            deltaMgDlPer5 = delta, iobU = iob,
            windowSinceLastDoseMin = lastBolusAgeMinutes,
            exerciseFlag = exerciseDetected,
            profileIsf = profileIsf, tdd24h = tdd24hTotal,
            iobValues = iobValues
        )
        if (pkpdRuntimeTemp != null) {
            pkpdRuntime = pkpdRuntimeTemp
            fusedIsf = pkpdRuntimeTemp.fusedIsf
        }

        digestionDetector.update(
            bg = bg, delta = delta, shortAvgDelta = shortAvgDelta,
            lastSmbMinutes = lastsmbtime, now = dateUtil.now()
        )
        // ════════════════════════════════════════════════════════════════


        // ******    IMPORTANT PARAMETER ************
        // Adjustments Phase 3 - maxIOB and maxSMB adjustment
        // DinMaxIob calc.
        // DinMaxIob dinâmico — interpola DENOM 45→40 de bg 110→150 (sem degrau bg>140)
        val baseDinMaxIob = ((bg / BG_BASELINE) * (bg / DIN_MAX_IOB_DENOM_HIGH) + (delta / 2.0)).coerceAtLeast(MAX_IOB_FLOOR)
        DinMaxIob = if (delta > 0 && bg > 110) {
            val progress = ((bg - 110).coerceIn(0.0, 40.0) / 40.0) // 0.0 em 110, 1.0 em 150
            val denomEff = DIN_MAX_IOB_DENOM_HIGH - (DIN_MAX_IOB_DENOM_HIGH - DIN_MAX_IOB_DENOM_LOW) * progress // 45→40
            val boosted = (bg / BG_BASELINE) * (bg / denomEff) + (delta / 2.0)
            maxOf(baseDinMaxIob, boosted).coerceAtMost(maxIob.toDouble())
        } else {
            baseDinMaxIob.coerceAtMost(maxIob.toDouble())
        }

        // ******    IMPORTANT PARAMETER ************
        // Tarciso Dynamic Max SMB
        DynMaxSmb = (((bg / 200) * (bg / 100)) + (delta / 2)).toDouble()
        val MaxlimitSMB = maxSMB * MAX_SMB_BUFFER
        if (DynMaxSmb < DYN_MAX_SMB_FLOOR) {
            DynMaxSmb = DYN_MAX_SMB_FLOOR
        } else if (DynMaxSmb > maxSMB && bg > 149 && delta >= 3) {
            DynMaxSmb = MaxlimitSMB
        } else if (DynMaxSmb > maxSMB) {
            DynMaxSmb = maxSMB.toDouble()
        }
        // M3 (01/Ago/2026): removido o branch no-op (DynMaxSmb < maxSMB → DynMaxSmb = DynMaxSmb).
        // Quando DynMaxSmb <= maxSMB sem condição especial, o valor dinâmico É o teto (inalterado).


        // ******    IMPORTANT PARAMETER ************
        // Tarciso LowFactor — REMOVIDO (movido para antes de adjustFactors)











        smbToGive = when {
            // bg > 180            -> (smbToGive * hyperfactor).toFloat()
            // N7 (03/Ago/2026): hora 0 (00:00-00:59) incluída no fator de período
            // noturno. Antes caía no else e ficava SEM multiplicador. Pré-existente.
            hourOfDay in 0..4   -> smbToGive * adjustedEveningFactor.toFloat()
            hourOfDay in 5..10  -> smbToGive * adjustedMorningFactor.toFloat()
            hourOfDay in 11..21 -> smbToGive * adjustedAfternoonFactor.toFloat()
            hourOfDay in 22..23 -> smbToGive * adjustedEveningFactor.toFloat()
            else                -> smbToGive
        }



        smbToGive = applySafetyPrecautions(smbToGive)
        smbToGive = roundToPoint05(smbToGive)

        logDataToCsv(false, predictedSMB, smbToGive, this.bg)
        logDataToCsv(true, predictedSMB, smbToGive, this.bg)

        // ML training trigger (fire-and-forget, rate-limited a 6h)
        if (sp.getBoolean(R.string.key_aimi_smb_refine, false)) {
            AimiSmbTrainer.maybeTrainAsync(recordsFile)
        }

        // Rounded to better view in Loop Log
        val maxIobround = (maxIob * 100).roundToInt() / 100.0
        val DinMaxIobround = (DinMaxIob * 100).roundToInt() / 100.0
        val MaxSMBround = (maxSMB * 100).roundToInt() / 100.0
        val DinMaxSMBround = (DynMaxSmb * 100).roundToInt() / 100.0
        // C3 (01/Ago/2026): maxSMB2 removido — MaxSMBProfround calculado direto de maxSMB
        MaxSMBProfround = ((maxSMB * 100).roundToInt() / 100.0)

        //val Autosense = (bg / tddBLast4Hrs)  - (delta * 4)
        //val Autosense2 = (bg / iob) - (delta * 4)



        val Autosense = (bg / tddBLast4Hrs).toDouble()
        val Autosense2 = if (iob !=0.0) {(bg / iob).toDouble()} else {0.0}
        val Autosense3 = if (iob !=0.0) {(delta / iob).toDouble()} else {0.0}



        this.protection = when {
            predictedBg < targetBg && hipoTrigger == HIPO_ACTIVE && delta >= sp.getDouble(R.string.key_openapsaimi_power_hipo, 110.0) -> "(OFF)"
            predictedBg < targetBg && hipoTrigger == HIPO_NONE && delta >= sp.getDouble(R.string.key_openapsaimi_power_std, 110.0) -> "(OFF)"
            predictedBg < targetBg -> "(ON)"
            else -> {"(OFF)"}
        }



        // val futureBG2 = (bg+(delta*1.5))-((iob/1.5)*variableSensitivity)

        val constraintStr =
            if(sp.getBoolean(R.string.auto_mode, true)===true){
             "Auto Mode: ON <br/>" +
             " MaxIOB: $MaxIobProfround -> $maxIobround -> ${DinMaxIobround} <br/> MaxSMB: $MaxSMBProfround -> $MaxSMBround -> ${DinMaxSMBround} <br/>" +
                "React Factor: ${(dynISFadjust2*100).roundToInt() } ->  ${(adjustDynIsf*100).roundToInt() } " +
              "<br/>" +
                 "Hourly Factor:  ${(hourlyfactor).roundToInt()} -> ${(adjustedMorningFactor * 100 ).roundToInt()}" +
              "<br/>" +
              "ISF: ${variableSensitivity.roundToInt()} / Stable BG:   $stable2 "

            } else {
             "Auto Mode: OFF <br/>" +
             " MaxIOB: $MaxIobProfround -> $maxIobround -> ${DinMaxIobround} <br/> MaxSMB: $MaxSMBProfround -> $MaxSMBround -> ${DinMaxSMBround} <br/>" +
          //  "React Factor: ${(dynISFadjust2*100).roundToInt() } -> ${(dynISFadjust2*100+variableSensitivity.coerceIn(65.0, 200.0)).roundToInt()+lowFactor} -> ${(adjustDynIsf*100).roundToInt()}" +
             "React Factor: ${(dynISFadjust2*100).roundToInt() } -> ${(adjustDynIsf*100).roundToInt()}" +
             "<br/>" +
         //   "Hourly Factor:  ${(hourlyfactor).roundToInt()} -> ${(hourlyfactor+variableSensitivity.coerceIn(30.0, 50.0)+ lowFactor).roundToInt()} -> ${(adjustedMorningFactor * 100 ).roundToInt()}" +
                 //

            "Hourly Factor:  ${(hourlyfactor).roundToInt()} ->  ${(adjustedMorningFactor * 100 ).roundToInt()}" +
            "<br/>" +
            "ISF: ${variableSensitivity.roundToInt()} / Stable BG:   $stable2 "

            }
            // "React Factor: ${(dynISFadjust2*100).roundToInt() } -> ${(dynISFadjust2*100+variableSensitivity.coerceIn(65.0, 200.0)).roundToInt()+lowFactor} -> ${(adjustDynIsf*100).roundToInt() } " +
            // "React Factor: ${((variableSensitivity.coerceIn(75.0, 250.0) * ((bg+40)/100)).roundToInt()) + lowFactor} -> ${(adjustDynIsf*100).roundToInt() } " +

            //"maxSMBTest:   ${(maxSMBTEST ) } " +
            //"(5 - 11) Morning Factor: ${(morningfactor * 100).roundToInt() } -> ${(adjustedMorningFactor * 100 ).roundToInt() } <br/>" +
            //"(11 - 22) Aft. Factor: ${(afternoonfactor * 100).roundToInt() } -> ${(adjustedAfternoonFactor * 100).roundToInt() } <br/>" +
            // "(22 - 5) Evening Factor: ${(eveningfactor * 100).roundToInt() } -> ${(adjustedEveningFactor * 100).roundToInt() } <br/>" +
            // "ISF:   ${ProfileISF.roundToInt()} -> ${variableSensitivity.roundToInt()} <br/>" +


        // "Meal Bolus: ${lastmealtime} MinAgo"
        val glucoseStr = " bg: $bg <br/> targetBg: ${(targetBg).roundToInt()} $targetStatus <br/> Protection: ${(predictedBg).roundToInt()} $protection <br/>" + // futureBgT:
            // "futureBg TESTE: ${futureBG2.roundToInt()} <br/> " +// $predictedBgT <br/>
            "delta: $delta <br/> short avg delta: $shortAvgDelta <br/> long avg delta: $longAvgDelta <br/>"//+
            //" accelerating_up: $accelerating_up <br/> deccelerating_up: $deccelerating_up <br/> accelerating_down: $accelerating_down <br/> deccelerating_down: $deccelerating_down <br/> stable:
        //$stable"
        val iobStr = " IOB: ${roundToPoint05(iob.toFloat())} <br/> tdd 7d/h: ${roundToPoint05(tdd7DaysPerHour.toFloat())} <br/> " +
            "tdd 2d/h : ${roundToPoint05(tdd2DaysPerHour.toFloat())} <br/> " +
            "tdd Last4h : ${roundToPoint05(tddBLast4Hrs.toFloat())}<br/>"
            //"Auto Sense TDD Test:   ${roundToPoint05(Autosense.toFloat()) } <br/>"+
            //"Auto Sense BG/IOB Test:   ${roundToPoint05(Autosense2.toFloat()) } <br/>"+
            //"Auto Sense Delta/IOB Test:   ${roundToPoint05(Autosense3.toFloat()) } <br/>"
            //"tdd daily/h : ${roundToPoint05(tddPerHour)} <br/> " +
            //"tdd 24h/h : ${roundToPoint05(tdd24HrsPerHour)}<br/>" +
            //"tdd Last4h : ${roundToPoint05(tddBLast4Hrs)}<br/>"
            //"basalaimi : $basalaimi <br/> basalsmb : $basalSMB <br/>"
        val profileStr = " Hour: $hourOfDay / Weekend: $weekend <br/>" +
            " 5m Steps: $recentSteps5Minutes / 10m Steps: $recentSteps10Minutes <br/> 15m Steps: $recentSteps15Minutes /" +
            " 30m Steps: $recentSteps30Minutes <br/> 60m Steps: $recentSteps60Minutes / 180m Steps: $recentSteps180Minutes <br/>" //+
            //" Heart Beat/mim(average 5 min) : $averageBeatsPerMinute <br/> Heart Beat/min(average 180 min) : $averageBeatsPerMinute180"

        var mealStr = "Bg Adjust: ${TirStatus}<br/> TDD Adjust: $TDDStatus<br/>" +
            //"Night Protection: $nightprotection<br/>" +
            "TIR Low: ${(lastHourTIRLow).roundToInt()}%/h, ${(currentTIRLow).roundToInt()}%/24h<br/>"+
            "Low Glucose Alarms: ${alarmesLowGlucoseUltimaHora}/h, ${alarmesLowGlucose24Horas}/24h<br/>"

            //"Delta Adjustment: $DeltaStatus<br/>"+ // tags180to240minAgo: $tags180to240minAgo<br/> " +
            // "lastHourAbove: ${(lastHourTIRAbove95).roundToInt()}%<br/> todayTIRAbove: ${(currentTIRAbove).roundToInt()}%<br/> todayTIRRange: ${(currentTIRRange).roundToInt()}%<br/> " +
            // "todayTIRLow: ${(currentTIRLow).roundToInt()}%<br/> lastHourLow: ${(lastHourTIRLow).roundToInt()}%<br/>"
        val reason = "Requested ${smbToGive}u to the pump" +
            ",<br/>AIMI.1 ML.2, 14/11/2023 <br/>" +
            "Chg. Ver. $BUILD_VERSION"
        //val reason = "SMB predicted ${roundToPoint001(predictedSMB)}u, but requested ${smbToGive}u to the pump" +
        //    ",<br/>Plugin version OpenApsAIMI.1 ML.2, 14 Novembre 2023"
        val targetstr = targetBg.toString()
        val targetProtectionstr = (predictedBg.roundToInt()).toString()
        val determineBasalResultAIMISMB = DetermineBasalResultAIMISMB(injector, smbToGive, constraintStr, glucoseStr, iobStr, profileStr, mealStr, reason, targetstr, targetProtectionstr)

        glucoseStatusParam = glucoseStatus.toString()
        iobDataParam = iobData.toString()
        currentTempParam = currentTemp.toString()
        profileParam = profile.toString()
        mealDataParam = mealData.toString()
        return determineBasalResultAIMISMB
    }

    // L2 (01/Ago/2026): formatter cacheado — getFormattedDateTime é chamado 2×/ciclo
    // (576×/dia). DateTimeFormatter é thread-safe e imutável.
    private val csvDateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.US)

    private fun getFormattedDateTime(): String {
        return try {
            java.time.Instant.ofEpochMilli(dateUtil.now())
                .atZone(java.time.ZoneId.systemDefault())
                .format(csvDateTimeFormatter)
        } catch (e: Exception) {
            // Fallback para SimpleDateFormat caso a API moderna não esteja disponível
            java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US)
                .format(Date(dateUtil.now()))
        }
    }


    private fun logDataToCsv(hb: Boolean, predictedSMB: Float1, smbToGive: Float1, bgCorrigido: Double) {
        //val dateStr = dateUtil.dateAndTimeString(dateUtil.now())
        val dateStr = getFormattedDateTime()

        if (hb) {
            val file = recordsHBFile
            rotateCsvIfNeeded(file, isHb = true)
            if (!file.exists() || file.length() == 0L) {
                val headerRow = "dateStr,dateLong,hourOfDay,weekend," +
                    "bg,targetBg,iob,cob,lastCarbAgeMin,futureCarbs,delta,shortAvgDelta,longAvgDelta," +
                    "accelerating_up,deccelerating_up,accelerating_down,deccelerating_down,stable," +
                    "tdd7DaysPerHour,tdd2DaysPerHour,tddDailyPerHour,tdd24HrsPerHour," +
                    "recentSteps5Minutes,recentSteps10Minutes,recentSteps15Minutes,recentSteps30Minutes,recentSteps60Minutes,recentSteps180Minutes,averageBeatsPerMinute, averageBeatsPerMinute180," +
                    "tags0to60minAgo,tags60to120minAgo,tags120to180minAgo,tags180to240minAgo," +
                    "variableSensitivity,predictedSMB,DinMaxIob,DynMaxSmb,smbGiven,bgConfidence,bgCorrigido\n"
                file.appendText(headerRow)
            }
            val valuesToRecord = "$dateStr,${dateUtil.now()},$hourOfDay,$weekend," +
                "$bg,$targetBg,$iob,$cob,$lastCarbAgeMin,$futureCarbs,$delta,$shortAvgDelta,$longAvgDelta," +
                "$accelerating_up,$deccelerating_up,$accelerating_down,$deccelerating_down,$stable," +
                "$tdd7DaysPerHour,$tdd2DaysPerHour,$tddPerHour,$tdd24HrsPerHour," +
                "$recentSteps5Minutes,$recentSteps10Minutes,$recentSteps15Minutes,$recentSteps30Minutes,$recentSteps60Minutes,$recentSteps180Minutes," +
                "$averageBeatsPerMinute, $averageBeatsPerMinute180," +
                "$tags0to60minAgo,$tags60to120minAgo,$tags120to180minAgo,$tags180to240minAgo," +
                "$variableSensitivity,$predictedSMB,$DinMaxIob,$DynMaxSmb,$smbToGive,$bgConfidence,$bgCorrigido"
            file.appendText(valuesToRecord + "\n")
        } else {
            val file = recordsFile
            rotateCsvIfNeeded(file, isHb = false)
            if (!file.exists() || file.length() == 0L) {
                val headerRow = "dateStr,dateLong,hourOfDay,weekend," +
                    "bg,targetBg,iob,cob,lastCarbAgeMin,futureCarbs,delta,shortAvgDelta,longAvgDelta," +
                    "tdd7DaysPerHour,tdd2DaysPerHour,tddPerHour,tdd24HrsPerHour," +
                    "recentSteps5Minutes,recentSteps10Minutes,recentSteps15Minutes,recentSteps30Minutes,recentSteps60Minutes,recentSteps180Minutes," +
                    "tags0to60minAgo,tags60to120minAgo,tags120to180minAgo,tags180to240minAgo," +
                    "predictedSMB,maxIob,maxSMB,smbGiven,bgConfidence,bgCorrigido,bruto,ajustes,pkpd,damping,brake,finalize,maxLimits,tier,final\n"
                file.appendText(headerRow)
            }
            val valuesToRecord = "$dateStr,${dateUtil.now()},$hourOfDay,$weekend," +
                "$bg,$targetBg,$iob,$cob,$lastCarbAgeMin,$futureCarbs,$delta,$shortAvgDelta,$longAvgDelta," +
                "$tdd7DaysPerHour,$tdd2DaysPerHour,$tddPerHour,$tdd24HrsPerHour," +
                "$recentSteps5Minutes,$recentSteps10Minutes,$recentSteps15Minutes,$recentSteps30Minutes,$recentSteps60Minutes,$recentSteps180Minutes," +
                "$tags0to60minAgo,$tags60to120minAgo,$tags120to180minAgo,$tags180to240minAgo," +
                "$predictedSMB,${DinMaxIob},${DynMaxSmb},$smbToGive,$bgConfidence,$bgCorrigido," +
                "${"%.2f".format(java.util.Locale.US, lastEtapaBruto.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaAjustes.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaPkpd.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaDamping.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaBrake.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaFinalize.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaMaxLimits.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaTier.toDouble())},${"%.2f".format(java.util.Locale.US, lastEtapaFinal.toDouble())}"
            file.appendText(valuesToRecord + "\n")
        }
    }
    private fun applySafetyPrecautions(smbToGiveParam: Float1): Float1 {
        var smbToGive = smbToGiveParam
        val etapaBruto = smbToGive

        // Vérifier les conditions de sécurité critiques
        if (isCriticalSafetyCondition()) {
            // CSV phases: registra bruto e zera restante (final=0)
            lastEtapaBruto = etapaBruto
            lastEtapaAjustes = 0f
            lastEtapaPkpd = 0f
            lastEtapaDamping = 0f
            lastEtapaBrake = 0f
            lastEtapaFinalize = 0f
            lastEtapaMaxLimits = 0f
            lastEtapaTier = 0f
            lastEtapaFinal = 0f
            return 0.0f  // Arrêt immédiat si une condition de sécurité critique est remplie
        }

        // Ajustements basés sur des conditions spécifiques
        smbToGive = applySpecificAdjustments(smbToGive)
        val etapaAjustes = smbToGive

        // ════════════════════════════════════════════════════════════════
        // FASE 1 — PKPD ABSORPTION GUARD + SMB DAMPING (Jul/2026)
        // ════════════════════════════════════════════════════════════════
        val isDigesting = digestionDetector.isActive()
        val pkpdGuard = PkpdAbsorptionGuard.compute(
            pkpdRuntime = pkpdRuntime,
            windowSinceLastDoseMin = lastBolusAgeMinutes, bg = bg,
            delta = delta, shortAvgDelta = shortAvgDelta,
            targetBg = targetBg, predBg = predictedBg,
            isMealMode = isDigesting,
            isConfirmedHighRise = accelerating_up >= 2 && delta > 2.0
        )
        val beforeGuard = smbToGive
        smbToGive = (smbToGive * pkpdGuard.factor.toFloat()).coerceAtLeast(0f)
        if (smbToGive < beforeGuard) {
            aapsLogger.debug(LTag.APS, "PKPD Guard (${pkpdGuard.reason}): " +
                "%.2f".format(beforeGuard.toDouble()) + " -> " +
                "%.2f".format(smbToGive.toDouble()) + " U")
        }
        val etapaPkpd = smbToGive

        val damping = SmbDampingUsecase.run(
            SmbDampingUsecase.Input(
                smbDecision = smbToGive.toDouble(),
                exercise = recentSteps5Minutes > 200 && averageBeatsPerMinute > averageBeatsPerMinute180,
                suspectedLateFatMeal = isDigesting && hourOfDay in 20..23,
                mealModeRun = isDigesting,
                highBgRiseActive = accelerating_up >= 2 && delta > 2.0
            ),
            lastBolusAgeMinutes = lastBolusAgeMinutes,
            peakTimeMinutes = pkpdRuntime?.peakMin ?: 75.0
        )
        if (damping.smbAfterDamping.toFloat() < smbToGive) {
            aapsLogger.debug(LTag.APS, "SMB Damping: " +
                "%.2f".format(smbToGive.toDouble()) + " -> " +
                "%.2f".format(damping.smbAfterDamping) + " U")
        }
        smbToGive = damping.smbAfterDamping.toFloat()
        val etapaDamping = smbToGive
        // ════════════════════════════════════════════════════════════════

        // ════════════════════════════════════════════════════════════════
        // Proposta #1 — SMB Cumulative Brake (Tarciso, Jul/2026)
        // ════════════════════════════════════════════════════════════════
        // Conta SMBs administrados nas últimas 4 horas e aplica freio
        // progressivo se o volume acumulado exceder o threshold.
        // Bypassa o DIA: mesmo que o IOB calculado esteja baixo (por DIA curto),
        // o freio impede que o sistema acumule insulina em excesso.
        // ════════════════════════════════════════════════════════════════
        smbToGive = applyCumulativeSmbBrake(smbToGive)
        val etapaBrake = smbToGive

        smbToGive = finalizeSmbToGive(smbToGive)
        val etapaFinalize = smbToGive

        // Appliquer les limites maximum
        smbToGive = applyMaxLimits(smbToGive)
        val etapaMaxLimits = smbToGive

        // ═══════════════════════════════════════════════════════════════════
        // FASE 2 — BG Confidence Multiplier (Tarciso, 11/Ago/2026)
        // ═══════════════════════════════════════════════════════════════════
        // Liga o veredito da rede neural de confiança do BG ao SMB final.
        // Multiplicador NUNCA aumenta (máx 1.0), BAD não zera (×0.5),
        // piso 0.8 durante digestão/subida (Melhoria D — lag fisiológico).
        // Sem modelo (retreino) → tier 0 → ×1.0 → comportamento inalterado (fail-safe).
        // Reversão: remover este bloco.
        val confMultiplier = BgConfidenceGuard.smbMultiplier(this.bgConfidence, isDigesting, delta)
        // MELHORIA-6: loga SEMPRE (inclusive tier 0 / ×1.0) para diagnóstico do
        // veredito da rede neural de confiança do BG.
        aapsLogger.debug(
            LTag.APS,
            "BG Confidence: tier=$bgConfidence, bg=" + "%.1f".format(bg) +
                ", delta=" + "%.1f".format(delta) + ", mult=$confMultiplier, smb=" +
                "%.2f".format(smbToGive.toDouble()) + " U"
        )
        if (confMultiplier < 1.0) {
            val adjusted = (smbToGive * confMultiplier.toFloat()).coerceAtLeast(0f)
            aapsLogger.debug(LTag.APS, "  → SMB ajustado: " + "%.2f".format(adjusted.toDouble()) + " U")
            smbToGive = adjusted
        }
        val etapaTier = smbToGive

        // SMB Chain — log único da cadeia completa (sempre emitido, debug, LTag.APS)
        // Locale.US fixo — evita vírgula decimal em locale pt_BR
        aapsLogger.debug(LTag.APS, String.format(java.util.Locale.US,
            "SMB Chain: bruto=%.2f -> ajustes=%.2f -> pkpd=%.2f -> damping=%.2f -> brake=%.2f -> finalize=%.2f -> maxLimits=%.2f -> tier=%.2f -> final=%.2f U",
            etapaBruto, etapaAjustes, etapaPkpd, etapaDamping, etapaBrake, etapaFinalize, etapaMaxLimits, etapaTier, smbToGive))

        // Persiste fases para o CSV (9 cols suffix) — impacto zero no dosing, só serialização
        lastEtapaBruto = etapaBruto
        lastEtapaAjustes = etapaAjustes
        lastEtapaPkpd = etapaPkpd
        lastEtapaDamping = etapaDamping
        lastEtapaBrake = etapaBrake
        lastEtapaFinalize = etapaFinalize
        lastEtapaMaxLimits = etapaMaxLimits
        lastEtapaTier = etapaTier
        lastEtapaFinal = smbToGive

        // smbToGive = (smbToGive + mealTrigger).toFloat()
        return smbToGive
    }



    private fun applyMaxLimits(smbToGive: Float1): Float1 {
        var result = smbToGive
        // Vérifiez d'abord si smbToGive dépasse maxSMB
        if (result > DynMaxSmb) {
            result = DynMaxSmb.toFloat()
        }

       // Ensuite, vérifiez si la somme de iob et smbToGive dépasse maxIob
        if (iob + result > DinMaxIob) {
            // FIX 02/Ago/2026: coerceAtLeast(0f) — quando iob > DinMaxIob, o cálculo
            // (DinMaxIob - iob) gerava SMB NEGATIVO, que era retornado DEPOIS do
            // finalizeSmbToGive (clamp ≥0) e travava a dosagem. Causa raiz da
            // diferença de 3.9U vs NS no período 01-02/Ago. Nunca negativo.
            result = (DinMaxIob - iob).toFloat().coerceAtLeast(0f)
        }
        // result = (result + mealTrigger).toFloat()
        return result
    }

    // ═══════════════════════════════════════════════════════════════════
    // Proposta #1 — SMB Cumulative Brake (Tarciso, Jul/2026)
    // ═══════════════════════════════════════════════════════════════════
    private fun applyCumulativeSmbBrake(smbToGive: Float1): Float1 {
        try {
            // ═══════════════════════════════════════════════════════════════════
            // P2 — Extended Morning Window (Tarciso, Jul/2026)
            // ═══════════════════════════════════════════════════════════════════
            // Se é período matinal (4-10h) e não houve hiper recente, usar janela
            // de 8h para capturar SMBs da noite anterior que podem estar causando
            // hipo matinal (ex: SMB às 23h → hipo às 07h, IOB já expirou pelo DIA
            // curto mas o efeito cumulativo permanece).
            // Se houve hiper recente (bgAltoUltimas4h), mantém 4h — a hiper justifica
            // os SMBs, não há overshoot suspeito.
            // ═══════════════════════════════════════════════════════════════════
            val isMorning = hourOfDay in 4..10
            val lookbackHours = if (isMorning && !bgAltoUltimas4h) 8 else 4
            val lookbackWindowStart = now - lookbackHours * 60 * 60 * 1000L
            val smbsUltimas4h = repository.getBolusesDataFromTimeToTime(lookbackWindowStart, now, false)
                .timeout(3, TimeUnit.SECONDS)
                .onErrorReturnItem(emptyList())
                .blockingGet()
                .filter { it.isValid && it.type == Bolus.Type.SMB }

            val totalSMB = smbsUltimas4h.sumOf { it.amount }
            val threshold = 3.0  // 3U de SMB em 4h

            if (totalSMB > threshold) {
                val excess = totalSMB - threshold
                var brakeFactor = (1.0 - (excess / (threshold * 2.0)))
                    .coerceIn(0.0, 1.0)

                // ═══════════════════════════════════════════════════════════════════
                // Proportional Distribution Brake (Tarciso, Jul/2026)
                // ═══════════════════════════════════════════════════════════════════
                // Se SMBs concentrados em poucas horas → brake mais suave (refeição)
                // Se SMBs dispersos em muitas horas → brake mais agressivo (excesso)
                // distributionFactor: 0.5 (1h ativa) a 1.0 (4h+ ativas)
                // Threshold hora ativa: > 0.3U
                // ═══════════════════════════════════════════════════════════════════
                if (smbsUltimas4h.isNotEmpty()) {
                    val smbByHour = smbsUltimas4h.groupBy { getHourOfDay(it.timestamp) }
                    val activeHours = smbByHour.count { (_, boluses) ->
                        boluses.sumOf { it.amount } > 0.3
                    }
                    val distributionFactor = (activeHours / 4.0).coerceIn(0.5, 1.0)
                    brakeFactor *= distributionFactor
                    aapsLogger.debug(LTag.APS,
                        "Distribution Brake: ${activeHours}h ativas (>0.3U), " +
                        "distFactor=$distributionFactor, brake=$brakeFactor")
                }

                // ── ADAPTATIVO HIPO 0 + NOITE INTEGRAL (24/08/2026) ──
                val isNight = hourOfDay in 0..5 // 00-06h: hipo noturna mais perigosa → freio integral
                val hypoRisk = bg < 120 || (bg < targetBg + 30 && delta < 0) || iob > 4.5
                val hyperSeverity = when {
                    isNight -> 0.0 // pode flexibilizar se freio noturno estiver excessivo
                    !hypoRisk && bg > 180 && delta > 2.5 && predictedBg > targetBg + 50 -> 0.70
                    !hypoRisk && bg > 160 && delta > 1.5 -> 0.50
                    !hypoRisk && bg > 150 && delta > 0.5 -> 0.30
                    else -> 0.0
                }
                if (!isNight && !hypoRisk && hyperSeverity > 0) {
                    val oldBrake = brakeFactor
                    brakeFactor = (brakeFactor + (1.0 - brakeFactor) * hyperSeverity).coerceIn(0.0, 1.0)
                    aapsLogger.debug(LTag.APS, "BRAKE Adapt: hypoRisk=$hypoRisk isNight=$isNight hyper=$hyperSeverity brake ${"%.3f".format(oldBrake)}→${"%.3f".format(brakeFactor)}")
                } else if (isNight) {
                    aapsLogger.debug(LTag.APS, "BRAKE Adapt: isNight=$isNight → freio integral mantido (hyperSeverity ignorado)")
                }
                val reducedSmb = (smbToGive * brakeFactor).toFloat()
                aapsLogger.debug(LTag.APS,
                    "SMB Cumulative Brake: ${"%.1f".format(totalSMB)}U in ${lookbackHours}h " +
                    "(threshold=$threshold, excess=${"%.1f".format(excess)}U) " +
                    "brake=${"%.2f".format(brakeFactor)}")
                return reducedSmb
            }
            return smbToGive
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "SMB Cumulative Brake failed", e)
            return smbToGive
        }
    }

    // Tarciso start finger stick BG event
    // Check for finger stick BG events in the last 10 minutes

    // Tarciso start finger stick BG event
    // Check for finger stick BG events in the last 10 minutes
    private fun isBGfingerEvent(): List<TherapyEvent>? {
        val tenMinutesAgo = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)
        }.timeInMillis

        val BGfingerEvent = repository.getTherapyEventDataFromTime(tenMinutesAgo, TherapyEvent.Type.FINGER_STICK_BG_VALUE, true)
            .timeout(3, TimeUnit.SECONDS)
            .onErrorReturnItem(emptyList())
            .blockingGet()

    return BGfingerEvent
    }



    // Tarciso start Sensor Change Check - Últimas 24 Horas
    private fun isSensorChangeEvent(): List<TherapyEvent>? {
        val twentyfourHoursAgo = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
            //timeInMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        }.timeInMillis
        // Busca eventos do tipo SENSOR_CHANGE nas últimas 24 horas
        var sensorChangeEvents = repository.getTherapyEventDataFromTime(
            twentyfourHoursAgo,
            TherapyEvent.Type.SENSOR_CHANGE,
            true
        )
            .timeout(3, TimeUnit.SECONDS)
            .onErrorReturnItem(emptyList())
            .blockingGet()
        // Verifica se houve eventos de mudança de sensor nas últimas 24 horas
        return sensorChangeEvents
    }





    private fun isNoteEvent(): List<TherapyEvent>? {
        val twentyfourHoursAgo = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
            //timeInMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        }.timeInMillis
        // Busca eventos do tipo SENSOR_OK nas últimas 24 horas
        var NoteEvents = repository.getTherapyEventDataFromTime(
            twentyfourHoursAgo,
            TherapyEvent.Type.NOTE,
            true
        )
            .timeout(3, TimeUnit.SECONDS)
            .onErrorReturnItem(emptyList())
            .blockingGet()
        // Verifica se houve eventos de mudança de sensor nas últimas 24 horas
        return NoteEvents
    }






    private fun isCriticalSafetyCondition(): Boolean {
        val belowMinThreshold = bg < 80
        val belowTargetAndDropping = bg < targetBg && delta < -2
        val belowTargetAndStableButNoCob = bg < targetBg - 15 && shortAvgDelta <= 2
        // val dropping = bg < 140 && delta < 0
        val droppingFast = bg < 150 && delta < -5
        val droppingFastAtHigh = bg < 200 && delta < -7
        val droppingVeryFast = delta < -10
        val predictionhipo = hipoTrigger == HIPO_ACTIVE && predictedBg < targetBg && delta <= sp.getDouble(R.string.key_openapsaimi_power_hipo, 110.0) //Hipo
        val prediction = hipoTrigger == HIPO_NONE && predictedBg < targetBg && delta < sp.getDouble(R.string.key_openapsaimi_power_std, 110.0) // Standard
        //val predictionhipo2 = hipoTrigger > 0 && predictedBg < targetBg && delta <= 1.5 && bg <= 140
        val interval = predictedBg < targetBg && delta > 10 && iob >= maxSMB && lastsmbtime < 10
        val targetinterval = targetBg >= 130 && delta > 0 && iob >= maxSMB && lastsmbtime < 15

        // Tarciso: New trigger: Between 11:00 PM and 05:00 AM, and delta greater than 15
        val nightTrigger = LocalTime.now().run { (hour in NIGHT_HOUR_START..23 || hour in 0..NIGHT_HOUR_END) } && delta > 15 && cob == 0.0

        // Tarciso: Falling Trajectory Guard (Proposta #4, Jul/2026)
        // Detecta queda consistente pós-hiper noturno com IOB artificialmente baixo
        // Condições: delta negativo sustentado + BG baixo/estável + IOB residual baixo
        // + hiper recente + horário noturno
        // Previne que o sistema dê SMB quando o BG já está em declínio noturno
        val isNight = LocalTime.now().run { hour in 22..23 || hour in 0..5 }
        val slowDeclineNoturno = delta < 0 && shortAvgDelta < 0 &&
            iob < 1.0 && bgAltoUltimas4h && isNight

        // ═══════════════════════════════════════════════════════════════════
        // SMB Exhaustion Guard (Tarciso, Jul/2026 — v3)
        // ═══════════════════════════════════════════════════════════════════
        // Bloqueia SMBs quando o sistema já está saturado de insulina
        // e o BG está em trajetória de queda. O IOB pode ser baixo (DIA
        // curto) mas o SMB acumulado nas últimas 4h ainda está ativo.
        // Condições: SMB total > 4.0U em 4h + BG caindo + horário noturno
        // ═══════════════════════════════════════════════════════════════════
        val smbExhaustion = smbTotalUltimas4h >= 4.0 && delta < 0 && shortAvgDelta <= 0 && isNight

        // ═══════════════════════════════════════════════════════════════════
        // Hypo Recovery Guard (Tarciso, Jul/2026)
        // ═══════════════════════════════════════════════════════════════════
        // Bloqueia SMBs durante a recuperação de uma hipo noturna.
        // Um BG subindo DE hipo (75→98, delta>0) NÃO é subida real —
        // é recuperação fisiológica. SMBs neste período causam overshoot
        // com alto risco de segunda hipo (Evento 04:40 observado em 23/Jul).
        // Usa lowestBgUltimaHora (query direta nas leituras de BG da última
        // hora, threshold ≤70) em vez de alarmesLowGlucoseUltimaHora, pois
        // o alarme pode não refletir todas as hipos (depende de snooze/
        // configuração do alerta).
        // Condições: BG ≤70 na última hora + BG atual < 96 + delta positivo
        // (recuperando) + IOB baixo (< 1.0) + horário noturno (22-5)
        // ═══════════════════════════════════════════════════════════════════
        val hypoRecovery = lowestBgUltimaHora <= 70.0 &&
            bg < 96 && delta > 0 && iob <= 0.9 && isNight

        // Tarciso: New trigger: avoid SMB after calibration with delta < 3
        // val isNewCalibration = XdripCalibration.toString().contains("FINGER_STICK_BG_VALUE", ignoreCase = true) && delta > 1

        // ─── PROPOSTA 05/Ago (Opção A) — Lockout pós-alarme low glucose ───
        // Ativo 24h. Saída: o que vier primeiro — 30 min desde o alarme OU BG ≥ 110.
        // Replica manualmente o que o usuário fez na noite de 04→05/08 (bomba off 30min).
        // Lê o timestamp do ÚLTIMO alarme de low glucose (JSONArray de longs no SP).
        // ─────────────────────────────────────────────────────────────────────
        val alarmLockoutAtivo = alarmeLowRecente()

        return belowMinThreshold || belowTargetAndDropping || belowTargetAndStableButNoCob ||
            droppingFast || droppingFastAtHigh || droppingVeryFast || prediction || interval || targetinterval ||
            // droppingFast || droppingFastAtHigh || droppingVeryFast || targetinterval || dropping ||
             nightTrigger  || predictionhipo  || slowDeclineNoturno || hypoRecovery || smbExhaustion || alarmLockoutAtivo // Tarciso: Triggers added

    }



    private fun applySpecificAdjustments(smbToGive: Float1): Float1 {
        var result = smbToGive
        val safetysmb = recentSteps180Minutes > 900 && bg < 130
        val safetyWeekend = weekend > 0 && LocalTime.now().run {(hour in 9..10)} && bg < 130 && recentSteps180Minutes > 500
        if (safetysmb || safetyWeekend) {
            result /= 2
        }

        if (recentSteps5Minutes > 100 && recentSteps30Minutes > 500 && lastsmbtime < 20) {
            result = 0.0f
        }

        // ═══════════════════════════════════════════════════════════════════
        // P1 — Near-Miss Hypo + Bounce Protection (Tarciso, Jul/2026)
        // ═══════════════════════════════════════════════════════════════════
        // Soft guards que previnem SMB em situações de alto risco de hipo.
        // Usam fator combinado (else-if) para evitar double-penalty: se ambos
        // os cenários fossem verdadeiros (ex: BG 85 caindo + hipo recente),
        // o SMB seria reduzido uma única vez (0.5×), não duas (0.25×).
        // ═══════════════════════════════════════════════════════════════════
        // Guard 1 — Near-Miss: BG 80-100 com tendência de queda sustentada
        //   O sistema ainda não detectou hipo (lowestBg > 70) mas a trajetória
        //   é claramente descendente. Reduz SMB preventivamente.
        // ═══════════════════════════════════════════════════════════════════
        // Guard 2 — Bounce: hipo recente (lowestBg ≤ 70 na última hora) com
        //   recuperação em andamento (delta > 0, bg < 160). O BG subindo NÃO
        //   é subida real — é recuperação fisiológica pós-hipo. SMBs neste
        //   período causam o padrão de overshoot (ex: evento 04:40).
        // ═══════════════════════════════════════════════════════════════════
        val nearMiss = bg in 80.0..100.0 && shortAvgDelta < -1.0
        val bounce = lowestBgUltimaHora <= 70.0 && bg < 160.0 && delta > 0
        val softGuardFactor = when {
            nearMiss || bounce -> 0.5f
            else -> 1.0f
        }
        if (softGuardFactor < 1.0f) {
            val prevResult = result
            result *= softGuardFactor
            aapsLogger.debug(LTag.APS,
                "P1 Soft Guard: factor=$softGuardFactor " +
                "(bg=$bg, delta=$delta, shortAvgDelta=$shortAvgDelta, " +
                "lowestBg=$lowestBgUltimaHora) $prevResult → $result")
        }

        // ════════════════════════════════════════════════════════════════
        // Proposta #5 — Rate Limiter Contextual Noturno (Tarciso, Jul/2026)
        // v2 — Adicionado guardrail BG baixo+plano + teto else noturno
        // ════════════════════════════════════════════════════════════════
        val isNight = LocalTime.now().run { hour in 22..23 || hour in 0..5 }
        if (isNight) {
            result = when {
                // ─── Guardrail: BG baixo e plano → SMB reduzido ───
                // Evita SMB quando BG já está baixo com tendência plana/descendo
                // v2.1: iob<2.0 (vs 1.0) para cobrir cenários com IOB residual
                bg < 95 && iob < 2.0 && delta < 2.0
                    && shortAvgDelta < 1.0
                    && longAvgDelta < 0.2 -> result.coerceAtMost(0.1f)

                // ═══════════════════════════════════════════════════════════════════
                // Pós-hiper noturno com alto volume de SMB — proteção contra stacking
                // (Tarciso, Jul/2026 — v3: substitui iob<0.7 por smbTotalUltimas4h>4.0)
                // v4 (P2, Jul/2026) — Adicionado bgAltoRatio: se >50% das leituras >140
                // (elevação prolongada), permite SMBs maiores porque o sistema precisa
                // tratar — não é pico isolado. Tiers:
                //   Prolongada + BG ≥ 170: 0.8U (severa, tratar)
                //   Prolongada + BG ≥ 140: 0.4U (moderada, tratar)
                //   Prolongada + BG < 140: 0.2U (stacking, proteger)
                //   Pico isolado + BG ≥ 170: 0.6U (comportamento original)
                //   Pico isolado + BG < 170: 0.2U (comportamento original)
                // ═══════════════════════════════════════════════════════════════════
                bgAltoUltimas4h && smbTotalUltimas4h > 4.0 -> {
                    if (bgAltoRatio > 0.5) {
                        // Elevação prolongada: permite mais SMB porque a hiper é sustentada
                        when {
                            bg >= 170 -> result.coerceAtMost(0.8f)
                            bg >= 140 -> result.coerceAtMost(0.4f)
                            else -> result.coerceAtMost(0.2f)
                        }
                    } else {
                        // Pico isolado: proteção contra stacking (comportamento original)
                        if (bg >= 170) result.coerceAtMost(0.6f)
                        else result.coerceAtMost(0.2f)
                    }
                }

                // Noturno estável → 0.7U máx/ciclo
                // Se BG < 100, protege ainda mais → 0.5U
                delta in -1.0..1.0 && iob < 1.7 && bg < 120.0 -> {
                    if (bg < 100.0) result.coerceAtMost(0.5f)
                    else result.coerceAtMost(0.7f)
                }

                // ═══════════════════════════════════════════════════════════════════
                // Subida modesta noturna — Severity Tiers (Tarciso, Jul/2026)
                // ═══════════════════════════════════════════════════════════════════
                // Adiciona tiers baseados no BG: quanto mais alto, mais SMB.
                // BG ≥ 140 → 0.7U (mais alto, precisa tratar)
                // BG 120-139 → 0.5U (moderado, original)
                // BG 95-119 → 0.3U (mais baixo, proteger)
                // ═══════════════════════════════════════════════════════════════════
                delta in 1.0..3.0 && bg >= 95.0 && bg < 150.0 && iob < 1.5 -> {
                    when {
                        bg >= 140.0 -> result.coerceAtMost(0.7f)
                        bg >= 120.0 -> result.coerceAtMost(0.5f)
                        else       -> result.coerceAtMost(0.3f)
                    }
                }

                // ═══════════════════════════════════════════════════════════════════
                // Noturno subindo forte (refeição tardia) — Severity Tiers (Jul/2026)
                // ═══════════════════════════════════════════════════════════════════
                // BG ≥ 200 → 1.5U (hiper severa, tratar)
                // BG ≥ 160 → 1.2U (moderada, original)
                // BG 120-159 → 0.8U (leve, proteger)
                // ═══════════════════════════════════════════════════════════════════
                delta > 2.5 && bg >= 120.0 -> {
                    when {
                        bg >= 200.0 -> result.coerceAtMost(1.5f)
                        bg >= 160.0 -> result.coerceAtMost(1.2f)
                        else       -> result.coerceAtMost(0.8f)
                    }
                }

                // Qualquer outro caso noturno → teto conservador 0.3U
                // Fecha a brecha do else original
                else -> result.coerceAtMost(0.3f)
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SMB Load Brake (Tarciso, Jul/2026)
        // ═══════════════════════════════════════════════════════════════════
        // Reduz o SMB quando o sistema está sob alta carga (SMB acumulado
        // ≥5U nas últimas 4h), prevenindo quedas por excesso de insulina
        // circulante. O filtro desliga se a trajetória de subida for
        // confirmada (todos os 3 deltas positivos), garantindo resposta
        // total a hipers reais.
        // ═══════════════════════════════════════════════════════════════════
        if (smbTotalUltimas4h >= 5.0) {
            val trajectoryConfiavel = delta > 0 && shortAvgDelta > 0 && longAvgDelta > 0
            if (!trajectoryConfiavel) {
                val loadFactor = when {
                    smbTotalUltimas4h >= 8.0 -> 0.4f
                    smbTotalUltimas4h >= 6.0 -> 0.6f
                    else -> 0.8f  // 5.0..5.9
                }
                aapsLogger.debug(LTag.APS,
                    "SMB Load Brake: ${"%.1f".format(smbTotalUltimas4h)}U/4h " +
                    "factor=$loadFactor (traj=${(delta>0&&shortAvgDelta>0&&longAvgDelta>0)})")
                result *= loadFactor
            }
        }

        return result
    }



    private fun finalizeSmbToGive(smbToGive: Float1): Float1 {
        var result = smbToGive
        // Assurez-vous que smbToGive n'est pas négatif
        if (result < 0.0f) {
            result = 0.0f
        }

       // if (result == 0.0f && delta > 1 && bg > 100 && lastsmbtime > 20 && lastHourTIRAbove95 > 99 && LocalTime.now().run { hour in 6..21 }) {
       //     result = result + 0.3f
       //     TirStatus = "Forced SMB (TIR95)"
       // }


        // Logique finale pour ajuster smbToGive
        /*if (result == 0.0f && delta > 2 && bg > 100 && lastsmbtime > 20 && predictedBg > targetBg) {
            result = if ((iob + basalSMB) > maxIob) maxIob - iob else basalSMB
        }*/
        //var night_mode = night_mode
        //if (night_mode = true) {
        //    if (LocalTime.now().run { (hour in 21..23 || hour in 0..2) } && bg < 135 && iob < maxSMB) result /= 2
        //}
        //val night_mode = SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_night_mode,"false")) == 1.0
        //if (night_mode) {
        //}

        return result
    }


    private fun roundToPoint05(number: Float): Float1 {
        return (number * 20.0).roundToInt() / 20.0f
    }

    /**
     * Calcula SMB a partir do modelo TFLite.
     * v3 (01/Ago/2026): retorno nullable — C2+M6.
     *   Float  = decisão do modelo (0.0F é LEGÍTIMO: modelo decidiu não dar SMB)
     *   null   = falha real (modelo corrompido, erro de run, sem modelo)
     * A criação do Interpreter e o run estão no MESMO try-catch: um arquivo
     * .tflite corrompido não derruba mais o ciclo APS (C2).
     */
    private fun calculateSMBFromModel(): Float? {
        return try {
            val modelInputs: FloatArray

            when {
                modelHBFile.exists() -> {
                    // Cria o interpreter HB se necessário
                    if (modelInterpreter == null || !interpreterIsHB) {
                        modelInterpreter?.close()
                        modelInterpreter = Interpreter(modelHBFile)
                        interpreterIsHB = true
                    }
                    modelInputs = floatArrayOf(
                        hourOfDay.toFloat(), weekend.toFloat(),
                        bg.toFloat(), targetBg.toFloat(), iob.toFloat(), delta.toFloat(), shortAvgDelta.toFloat(), longAvgDelta.toFloat(),
                        tdd7DaysPerHour.toFloat(), tdd2DaysPerHour.toFloat(), tddPerHour.toFloat(), tdd24HrsPerHour.toFloat(), averageBeatsPerMinute.toFloat()
                    )
                }

                modelFile.exists()   -> {
                    if (modelInterpreter == null || interpreterIsHB) {
                        modelInterpreter?.close()
                        modelInterpreter = Interpreter(modelFile)
                        interpreterIsHB = false
                    }
                    modelInputs = floatArrayOf(
                        hourOfDay.toFloat(), weekend.toFloat(),
                        bg.toFloat(), targetBg.toFloat(), iob.toFloat(), delta.toFloat(), shortAvgDelta.toFloat(), longAvgDelta.toFloat(),
                        tdd7DaysPerHour.toFloat(), tdd2DaysPerHour.toFloat(), tddPerHour.toFloat(), tdd24HrsPerHour.toFloat()
                    )
                }

                else                 -> {
                    aapsLogger.error(LTag.APS, "NO Model found at specified location")
                    return null
                }
            }

            val interpreter = modelInterpreter ?: return null
            val output = arrayOf(floatArrayOf(0.0F))
            interpreter.run(modelInputs, output)
            var smbToGive = output[0][0].toString().replace(',', '.').toDouble()
            smbToGive = smbFormatter.format(smbToGive).toDouble()
            smbToGive.toFloat().coerceAtLeast(0f)  // defesa extra: nunca negativo como decisão
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "TFLite model error (create/run): ${e.message}", e)
            null
        }
    }


    // M5 (01/Ago/2026): calculateAdjustedDelayFactor + calculateInsulinEffect removidos.
    // Eram órfãs desde que predictFutureBg trocou para a fórmula atual
    // (bg+delta*1.5) - ((iob/2)*ISF). Nenhuma chamada ativa no arquivo ou no projeto.


    private fun predictFutureBg(
        bg: Float1,
        iob: Float1,  // Insuline active (IOB)
        variableSensitivity: Float1,  // Facteur de sensibilité à l'insuline (ISF)
        cob: Float1,  // Glucides à bord (COB)
        CI: Float1,  // Rapport insuline/glucides (ICR)
    ): Double {
        // Version 172
        var futureBg = (bg+(delta*1.5))-((iob/2)*variableSensitivity)

        // testes
        /*if (delta >= 3.5 || bg >= 135){
            futureBg = (bg+(delta*1.5))-((iob/1.8)*variableSensitivity)
            protection = "(ON)+"
        }*/



        // Version 170
        // if (LocalTime.now().run { hour in 6..22 } && bg <= 100 && delta > 0) {

        // Version 171
        //if (LocalTime.now().run { hour in 6..22 } && bg <= 107 && delta > 0) {



        // Version 172
        //if (LocalTime.now().run { hour in 6..22 } && bg <= 115 && delta > 0) {

        // Version 173
        // if (LocalTime.now().run { hour in 6..22 } && bg <= 125 && delta > 0) {

        // Version 208
        /*if (LocalTime.now().run { hour in 4..22 } && bg <= 155 && delta > 0) {
            futureBg = targetBg + 2

            }*/
        // Version 209 — v2: Adicionado guarda iob < 2.0 (Jul/2026)
        // Version 210 — v3: Teto IOB calibrado para 5.0U (Jul/2026)
        // Version 211 — v4 (Tarciso, Jul/2026): Removida trava de IOB do override.
        //   Trava de IOB causava tratamento tardio do hiper (cortava SMB com BG
        //   130-165 subindo quando IOB >= 2.0/5.0). A proteção de hipo continua
        //   nas demais condições de isCriticalSafetyCondition (BG real < 120).
        if (bg <= 220 && delta > 0) {
            futureBg = targetBg + 2
            }


        /*if (LocalTime.now().run { hour in 1..3 } && bg >= 135 && bg <= 160 && delta > 2 && iob >= 2) {
            futureBg = targetBg + 2

        }*/

        //if (LocalTime.now().run { hour in 6..22 } && bg > 100 && bg <= 140 && delta > 0) {
        //    futureBg = (bg+(delta*1.5))-((iob/(bg/75))*variableSensitivity)
        //}


        // S'assurer que la glycémie future n'est pas inférieure à une valeur minimale, par exemple 39

        if (futureBg < 59.0) {
            futureBg = 59.0
        }

        return futureBg.toDouble()
    }




// Tarciso DynamicAdjuts - Main code START
    private fun adjustFactorsBasedOnBgAndHypo(
        bg: Double,
        delta: Double
    ): Triple<Double, Double, Double> {

        bgImpact = BG_BASELINE - ((bg - BG_BASELINE)/BG_IMPACT_DIVISOR)

        fun calcAdjustment(maxFactor: Double): Double {
            var adjustment: Double
            if (sp.getBoolean(R.string.auto_mode, true)===true) {
                adjustment = ((0.5 * (bg /(hourlyfactor + variableSensitivity.coerceIn(ISF_CLAMP_MIN, ISF_CLAMP_MAX)+lowFactor) )) + (delta / DELTA_DIVISOR)) / (bgImpact.coerceIn(BG_IMPACT_CLAMP_MIN, BG_IMPACT_CLAMP_MAX)/100)
            } else {
                adjustment = ((0.5 * (bg /(hourlyfactor + variableSensitivity.coerceIn(ISF_CLAMP_MIN, ISF_CLAMP_MAX)+lowFactor) )) + (delta / DELTA_DIVISOR))
            }
            val maxBgAdjustment = hourlyfactor * maxFactor
            if (adjustment > maxBgAdjustment) adjustment = maxBgAdjustment
            if (adjustment < ADJUSTMENT_FLOOR) {
                adjustment = ADJUSTMENT_FLOOR
            } else if (targetBg >= TARGET_BG_CLAMP_LOW && targetBg < TARGET_BG_CLAMP_HIGH) {
                adjustment = ((0.35 * (bg /(hourlyfactor * 2) )) + (delta / DELTA_DIVISOR))
            } else if (targetBg >= TARGET_BG_CLAMP_HIGH) {
                adjustment = ADJUSTMENT_HIGH_TARGET
            }
            return adjustment
        }

        val bgAdjustmentM = calcAdjustment(2.5)
        val bgAdjustmentA = calcAdjustment(2.5)
        val bgAdjustmentE = calcAdjustment(2.0)

        return Triple(bgAdjustmentM, bgAdjustmentA, bgAdjustmentE)
    }



    // Tarciso new code for adjustFactorsdynisfBasedOnBgAndHypo

    private fun adjustFactorsdynisfBasedOnBgAndHypo(
        dynISFadjust: Float,

    ): Float {
        //var isfadjust = ((bg.toDouble() / 200) * (bg.toDouble() / 70)) + (delta.toDouble() / 3)
        // val maxdynISFadjust = dynISFadjust * 1.40

        //if (isfadjust > maxdynISFadjust.toDouble()) {
        //            isfadjust = maxdynISFadjust.toDouble()
        //        }
        //if (isfadjust < 0.2) {
        //          isfadjust = 0.2
        //        }





        // Versio 169
        // var isfadjust = ((bg.toDouble() / (bg * 2)) * (bg.toDouble() / ((dynISFadjust * 100)/1.34))) + (delta.toDouble() / 25)

        // Version 170
        // var isfadjust = ((bg.toDouble() / (bg * 2)) * (bg.toDouble() / ((dynISFadjust * 100)/1.34))) + (delta.toDouble() / 15)

        // Version 171
        //var isfadjust = (0.5 * Math.pow(bg.toDouble() / (((dynISFadjust * 100)+ variableSensitivity.coerceIn(50.0, 120.0))/1.34), 1.15)) + (delta.toDouble() / 15)




        var isfDynamicAdjust = 0.0
        var bgtriger = ((bg - 120.0).coerceIn(0.0,40.0))


        var lowLimit = 48.0
        lowLimit = when {
            bg >= 170 -> {
                35.0
            }
            bg >= 160 -> {
                39.0
            }
            bg >= 150 -> {
                41.0
            }
            bg >= 140 -> {
                43.0
            }
            bg >= 130 -> {
                45.0
            }
            bg >= 120 -> {
                47.0
            }
            else -> lowLimit
        }

        bgImpact = BG_BASELINE -((bg - BG_BASELINE)/BG_IMPACT_DIVISOR)
        if(sp.getBoolean(R.string.auto_mode, true)===true){

            // isfadjust1 = (variableSensitivity.coerceIn(75.0, 250.0) * ((bg + maxOf(0.0, (140.0 - bg) / 3.0)) /100)) + lowFactor
            // isfadjust1 = ((variableSensitivity.coerceIn(lowLimit, 250.0) * ((bg + (((lowLimit-10)/2)+(lowLimit-80)) ) /100))) + lowFactor


            // isfadjust1 = bgImpact.coerceIn(73.0, 250.0) + lowLimit + lowFactor
            // isfadjust1 = bgImpact.coerceIn(63.0, 250.0) + lowLimit + lowFactor
            // isfadjust1 = bgImpact.coerceIn(63.0, 250.0) + (bg/iob+10) + lowFactor
            isfDynamicAdjust = (dynISFadjust * bgImpact.coerceIn(ISF_DYN_CLAMP_MIN, ISF_DYN_CLAMP_MAX)) + variableSensitivity.coerceIn(SENSITIVITY_ISF_CLAMP_MIN, SENSITIVITY_ISF_CLAMP_MAX) + lowFactor
        } else {
            // Ajuste baseado na formula original usando IFS
            isfDynamicAdjust = (dynISFadjust * 100) + variableSensitivity.coerceIn(SENSITIVITY_ISF_CLAMP_MIN, SENSITIVITY_ISF_CLAMP_MAX) + lowFactor

            // Ajuste baseado na formula do auto mode
            //isfadjust1 = (dynISFadjust * 100) + (bgImpact.coerceIn(63.0, 250.0)-(lowLimit/3)) + lowFactor
        }


        //var isfadjust0 = (dynISFadjust * 100) + variableSensitivity.coerceIn(65.0, 200.0) + lowFactor
        //var isfadjust1 = (variableSensitivity.coerceIn(75.0, 250.0) *((bg+40)/100)) + lowFactor
        // var isfadjust = (0.5 * Math.pow(bg.toDouble() / (((dynISFadjust * 100) + variableSensitivity.coerceIn(65.0, 200.0))/1.34), 1.15)) + (delta.toDouble() / 15)
        var isfadjust = (0.5 * Math.pow(bg.toDouble() / (isfDynamicAdjust /1.34), 1.15)) + (delta.toDouble() / 15)

        val maxdynISFadjust = isfadjust.coerceIn(60.0, 300.0)

        if (isfadjust > maxdynISFadjust.toDouble()) {
            isfadjust = maxdynISFadjust.toDouble()
        } else if (isfadjust < 0.2) {
            isfadjust = 0.2
        } else if (targetBg >= TARGET_BG_CLAMP_LOW && targetBg < TARGET_BG_CLAMP_HIGH) {
            //isfadjust = dynISFadjust.toDouble()
            isfadjust = (0.35 * Math.pow(bg.toDouble() / (isfDynamicAdjust /1.34), 1.15)) + (delta.toDouble() / 15)
        } else if (targetBg >= TARGET_BG_CLAMP_HIGH) {
            //isfadjust = dynISFadjust.toDouble()
            isfadjust = 0.40
        }

        return isfadjust.toFloat()
    }
    // Tarciso DynamicAdjuts - Main code END




    @Suppress("SpellCheckingInspection")
    @Throws(JSONException::class)
    override fun setData(
        profile: Profile,
        maxIob: Double,
        maxBasal: Double,
        minBg: Double,
        maxBg: Double,
        targetBg: Double,
        basalRate: Double,
        iobArray: Array<IobTotal>,
        glucoseStatus: GlucoseStatus,
        mealData: MealData,
        autosensDataRatio: Double,
        tempTargetSet: Boolean,
        microBolusAllowed: Boolean,
        uamAllowed: Boolean,
        advancedFiltering: Boolean,
        flatBGsDetected: Boolean,
        tdd1D: Double?,
        tdd7D: Double?,
        tddLast24H: Double?,
        tddLast4H: Double?,
        tddLast8to4H: Double?
    ) {
        this.now = System.currentTimeMillis()
        val calendarInstance = Calendar.getInstance()
        this.hourOfDay = calendarInstance[Calendar.HOUR_OF_DAY]
        val dayOfWeek = calendarInstance[Calendar.DAY_OF_WEEK]
        this.weekend = if (dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY) 1 else 0

        // ─── Tarciso_REMOVER_ISF (09/Ago/2026) — hourlyfactor lido ANTES de tudo ───
        // Movido do bloco original (L~1491) para o INÍCIO do setData, para que
        // TODAS as escritas de variableSensitivity (incl. PK/PD L315, Delta Lock
        // L1690, fallbacks) tenham o HF disponível. Elimina dependência da profile.
        hourlyfactor = when {
            hourOfDay == 0 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_00, "50"))
            hourOfDay == 1 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_01, "50"))
            hourOfDay == 2 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_02, "50"))
            hourOfDay == 3 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_03, "50"))
            hourOfDay == 4 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_04, "50"))
            hourOfDay == 5 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_05, "50"))
            hourOfDay == 6 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_06, "50"))
            hourOfDay == 7 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_07, "50"))
            hourOfDay == 8 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_08, "50"))
            hourOfDay == 9 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_09, "50"))
            hourOfDay == 10 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_10, "50"))
            hourOfDay == 11 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_11, "50"))
            hourOfDay == 12 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_12, "50"))
            hourOfDay == 13 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_13, "50"))
            hourOfDay == 14 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_14, "50"))
            hourOfDay == 15 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_15, "50"))
            hourOfDay == 16 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_16, "50"))
            hourOfDay == 17 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_17, "50"))
            hourOfDay == 18 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_18, "50"))
            hourOfDay == 19 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_19, "50"))
            hourOfDay == 20 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_20, "50"))
            hourOfDay == 21 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_21, "50"))
            hourOfDay == 22 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_22, "50"))
            hourOfDay == 23 -> SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hourly_percentage_23, "50"))
            // M4 (01/Ago/2026): fallback consistente com os branches (50.0, não 50.0/100=0.5).
            // Se hourOfDay sair do range 0-23 (bug de relógio/fuso), o fator horário
            // permanece 50.0 — evita SMB até 2.5× maior que o modelo pediu.
            else -> 90.0
        }

        // Carregar modelo ML SMB uma vez
        if (!smbModelLoaded && sp.getBoolean(R.string.key_aimi_smb_refine, false)) {
            AimiSmbTrainer.loadModel(File(path, "AAPS/ml"))
            smbModelLoaded = true
        }

        // ─── Tarciso_BG_CONFIDENCE (08/Ago/2026) — OPÇÃO A (19/Ago/2026) ───
        // Rede neural DESATIVADA: rawTier=0 fixo, mantendo apenas as
        // TRAVAS 1-9 determinísticas do BgConfidenceGuard.
        // Motivo: analise_2026-08-19_rede_neural_avaliacao_6_propostas.md
        // (modelo treinado com 48 amostras inválidas; falso-positivos BAD em
        // BG estável cortavam SMB em 50% em ~35% dos ciclos).
        try {
            // Idade do sensor (TRAVA 9 — substitui a feature faseSensor da NN)
            val idadeSensor = getSensorAgeMinutes()

            // Cálculo do delta da leitura ANTERIOR (feature da TRAVA 8)
            val historicoRecente = repository.compatGetBgReadingsDataFromTime(now - 15 * 60 * 1000L, now, false)
                .timeout(2, TimeUnit.SECONDS)
                .onErrorReturnItem(emptyList())
                .blockingGet()
                .filter { it.isValid }
                .sortedBy { it.timestamp }

            val bgAnt1 = if (historicoRecente.size >= 2) historicoRecente[historicoRecente.size - 2].value else glucoseStatus.glucose
            val bgAnt2 = if (historicoRecente.size >= 3) historicoRecente[historicoRecente.size - 3].value else bgAnt1
            val prevDeltaCalc = bgAnt1 - bgAnt2          // delta da leitura ANTERIOR (para TRAVA 8)

            // OPÇÃO A (19/Ago/2026): rede neural desativada — rawTier=0 fixo.
            // TRAVAS 1-9 continuam aplicadas por regra (hipo/hiper/ruído/rebote/idade).
            bgConfidence = BgConfidenceGuard.applySafety(
                rawTier = 0,
                bg = glucoseStatus.glucose,
                delta = glucoseStatus.delta,
                prevDelta = prevDeltaCalc,
                shortAvgDelta = glucoseStatus.shortAvgDelta,
                deltaSuspeito = sp.getDouble(R.string.key_aimi_delta_suspeito, BgConfidenceGuard.DEFAULT_DELTA_SUSPEITO),
                idadeSensorMin = if (idadeSensor == Long.MAX_VALUE) 0L else idadeSensor
            )
        } catch (e: Exception) {
            bgConfidence = 0
            aapsLogger.warn(LTag.APS, "BG confidence travas falharam (fallback OK): ${e.message}")
        }

        // Item 7 - Opção A-v2: BG Corrigido para ser utilizado nos cálculos de dose
        val bgDeTrabalho = BgConfidenceGuard.calculateCorrectedBg(
            bg = glucoseStatus.glucose,
            delta = glucoseStatus.delta,
            shortAvgDelta = glucoseStatus.shortAvgDelta,
            tier = bgConfidence
        )

        // ─── Tarciso_BG_CONFIDENCE_V2 (09/Ago/2026) — captura automática de suspeitas ───
        // Usa as leituras anteriores/futuras do histórico para detectar reversões de salto,
        // xDrip error (39) e sensor novo oscilando. Acelera o aprendizado sem ponta de dedo.
        try {
            val historico = repository.compatGetBgReadingsDataFromTime(
                now - 15 * 60 * 1000L, now + 5 * 60 * 1000L, false)
                .timeout(3, TimeUnit.SECONDS)
                .onErrorReturnItem(emptyList())
                .blockingGet()
                .filter { it.isValid }
                .sortedBy { it.timestamp }

            if (historico.size >= 3) {
                val bgAnt = historico[historico.size - 3].value
                val bgAtual = historico[historico.size - 2].value
                val bgFut = historico.last().value
                val idadeSensorCap = getSensorAgeMinutes()
                val faseSensorCap = when {
                    idadeSensorCap == Long.MAX_VALUE -> 0.0
                    idadeSensorCap < 24 * 60 -> 0.0
                    idadeSensorCap < 12 * 24 * 60 -> 1.0
                    else -> 2.0
                }
                AimiBgConfidenceTrainer.capturarSuspensaoAutomatica(
                    csvFile = File(path, "AAPS/ml/bg_confidence_training.csv"),
                    bgAnterior = bgAnt, bgAtual = bgAtual, bgFutura = bgFut,
                    delta = delta,
                    idadeSensorMin = if (idadeSensorCap == Long.MAX_VALUE) 0L else idadeSensorCap,
                    iob = this.iob.toDouble(),
                    cob = mealData.mealCOB.toDouble(),
                    tdd7DaysPerHour = tdd7DaysPerHour.toDouble(),
                    isNight = if (LocalTime.now().run { hour in 22..23 || hour in 0..5 }) 1.0 else 0.0,
                    faseSensor = faseSensorCap,
                    emJanelaRefeicao = emJanelaRefeicao()
                )
            }
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "Captura automática BG confidence falhou: ${e.message}")
        }

        // Guard: variableSensitivity ainda nao foi calculada? Usa hourlyfactor como fallback.
        // Tarciso_REMOVER_ISF (09/Ago/2026): HF auto-ajustado em vez de profile.
        if (this.variableSensitivity <= 0.0) {
            this.variableSensitivity = hourlyfactor.coerceAtLeast(1.0)
        }

        val iobCalcs = iobCobCalculator.calculateIobFromBolus()

        this.iob = iobCalcs.iob + iobCalcs.basaliob.toFloat()
        this.iobArray = iobArray.toList()  // Bug #4 fix (Jul/2026) — popula array para Fase 1 PK/PD
        // this.iob = iobCalcs.iob.toFloat() + iobCalcs.basaliob.toFloat()
        this.bg = bgDeTrabalho  // Item 7: Usa BG corrigido (não BG cru) para cálculos subsequentes
        this.bgTime = glucoseStatus.date
        calcularBgAltoUltimas4h()
        calcularLowestBgUltimaHora()
        this.targetBg = targetBg
        this.cob = mealData.mealCOB
        var lastCarbTimestamp = mealData.lastCarbTime

        if(lastCarbTimestamp.toInt() == 0) {
            val oneDayAgoIfNotFound = now - 24 * 60 * 60 * 1000
            lastCarbTimestamp = iobCobCalculator.getMostRecentCarbByDate() ?: oneDayAgoIfNotFound
        }
        this.lastCarbAgeMin = ((now - lastCarbTimestamp) / (60 * 1000)).toDouble().roundToInt()

        if(lastCarbAgeMin < 15 && cob == 0.0) {
            this.cob = iobCobCalculator.getMostRecentCarbAmount()?: 0.0
        }

        this.futureCarbs = iobCobCalculator.getFutureCob()
        val fourHoursAgo = now - 4 * 60 * 60 * 1000
        this.recentNotes = iobCobCalculator.getUserEntryDataWithNotesFromTime(fourHoursAgo)

        this.tags0to60minAgo = parseNotes(0, 60)
        this.tags60to120minAgo = parseNotes(60, 120)
        this.tags120to180minAgo = parseNotes(120, 180)
        this.tags180to240minAgo = parseNotes(180, 240)
        this.delta = glucoseStatus.delta
        this.shortAvgDelta = glucoseStatus.shortAvgDelta
        this.longAvgDelta = glucoseStatus.longAvgDelta
        var nowMinutes = calendarInstance[Calendar.HOUR_OF_DAY] + calendarInstance[Calendar.MINUTE] / 60.0 + calendarInstance[Calendar.SECOND] / 3600.0

        // M2 (01/Ago/2026): coletar alarmes ANTES do targetBg — elimina atraso de 1 ciclo
        // (determineTargetBgInSetData lê alarmesLowGlucoseUltimaHora/24Horas em 2067-2072)
        collectLowGlucoseAlarms()

        // TARGET BG DEFINITION
        determineTargetBgInSetData()




        this.accelerating_up = if (delta > 2 && delta - longAvgDelta > 2) 1 else 0
        this.deccelerating_up = if (delta > 0 && (delta < shortAvgDelta || delta < longAvgDelta)) 1 else 0
        this.accelerating_down = if (delta < -2 && delta - longAvgDelta < -2) 1 else 0
        this.deccelerating_down = if (delta < 0 && (delta > shortAvgDelta || delta > longAvgDelta)) 1 else 0
        this.stable = if (delta>-3 && delta<3 && shortAvgDelta>-3 && shortAvgDelta<3 && longAvgDelta>-3 && longAvgDelta<3) 1 else 0
        this.stable2 = if (delta>-1.5 && delta<1.5 && shortAvgDelta>-1.0 && shortAvgDelta<1.0 && longAvgDelta>-0.8 && longAvgDelta<0.8) 1 else 0
        bgstatus = when {
            accelerating_up == 1  -> 2
            deccelerating_up == 1 -> 1
            stable == 1 -> 0
            deccelerating_down ==1 -> -1
            accelerating_down == 1 -> -2
            else -> 0
        }




















        loadStepsAndHeartRate()

        // TDD calculations
        var tdd = calculateTDDMetrics()

        // Insulin divisor dinâmico (DIA adaptativo)
        // Base: derivado do tempo de pico da insulina (fixo por tipo)
        // Ajustes dinâmicos: hora do dia, IOB, atividade física (Bio-Sync)
        // v2 (Tarciso, 01/Ago/2026): REVERTIDA ordem das condições — a inversão
        // feita em 30/Jul mudou o divisor do Humalog de 55 para 65 (ISF maior,
        // SMB menor). Comportamento original restaurado: Humalog/Novolog/Apidra
        // (peak >= 35) usam divisor 55, como no código do celular/NS.
        val insulin = activePlugin.activeInsulin
        val baseDivisor = when {
            insulin.peak >= 35 -> 55  // Rápido (Humalog, Novolog, Apidra, Fiasp)
            insulin.peak > 45  -> 65  // Lento (preservado — compatibilidade)
            else               -> 75  // Ultra-rápido (Lyumjev)
        }

        // Camada 1 — Ajuste por hora do dia (ritmo circadiano)
        val hour = LocalTime.now().hour
        val timeAdjustment = when (hour) {
            in 6..10  -> 0.90  // Manhã: maior sensibilidade, DIA reduzido
            in 22..23, in 0..5 -> 1.10  // Noite: resistência natural, DIA estendido
            else      -> 1.0
        }

        // Camada 2 — Ajuste por IOB (insulina ativa)
        val iobAdjustment = if (iob > 2.0) {
            val excess = minOf((iob - 2.0), 5.0).coerceAtLeast(0.0)
            1.0 + 0.03 * excess  // +3%/U acima do threshold (máx +15%)
        } else 1.0

        // Camada 3 — Ajuste por atividade física (Bio-Sync adaptado)
        val activityAdjustment = when {
            // Exercício intenso: passos + FC elevada
            recentSteps5Minutes > 200 && averageBeatsPerMinute > averageBeatsPerMinute180 && bg < 130 -> 0.85
            // Atividade moderada sustentada
            recentSteps30Minutes > 500 && bg < 130 -> 0.90
            // Estresse em repouso (FC alta, sem atividade, BG elevado)
            averageBeatsPerMinute > 95 && recentSteps5Minutes < 100 && bg > 140 -> 1.15
            // Repouso profundo (FC baixa, sem passos)
            recentSteps5Minutes == 0 && averageBeatsPerMinute < 65 && averageBeatsPerMinute > 40 -> 1.05
            else -> 1.0
        }

        val insulinDivisor = (baseDivisor * timeAdjustment * iobAdjustment * activityAdjustment)
            .roundToInt()
            .coerceIn(35, 95)  // segurança: entre ~35 (ultra-rápido) e 95 (muito lento)

        val dynISFadjust = SafeParse.stringToDouble(sp.getString(R.string.key_DynISFAdjust, "120")) / 100.0
        val dynISFadjusthyper = SafeParse.stringToDouble(sp.getString(R.string.key_DynISFAdjusthyper, "150")) / 100.0
        adjustDynIsf = adjustFactorsdynisfBasedOnBgAndHypo(dynISFadjust.toFloat()).toDouble()

        tdd = if (bg > 180) tdd * dynISFadjusthyper else tdd * adjustDynIsf
        if (tdd.isInfinite()) tdd = SafeParse.stringToDouble(sp.getString(R.string.key_tdd7, "35"))

        // Basalaimi + CI + BasalSMB + bolus times
        val tdd7P = SafeParse.stringToDouble(sp.getString(R.string.key_tdd7, "35"))
        var tdd2Days = tddCalculator.averageTDD(tddCalculator.calculate(2, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        if (tdd2Days == 0.0f || tdd2Days < (tdd7P / 1.82)) tdd2Days = (tdd7P.toFloat() / 1.82f)
        this.basalaimi = (tdd2Days / SafeParse.stringToDouble(sp.getString(R.string.key_aimiweight, "50")))
        this.CI = if (tdd2Days != 0.0f) (450 / tdd2Days).toDouble() else 450.0 / tdd7P

        val choKey = SafeParse.stringToDouble(sp.getString(R.string.key_cho, "50"))
        this.aimilimit = if (CI != 0.0 && CI.isFinite()) (choKey / CI) else (choKey / profile.getIc())

        if (averageBeatsPerMinute != 0.0) {
            this.basalaimi = when {
                averageBeatsPerMinute >= averageBeatsPerMinute180 && recentSteps5Minutes > 100 && recentSteps10Minutes > 200 -> (basalaimi * 0.65)
                averageBeatsPerMinute180 != 80.0 && averageBeatsPerMinute > averageBeatsPerMinute180 && bg >= 130 && recentSteps10Minutes == 0 && LocalTime.now() > LocalTime.of(6, 0) -> (basalaimi * 1.3)
                averageBeatsPerMinute180 != 80.0 && averageBeatsPerMinute < averageBeatsPerMinute180 && recentSteps10Minutes == 0 && bg >= 110 -> (basalaimi * 1.2)
                else -> basalaimi
            }
        }

        this.b30upperbg = SafeParse.stringToDouble(sp.getString(R.string.key_B30_upperBG, "130"))
        this.b30upperdelta = SafeParse.stringToDouble(sp.getString(R.string.key_B30_upperdelta, "10"))
        val b30duration = SafeParse.stringToDouble(sp.getString(R.string.key_B30_duration, "20"))
        this.basalSMB = (((basalaimi * delta) / 60) * b30duration)
        this.basaloapsaimirate = when {
            delta < b30upperdelta && delta > 1 && bg < b30upperbg && lastsmbtime > 20 -> basalSMB.toDouble()
            predictedBg > targetBg && bg > targetBg && lastsmbtime > 20 -> basalSMB.toDouble()
            else -> 0.0
        }

        // N2 (03/Ago/2026): timeout(3s) + fallback Absent — mesmo padrão das outras
        // queries do arquivo. Impede que o ciclo APS congele indefinidamente se o
        // banco Room estiver sob lock (sync NS em massa, backup). Antes: blockingGet()
        // sem timeout podia travar o thread do ciclo. Comportamento normal inalterado.
        val getlastBolusSMB = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.SMB)
            .timeout(3, TimeUnit.SECONDS)
            .onErrorReturnItem(ValueWrapper.Absent())
            .blockingGet()
        val lastBolusSMBTime = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.timestamp else 0L
        this.lastsmbtime = ((now - lastBolusSMBTime) / (60 * 1000)).toDouble().roundToInt().toLong().toInt()
        val getlastBolusMEAL = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL)
            .timeout(3, TimeUnit.SECONDS)
            .onErrorReturnItem(ValueWrapper.Absent())
            .blockingGet()
        val lastBolusMEALTime = if (getlastBolusMEAL is ValueWrapper.Existing) getlastBolusMEAL.value.timestamp else 0L
        this.lastmealtime = ((now - lastBolusMEALTime) / (60 * 1000)).toDouble().roundToInt().toLong().toInt()

        // MaxIOB + MaxSMB definition
        determineMaxIOBandMaxSMB(profile)

     

        // profile.dia
        val abs = iobCobCalculator.calculateAbsoluteIobFromBaseBasals(System.currentTimeMillis())
        val absIob = abs.iob
        val absNet = abs.netInsulin
        val absBasal = abs.basaliob

        aapsLogger.debug(LTag.APS, "IOB options : bolus iob: ${iobCalcs.iob} basal iob : ${iobCalcs.basaliob}")
        aapsLogger.debug(LTag.APS, "IOB options : calculateAbsoluteIobFromBaseBasals iob: $absIob net : $absNet basal : $absBasal")
        val tddDouble = tdd.toDoubleSafely()
        val glucoseDouble = glucoseStatus.glucose?.toDoubleSafely()
        val insulinDivisorDouble = insulinDivisor?.toDoubleSafely()


        if (tddDouble != null && glucoseDouble != null && insulinDivisorDouble != null) {
            variableSensitivity = (1800 / (tdd * (ln((glucoseStatus.glucose / insulinDivisor) + 1))))
            // variableSensitivity = (1800 / ((tdd) * (ln((glucoseStatus.glucose / insulinDivisor) + 1)))) original

            // Ajout d'un log pour vérifier la valeur de variableSensitivity après le calcul
            val variableSensitivityDouble = variableSensitivity.toDoubleSafely()
            if (variableSensitivityDouble != null) {
            if (recentSteps5Minutes > 100 && recentSteps10Minutes > 200 && bg < 130 && delta < 10|| recentSteps180Minutes > 1500 && bg < 130 && delta < 10) variableSensitivity *= 1.5f
            if (recentSteps30Minutes > 500 && recentSteps5Minutes >= 0 && recentSteps5Minutes < 100 && bg < 130 && delta < 10) variableSensitivity *= 1.3f
            }
             } else {
            // Tarciso_REMOVER_ISF (09/Ago/2026): fallback = hourlyfactor, nunca profile.
            variableSensitivity = hourlyfactor.coerceAtLeast(1.0)
             }




        // ********************************************************************
        // ************           IMPORTANT - WARNING            **************
        // ********************************************************************
        // DELTA LOCK

        // Tarciso Add change to variableSensitivity. This is the next line. Id delta < -1 & bg < 160 then variableSensitivity = (Profile.ISF * TDD)
        // if (delta <0) variableSensitivity = (1800 / ((tdd.toDouble()/8) * (ln((glucoseStatus.glucose / insulinDivisor) + 1)))).toFloat()


        // This is a important safety lock. If delta is < than this condition no SMB will be delivered.
        // if (delta < 0) variableSensitivity = profile.getIsfMgdl() * (tdd.toFloat()/6)


        // Tarciso_REMOVER_ISF (09/Ago/2026): Delta Lock usa hourlyfactor (auto-ajustado) × (tdd/6),
        // nunca profile. Mais protetor que antes (HF~49-94 vs ISF equalizado = mesmo valor).
        if (delta <= 0.0 && bg < 160 ) variableSensitivity = hourlyfactor.coerceAtLeast(1.0) * (tdd.toFloat()/6)

        // END OF DELTA LOCK






            this.predictedBg = predictFutureBg(bg.toFloat(), iob.toFloat(), variableSensitivity.toFloat(), cob.toFloat(), CI.toFloat())
        // Tarciso Next line added to test Profile ISF — Tarciso_REMOVER_ISF (09/Ago/2026): usa HF.
        ProfileISF = hourlyfactor

        this.profile = JSONObject()
        // this.profile.put("max_iob", maxIob)
        // Tarciso
        // this.profile.put("dia", kotlin.math.min(profile.dia, 3.0))
        // this.profile.put("type", "current")
        // this.profile.put("max_daily_basal", profile.getMaxDailyBasal())
        // this.profile.put("max_basal", maxBasal)
        // this.profile.put("min_bg", minBg)
        // this.profile.put("max_bg", maxBg)
        // this.profile.put("target_bg", targetBg)
        // this.profile.put("futureBg", predictedBg)
        // this.profile.put("testes_para_maxIOB", profile.getIc())

       // this.profile.put("note_change", isNoteEvent())


        // this.profile.put("carb_ratio", CI)

        // this.profile.put("max_daily_safety_multiplier", sp.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3))
        // this.profile.put("current_basal_safety_multiplier", sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0))
        // this.profile.put("skip_neutral_temps", true)
        // this.profile.put("current_basal", basalRate)
        // this.profile.put("temptargetSet", tempTargetSet)
        // this.profile.put("autosens_adjust_targets", sp.getBoolean(R.string.key_openapsama_autosens_adjusttargets, true))

        // Tarciso Next line added to test Profile ISF
        // this.profile.put("profile_ISF", ProfileISF)
        // this.profile.put("adjustable_ISF", variableSensitivity.roundToInt())

        //this.profile.put("tddWeightedFromLast8H", tddWeightedFromLast8H)
        //this.profile.put("tdd",  tdd)
        // this.profile.put("delta", delta)
        // this.profile.put("dyn ISF adjust", (adjustDynIsf * 100).roundToInt())






       SensorChange = isSensorChangeEvent()
        if (SensorChange?.any { it.type == TherapyEvent.Type.SENSOR_CHANGE } == true) {
            this.profile.put("sensor_change", "New sensor, MaxIob, MaxSMB reduced by 25%. Add new note with SENSOR_OK to restore values")
        }

        TherapyNote = isNoteEvent()
        if (TherapyNote?.any { it.note?.contains("SENSOR_OK", ignoreCase = true) == true } == true) {
            this.profile.put("sensor_change", "Sensor OK, MaxIob and MaxSMB restored to preferences value.")
        }


        BGfinger = isBGfingerEvent()
        if (BGfinger?.isNotEmpty() == true) {
            this.profile.put("BGfinger", "$BGfinger")

            // ─── Tarciso_BG_CONFIDENCE (08/Ago/2026) — coletar amostra de treino ───
            // Quando o usuário informa uma ponta de dedo (FINGER_STICK_BG_VALUE),
            // registra uma amostra para a rede neural de confiança do BG.
            // Gabarito = divergência entre o valor da ponta de dedo e o sensor.
            // Usa o valor de glucose do TherapyEvent (true ground truth).
            try {
                val fingerValue = BGfinger?.firstOrNull()?.glucose
                if (fingerValue != null && fingerValue > 0.0) {
                    val divergencia = abs(bg - fingerValue)
                    val idadeSensor = getSensorAgeMinutes()
                    val faseSensor = when {
                        idadeSensor == Long.MAX_VALUE -> 0.0
                        idadeSensor < 24 * 60 -> 0.0
                        idadeSensor < 12 * 24 * 60 -> 1.0
                        else -> 2.0
                    }
                    // BUG-12: mesmas features reais do classify (histórico recente) em vez de 0.0/0.0
                    val historicoRecentef = repository.compatGetBgReadingsDataFromTime(now - 15 * 60 * 1000L, now, false)
                        .timeout(2, TimeUnit.SECONDS)
                        .onErrorReturnItem(emptyList())
                        .blockingGet()
                        .filter { it.isValid }
                        .sortedBy { it.timestamp }
                    val bgAnt1f = if (historicoRecentef.size >= 2) historicoRecentef[historicoRecentef.size - 2].value else glucoseStatus.glucose
                    val bgAnt2f = if (historicoRecentef.size >= 3) historicoRecentef[historicoRecentef.size - 3].value else bgAnt1f
                    val saltoAnteriorReal = (glucoseStatus.glucose - bgAnt1f).coerceAtLeast(0.0)
                    val reversaoReal = if ((bgAnt1f - bgAnt2f > 15.0 && glucoseStatus.glucose < bgAnt1f - 15.0) ||
                        (bgAnt2f - bgAnt1f > 15.0 && glucoseStatus.glucose > bgAnt1f + 15.0)) 1.0 else 0.0

                    AimiBgConfidenceTrainer.recordSample(
                        csvFile = File(path, "AAPS/ml/bg_confidence_training.csv"),
                        bg = glucoseStatus.glucose,
                        delta = glucoseStatus.delta,
                        shortAvgDelta = glucoseStatus.shortAvgDelta,
                        longAvgDelta = glucoseStatus.longAvgDelta,
                        saltoAnterior = saltoAnteriorReal,
                        reversao = reversaoReal,
                        idadeSensorMin = if (idadeSensor == Long.MAX_VALUE) 0L else idadeSensor,
                        compressaoAtiva = 0.0,  // Fase 2: integrar CompressionDetector aqui
                        iob = this.iob.toDouble(),
                        cob = mealData.mealCOB.toDouble(),
                        tdd7DaysPerHour = tdd7DaysPerHour.toDouble(),
                        isNight = if (LocalTime.now().run { hour in 22..23 || hour in 0..5 }) 1.0 else 0.0,
                        faseSensor = faseSensor,
                        direcaoDivergencia = fingerValue - bg,  // dedo − sensor (com sinal): lag de subida = positivo
                        emJanelaRefeicao = emJanelaRefeicao(),
                        divergenciaDedo = divergencia
                    )
                    aapsLogger.debug(LTag.APS, "🩸 BG confidence sample coletada: sensor=$bg, dedo=$fingerValue, divergência=${"%.1f".format(divergencia)}")
                }
            } catch (e: Exception) {
                aapsLogger.warn(LTag.APS, "BG confidence recordSample failed: ${e.message}")
            }
        }



        if (profileFunction.getUnits() == GlucoseUnit.MMOL) {
            this.profile.put("out_units", "mmol/L")
        }

        val tb = iobCobCalculator.getTempBasalIncludingConvertedExtended(now)
        currentTemp = JSONObject()
        currentTemp.put("temp", "absolute")
        currentTemp.put("duration", tb?.plannedRemainingMinutes ?: 0)
        currentTemp.put("rate", tb?.convertedToAbsolute(now, profile) ?: 0.0)
        // as we have non default temps longer than 30 minutes
        if (tb != null) currentTemp.put("minutesrunning", tb.getPassedDurationToTimeInMinutes(now))

        iobData = iobCobCalculator.convertToJSONArray(iobArray)
        this.glucoseStatus = JSONObject()
        this.glucoseStatus.put("glucose", glucoseStatus.glucose)
        if (sp.getBoolean(R.string.key_always_use_shortavg, false)) {
            this.glucoseStatus.put("delta", glucoseStatus.shortAvgDelta)
        } else {
            this.glucoseStatus.put("delta", glucoseStatus.delta)
        }
        this.glucoseStatus.put("short_avgdelta", glucoseStatus.shortAvgDelta)
        this.glucoseStatus.put("long_avgdelta", glucoseStatus.longAvgDelta)
        this.mealData = JSONObject()
        // this.mealData.put("carbs", mealData.carbs)
        // this.mealData.put("mealCOB", mealData.mealCOB)
        if (constraintChecker.isAutosensModeEnabled().value()) {
            autosensData.put("ratio", autosensDataRatio)
        } else {
            autosensData.put("ratio", 1.0)
        }
    }



    private fun determineNoteBasedOnBg(bg: Double): String {
        return when {
            bg > 140 -> "more aggressive"
            bg in 85.0..90.0 -> "less aggressive"
            bg in 80.0..84.9 -> "too aggressive" // Vous pouvez ajuster ces valeurs selon votre logique
            bg < 80 -> "low treatment"
            else -> "normal" // Vous pouvez définir un autre message par défaut pour les cas non couverts
        }
    }

    private fun Number.toDoubleSafely(): Double? {
        val doubleValue = this.toDouble()
        return doubleValue.takeIf { !it.isNaN() && !it.isInfinite() }
    }
    private fun parseNotes(startMinAgo: Int, endMinAgo: Int): String {
        val olderTimeStamp = now - endMinAgo * 60 * 1000
        val moreRecentTimeStamp = now - startMinAgo * 60 * 1000
        var notes = ""
        val recentNotes2: MutableList<String> = mutableListOf()

        val autoNote = determineNoteBasedOnBg(bg.toDouble())
        recentNotes2.add(autoNote)

        recentNotes?.forEach { note ->
            if(note.timestamp > olderTimeStamp
                && note.timestamp <= moreRecentTimeStamp
                && !note.note.lowercase().contains("low treatment")
                && !note.note.lowercase().contains("less aggressive")
                && !note.note.lowercase().contains("more aggressive")
                && !note.note.lowercase().contains("too aggressive")
            ) {
                notes += if(notes.isEmpty()) recentNotes2 else " "
                notes += note.note
            }
        }
        notes = notes.lowercase()
        notes = notes.replace(",", " ")
        notes = notes.replace(".", " ")
        notes = notes.replace("!", " ")
        notes = notes.replace("a", " ")
        notes = notes.replace("an", " ")
        notes = notes.replace("and", " ")
        notes = notes.replace("\\s+", " ")
        return notes
    }

    // ===== MÉTODOS AUXILIARES EXTRAÍDOS DE invoke() =====

    /** Busca estatísticas de alarmes de low glucose */
    private fun collectLowGlucoseAlarms() {
        try {
            alarmesLowGlucoseUltimaHora = localAlertUtils.getAlarmesUltimaHora()
            alarmesLowGlucose24Horas = localAlertUtils.getAlarmesNoPeriodo(T.hours(24).msecs())
            totalAlarmesLowGlucose = localAlertUtils.getTotalAlarmes()
            aapsLogger.debug(LTag.APS, "📊 Alarmes Low Glucose - Última hora: $alarmesLowGlucoseUltimaHora, 24h: $alarmesLowGlucose24Horas, Total: $totalAlarmesLowGlucose")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Erro ao obter estatísticas de alarmes", e)
        }
    }

    /**
     * PROPOSTA 05/Ago (Opção A) — Lockout de SMB pós-alarme low glucose.
     * Ativo quando o último alarme low glucose foi há < 30 min E o BG ainda está < 110.
     * Saída: o que vier primeiro — 30 min desde o alarme OU BG ≥ 110.
     * Ativo 24h. Usado por isCriticalSafetyCondition (corta SMB).
     */
    private fun alarmeLowRecente(): Boolean {
        return try {
            val timestampsJson = sp.getString(KEY_LOW_GLUCOSE_ALARM_TIMESTAMPS, "[]")
            val jsonArray = org.json.JSONArray(timestampsJson)
            if (jsonArray.length() > 0) {
                val ultimoAlarme = jsonArray.getLong(jsonArray.length() - 1)
                val minutosDesdeAlarme = (now - ultimoAlarme) / (60 * 1000)
                val menosDe30Min = minutosDesdeAlarme < 30
                val bgAindaBaixo = bg < 110
                menosDe30Min && bgAindaBaixo
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * PROPOSTA 05/Ago (Opção A, revisada v3) — Target 140 após alarme low glucose NOTURNO.
     * Ativado quando o alarme low dispara DURANTE A NOITE (padrão isNight do arquivo: 22-5).
     * DESLIGA quando QUALQUER das condições ocorrer (o que vier primeiro):
     *   1) fim da janela noturna (isNight false — hora >= 6 ou hora < 22)
     *   2) BG > 140 (já recuperou — não precisa mais de proteção)
     * Também desliga se o alarme foi há mais de 2h (proteção contra alarme antigo).
     * Alinhado com o padrão isNight = hour in 22..23 || hour in 0..5 do resto do arquivo.
     */
    private fun alarmeLowRecenteParaTarget(): Boolean {
        return try {
            val timestampsJson = sp.getString(KEY_LOW_GLUCOSE_ALARM_TIMESTAMPS, "[]")
            val jsonArray = org.json.JSONArray(timestampsJson)
            if (jsonArray.length() > 0) {
                val ultimoAlarme = jsonArray.getLong(jsonArray.length() - 1)
                val minutosDesdeAlarme = (now - ultimoAlarme) / (60 * 1000)
                val menosDe2Horas = minutosDesdeAlarme < 120
                // Condição 1: janela NOTURNA — padrão isNight do arquivo (22-23h e 0-5h)
                val isNight = LocalTime.now().run { hour in 22..23 || hour in 0..5 }
                // Condição 2: desliga quando BG > 140 (recuperou)
                val bgNaoRecuperou = bg <= 140
                menosDe2Horas && isNight && bgNaoRecuperou
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /** Verifica se BG esteve > 150 mg/dL nas últimas 4 horas e calcula proporção de leituras elevadas. */
    private fun calcularBgAltoUltimas4h() {
        try {
            val quatroHorasAtras = now - 4 * 60 * 60 * 1000
            val readings = repository.compatGetBgReadingsDataFromTime(quatroHorasAtras, now, false)
                .timeout(3, TimeUnit.SECONDS)
                .onErrorReturnItem(emptyList())
                .blockingGet()
            bgAltoUltimas4h = readings.any { it.value > 150.0 }
            // ═══════════════════════════════════════════════════════════════════
            // P2 — Prolonged Elevation Ratio (Tarciso, Jul/2026)
            // ═══════════════════════════════════════════════════════════════════
            // Calcula a proporção de leituras > 140 mg/dL nas últimas 4h.
            // Se >50% das leituras estão elevadas de forma sustentada (sem pico),
            // o sistema pode precisar de MAIS SMB, não menos.
            // Usado pelo Rate Limiter para evitar sub-corte em elevações crônicas.
            // ═══════════════════════════════════════════════════════════════════
            bgAltoRatio = if (readings.size >= 3) {
                val elevatedCount = readings.count { it.value > 140.0 }
                elevatedCount.toDouble() / readings.size
            } else 0.0
            if (bgAltoUltimas4h) {
                aapsLogger.debug(LTag.APS, "📈 bgAltoUltimas4h=true — bgAltoRatio=${"%.0f".format(bgAltoRatio * 100)}% (>140)")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Erro ao verificar bgAltoUltimas4h", e)
            bgAltoUltimas4h = false
            bgAltoRatio = 0.0
        }
    }

    /** Busca o menor valor de glicose (BG) na última hora. Usado pelo Hypo Recovery Guard. */
    private fun calcularLowestBgUltimaHora() {
        try {
            val umaHoraAtras = now - 60 * 60 * 1000
            val readings = repository.compatGetBgReadingsDataFromTime(umaHoraAtras, now, false)
                .timeout(3, TimeUnit.SECONDS)
                .onErrorReturnItem(emptyList())
                .blockingGet()
            lowestBgUltimaHora = readings.minOfOrNull { it.value } ?: 200.0
            if (lowestBgUltimaHora <= 70.0) {
                aapsLogger.debug(LTag.APS, "📉 lowestBgUltimaHora=$lowestBgUltimaHora — hipo (≤70) detectada na última hora")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Erro ao verificar lowestBgUltimaHora", e)
            lowestBgUltimaHora = 200.0
        }
    }

    /** Calcula o total de SMB administrado nas últimas 4 horas. Usado pelo SMB Load Brake. */
    private fun calcularSmbTotalUltimas4h() {
        try {
            val quatroHorasAtras = now - 4 * 60 * 60 * 1000
            smbTotalUltimas4h = repository.getBolusesDataFromTimeToTime(quatroHorasAtras, now, false)
                .timeout(3, TimeUnit.SECONDS)
                .onErrorReturnItem(emptyList())
                .blockingGet()
                .filter { it.isValid && it.type == Bolus.Type.SMB }
                .sumOf { it.amount }
            aapsLogger.debug(LTag.APS, "📊 smbTotalUltimas4h=$smbTotalUltimas4h")
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "Erro ao calcular smbTotalUltimas4h", e)
            smbTotalUltimas4h = 0.0
        }
    }

    /** Extrai a hora do dia (0-23) de um timestamp em millisegundos. */
    private fun getHourOfDay(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Apply sensor change protection — 2 fases temporais (08/Ago/2026).
     *
     * Substitui a trava antiga (25% por 24h dependente de SENSOR_OK) por
     * proteção temporal decrescente baseada na idade do sensor:
     *   FASE 1 (0-6h):   -30% (leituras mais instáveis após a troca)
     *   FASE 2 (6-12h):  -15% (sensor estabilizando)
     *   12h+:             0%  (sensor estável — trata hiper normalmente)
     *
     * IMPORTANTE: usa o timestamp do SENSOR_CHANGE COMO INFORMADO PELO USUÁRIO.
     * Se o usuário informa data retroativa (instalou antes, informou depois),
     * a idade do sensor já passa de 12h → trava NÃO ativa (sensor já estável).
     * Se informa na hora (troca imediata), FASE 1 protege contra leituras falsas.
     */
    private fun applySensorProtection() {
        val sensorAgeMin = getSensorAgeMinutes()
        if (sensorAgeMin == Long.MAX_VALUE) return   // sem registro = sem trava

        when {
            sensorAgeMin < 360 -> {   // FASE 1: < 6h — sensor recém-trocado
                maxIob *= SENSOR_PROTECTION_FACTOR_STRONG      // 0.70 (-30%)
                maxSMB *= SENSOR_PROTECTION_FACTOR_STRONG
                aapsLogger.debug(LTag.APS, "🛰️ Sensor FASE 1 (${sensorAgeMin}min): maxIob/maxSMB × 0.70")
            }
            sensorAgeMin < 720 -> {   // FASE 2: 6-12h — estabilizando
                maxIob *= SENSOR_PROTECTION_FACTOR_MILD        // 0.85 (-15%)
                maxSMB *= SENSOR_PROTECTION_FACTOR_MILD
                aapsLogger.debug(LTag.APS, "🛰️ Sensor FASE 2 (${sensorAgeMin}min): maxIob/maxSMB × 0.85")
            }
            // >= 12h: sem trava — sensor estabilizado, tratar hiper normalmente
        }
    }

    /**
     * Janela de refeição do usuário (11/Ago/2026): café 6-8h, almoço 11-14h, jantar 17-21h.
     * Prior SUAVE (feature de treino da rede de confiança) — NUNCA gate rígido
     * (validação empírica: 40% das digestões são tardias e legítimas).
     */
    private fun emJanelaRefeicao(): Double = when (hourOfDay) {
        in 6..8, in 11..14, in 17..21 -> 1.0
        else -> 0.0
    }

    /**
     * Idade do sensor em minutos desde a última troca registrada.
     * Usa o timestamp INFORMADO pelo usuário (getLastTherapyRecordUpToNow).
     * Long.MAX_VALUE se não houver registro.
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
            aapsLogger.warn(LTag.APS, "Erro ao obter idade do sensor", e)
            Long.MAX_VALUE
        }
    }

    /** Calcula métricas TIR (última hora e diário) */
    private fun calculateTIRMetrics() {
        this.lastHourTIRLow = tirCalculator.averageTIR(tirCalculator.calculateHour(TIR_LOW_MIN, TIR_LOW_MAX))?.belowPct() ?: 0.0
        this.lastHourTIRAbove95 = tirCalculator.averageTIR(tirCalculator.calculateHour(TIR_LOW_MIN, TIR_HIGH_THRESHOLD))?.abovePct() ?: 0.0
        this.currentTIRLow = tirCalculator.averageTIR(tirCalculator.calculateDaily(TIR_LOW_MIN, TIR_LOW_MAX))?.belowPct() ?: 0.0
        this.currentTIRRange = tirCalculator.averageTIR(tirCalculator.calculateDaily(TIR_LOW_MIN, TIR_LOW_MAX))?.inRangePct() ?: 0.0
        this.currentTIRAbove = tirCalculator.averageTIR(tirCalculator.calculateDaily(TIR_LOW_MIN, TIR_LOW_MAX))?.abovePct() ?: 0.0
    }

    /** Determina hipoTrigger (HIPO_NONE, HIPO_ACTIVE, HIPO_SLOW) e nightprotection */
    private fun determineHipoTrigger() {
        nightprotection = "OFF"
        when {
            lastHourTIRLow > 0 -> {
                maxIob = (maxIob * HYPO_REDUCTION_FACTOR)
                maxSMB = (maxSMB * HYPO_REDUCTION_FACTOR)
                targetBg = TARGET_BG_CLAMP_HIGH
                TirStatus = "IOB/SMB reduced (hipo)"
                hipoTrigger = HIPO_ACTIVE
            }
            currentTIRLow > 20 -> {
                maxIob = (maxIob * 0.95f)
                maxSMB = (maxSMB * 0.95f)
                TirStatus = "IOB/SMB reduced (hipo)"
                targetBg = TARGET_BG_CLAMP_HIGH
                hipoTrigger = HIPO_ACTIVE
            }
            targetBg >= TARGET_BG_CLAMP_HIGH -> {
                TirStatus = "IOB/SMB reduced (hipo)"
                hipoTrigger = HIPO_ACTIVE
            }
            targetBg >= TARGET_BG_CLAMP_LOW && targetBg < TARGET_BG_CLAMP_HIGH -> {
                TirStatus = "IOB/SMB reduced (slow)"
                hipoTrigger = HIPO_SLOW
            }
            bg >= 170 -> {
                TirStatus = "IOB/SMB reduced (slow)"
                hipoTrigger = HIPO_SLOW
            }
            sp.getBoolean(R.string.night_protection, false) && LocalTime.now().run { (hour in 22..23 || hour in 0..5) } -> {
                hipoTrigger = HIPO_ACTIVE
                nightprotection = "ON"
            }
            // N6 (03/Ago/2026): else defensivo — garante reset para HIPO_NONE quando
            // nenhuma condição bate. Hoje a instância é criada nova por ciclo, mas se o
            // DI passar a reutilizar a instância, sem este else o hipoTrigger ficaria
            // PRESO em HIPO_ACTIVE/HIPO_SLOW (maxIob/maxSMB reduzidos e predictionhipo
            // sempre ativo). Puramente defensivo — não muda comportamento atual.
            else -> hipoTrigger = HIPO_NONE
        }
    }

    // ===== FIM DOS MÉTODOS AUXILIARES DE invoke() =====

    /**
     * Unified increment adjustment for both STD and SLOW modes.
     * Consolidates ~80% duplicated logic between applyStandardAdjustment and applySlowAdjustment.
     *
     * @param isStd  true for STD (hipoTrigger==HIPO_NONE), false for SLOW (hipoTrigger==HIPO_SLOW)
     * @param nightMul  night high-BG multiplier (1.8 std, 1.6 slow)
     * @param dayMul    day normal multiplier (1.8 std, 1.3 slow)
     * @param aggMul    day aggressive multiplier (2.0 std, 1.5 slow)
     * @param tag       status label suffix e.g. "(std)" or "(slow)"
     * @param dayBgThresh  higher BG threshold for aggressive path (135 std, 130 slow)
     */
    private fun applyIncrementAdjustment(
        isStd: Boolean,
        nightMul: Double,
        dayMul: Double,
        aggMul: Double,
        tag: String,
        dayBgThresh: Double
    ) {
        val isNight = LocalTime.now().run { (hour in 22..23 || hour in 0..5) }
        if (isNight) {
            // Night: same structure for both modes, only multipliers differ
            if (bg > TARGET_BG_CLAMP_HIGH) {
                val base = bg - (TARGET_BG_CLAMP_HIGH / variableSensitivity)
                val factor = (bg * nightMul) / (base - delta)
                maxIob = (maxIob * factor)
                maxSMB = (maxSMB * factor)
            } else {
                var factor = (bg * 1.8) / TARGET_BG_CLAMP_HIGH
                if (factor < 1.1) factor = 1.1
                maxIob = (maxIob * factor)
                maxSMB = (maxSMB * factor)
            }
            TirStatus = "IOB/SMB (+) $tag"
        } else {
            // Day
            val base = bg - (TARGET_BG_CLAMP_HIGH / variableSensitivity)
            val dayFactor = (bg * dayMul) / (base - delta)
            val aggressiveFactor = (bg * aggMul) / (base - delta)
            val selector = when {
                isStd && bg < 100 && iob <= 1.2 && delta <= 2 && targetBg >= 98 -> base
                stable2 == 1 && bg > 145 && delta >= 0.5 -> aggressiveFactor
                bg > dayBgThresh && delta >= 1 -> aggressiveFactor
                else -> dayFactor
            }
            maxIob = (maxIob * selector)
            maxSMB = (maxSMB * selector)
            TirStatus = when {
                isStd && LocalTime.now().run { hour in 14..22 } && bg < 145 -> "IOB/SMB (+) / 3-10pm (-)"
                !isStd && LocalTime.now().run { hour in 14..22 } && bg < 145 -> "IOB/SMB+/3-10pm-slow"
                targetBg < 79 -> "IOB/SMB (+) (meal)"
                else -> "IOB/SMB (+) $tag"
            }
        }
    }

    /** STD behavior — delegates to unified adjuster */
    private fun applyStandardAdjustment() {
        if (bg > 90 && delta >= -1.5 && hipoTrigger == HIPO_NONE && delta < 9 && bg < 170 && targetBg < TARGET_BG_CLAMP_LOW) {
            applyIncrementAdjustment(isStd = true, nightMul = 1.8, dayMul = 1.8, aggMul = 2.0,
                tag = "(std)", dayBgThresh = 135.0)
        }
    }

    /** SLOW behavior — delegates to unified adjuster */
    private fun applySlowAdjustment() {
        if (bg > 90 && delta >= -1.5 && delta < 9 && hipoTrigger == HIPO_SLOW) {
            applyIncrementAdjustment(isStd = false, nightMul = 1.6, dayMul = 1.3, aggMul = 1.5,
                tag = "(slow)", dayBgThresh = 130.0)
        }
    }

    // ===== MÉTODOS AUXILIARES EXTRAÍDOS DE setData() =====

    /** Target BG definition: bgImpact_target + safety conditions */
    private fun determineTargetBgInSetData() {
        val bgImpact_target: Double = if (bg > 120 && delta > 1) {
            targetStatus = " (+)"
            100.0 - (((bg - BG_TARGET_BASELINE) / BG_IMPACT_DIVISOR) + (delta / 0.5))
        } else {
            targetStatus = " (std)"
            100.0 - ((bg - BG_TARGET_BASELINE) / BG_IMPACT_DIVISOR)
        }
        // Increase targetBG based on low bg alert
        when {
            // ─── PROPOSTA 05/Ago (Opção A, revisada) — target 140 até 05:30 ───
            // Duração separada do lockout de SMB: SMB off por 30 min, mas o
            // target 140 permanece até 05:30 (ou até 2h após o alarme, o que
            // vier primeiro) — replicando o procedimento manual do usuário.
            // ──────────────────────────────────────────────────────────────────
            sp.getBoolean(R.string.low_alert_protection, true) && alarmeLowRecenteParaTarget() && this.targetBg < TARGET_BG_CLAMP_HIGH ->
                this.targetBg = TARGET_BG_CLAMP_HIGH
            sp.getBoolean(R.string.low_alert_protection, true) && alarmesLowGlucoseUltimaHora >= 1 && bg <= 139 && delta <= 3 && this.targetBg < 120.0 ->
                this.targetBg = 120.0
            sp.getBoolean(R.string.low_alert_protection, true) && alarmesLowGlucose24Horas >= 2 && bg <= 139 && delta <= 3 && this.targetBg < 120.0 ->
                this.targetBg = 120.0
            sp.getBoolean(R.string.low_alert_protection, true) && alarmesLowGlucose24Horas >= 4 && bg <= 150 && delta <= 4 && this.targetBg < 120.0 ->
                this.targetBg = TARGET_BG_CLAMP_HIGH
        }
        // Reduce targetBG based on high BG and delta
        if (recentSteps5Minutes > 80 && recentSteps30Minutes > 650 && bg < 130 && delta < 3) this.targetBg = 120.0
        this.targetBg = when {
            this.targetBg >= TARGET_BG_CLAMP_HIGH && bg >= TARGET_BG_CLAMP_LOW && delta > 1 && iob < 4.0 && LocalTime.now().run { hour in 6..21 } -> TARGET_BG_CLAMP_LOW
            this.targetBg >= TARGET_BG_CLAMP_HIGH && bg >= 160 && delta > 2 && iob < 3.0 && LocalTime.now().run { (hour in 22..23 || hour in 0..5) } -> TARGET_BG_CLAMP_LOW
            this.targetBg >= TARGET_BG_CLAMP_LOW && this.targetBg < TARGET_BG_CLAMP_HIGH && bg >= 155 && delta > 2 -> bgImpact_target.coerceIn(85.0, 119.0)
            this.targetBg >= TARGET_BG_CLAMP_HIGH -> TARGET_BG_CLAMP_HIGH
            this.targetBg >= TARGET_BG_CLAMP_LOW && this.targetBg < TARGET_BG_CLAMP_HIGH -> TARGET_BG_CLAMP_LOW
            this.targetBg == 75.0 -> 75.0
            else -> bgImpact_target.coerceIn(85.0, 119.0)
        }
    }

    /** TDD calculations weighted from 7d, 2d, daily, last 4h/8h */
    private fun calculateTDDMetrics(): Double {
        val tdd7P = SafeParse.stringToDouble(sp.getString(R.string.key_tdd7, "35"))
        var tdd7Days = tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        if (tdd7Days == 0.0f || tdd7Days < tdd7P) tdd7Days = tdd7P.toFloat()
        // TDD Adjustment based on TIR
        if (lastHourTIRLow > 0) { tdd7Days = (tdd7Days / 1.1).toFloat(); TDDStatus = "TDD was reduced" }
        else if (currentTIRLow > 20) { tdd7Days = (tdd7Days / 1.09).toFloat(); TDDStatus = "TDD was reduced" }
        if (delta >= 0 && bg > 90 && LocalTime.now().run { hour in 7..20 }) {
            val normalizedTIR = lastHourTIRAbove95.coerceIn(1.0, 30.0)
            tdd7Days *= (1.0f + ((normalizedTIR / 100.0f) / 1.4f)).toFloat()
            if (tdd7Days.toDouble() / tdd7P < 1.1f) tdd7Days = tdd7P.toFloat() * 1.1f
            TDDStatus = "TDD was increased"
        }
        this.tdd7DaysPerHour = tdd7Days.toDouble() / 24
        var tdd2Days = tddCalculator.averageTDD(tddCalculator.calculate(2, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        if (tdd2Days == 0.0f || tdd2Days < (tdd7P / 1.82) || tdd2Days > (tdd7P / 1.83)) tdd2Days = (tdd7Days / 1.82).toFloat()
        this.tdd2DaysPerHour = tdd2Days.toDouble() / 24
        val tddLast4H = tdd2DaysPerHour * 4
        var tddDaily = tddCalculator.averageTDD(tddCalculator.calculate(1, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        if (tddDaily == 0.0f || tddDaily < (tdd7P / 1.82) || tddDaily > (tdd7P / 1.83)) tddDaily = (tdd7Days / 1.82).toFloat()
        this.tddPerHour = tddDaily.toDouble() / 24
        var tdd24Hrs = tddCalculator.calculateDaily(-24, 0)?.totalAmount?.toFloat() ?: 0.0f
        if (tdd24Hrs == 0.0f || tdd24Hrs < (tdd7P / 1.82) || tdd24Hrs > (tdd7P / 1.83)) tdd24Hrs = (tdd7Days / 1.82).toFloat()
        this.tdd24HrsPerHour = tdd24Hrs.toDouble() / 24
        val tddLast8to4H = tdd24HrsPerHour * 4
        tddBLast4Hrs = (tddCalculator.calculateDaily(-4, 0)?.totalAmount?.toFloat() ?: 0.0f).toDouble()
        val tddWeightedFromLast8H = ((1.4 * tddLast4H) + (0.6 * tddLast8to4H)) * 3
        return (tddWeightedFromLast8H * 0.60) + (tdd7Days.toDouble() * 0.20) + (tddDaily.toDouble() * 0.20)
    }

    /** Steps count + heart rate from DB or watch */
    private fun loadStepsAndHeartRate() {
        val timeMillisNow = System.currentTimeMillis()
        val timeMillis5 = timeMillisNow - 5 * 60 * 1000
        val timeMillis10 = timeMillisNow - 10 * 60 * 1000
        val timeMillis15 = timeMillisNow - 15 * 60 * 1000
        val timeMillis30 = timeMillisNow - 30 * 60 * 1000
        val timeMillis60 = timeMillisNow - 60 * 60 * 1000
        val timeMillis180 = timeMillisNow - 180 * 60 * 1000

        val stepsCount5 = repository.getLastStepsCountFromTimeToTime(timeMillis5, timeMillisNow)?.steps5min ?: 0
        val stepsCount10 = repository.getLastStepsCountFromTimeToTime(timeMillis10, timeMillisNow)?.steps10min ?: 0
        val stepsCount15 = repository.getLastStepsCountFromTimeToTime(timeMillis15, timeMillisNow)?.steps15min ?: 0
        val stepsCount30 = repository.getLastStepsCountFromTimeToTime(timeMillis30, timeMillisNow)?.steps30min ?: 0
        val stepsCount60 = repository.getLastStepsCountFromTimeToTime(timeMillis60, timeMillisNow)?.steps60min ?: 0
        val stepsCount180 = repository.getLastStepsCountFromTimeToTime(timeMillis180, timeMillisNow)?.steps180min ?: 0

        if (sp.getBoolean(R.string.count_steps_watch, false)) {
            this.recentSteps5Minutes = stepsCount5; this.recentSteps10Minutes = stepsCount10
            this.recentSteps15Minutes = stepsCount15; this.recentSteps30Minutes = stepsCount30
            this.recentSteps60Minutes = stepsCount60; this.recentSteps180Minutes = stepsCount180
        } else {
            this.recentSteps5Minutes = StepService.getRecentStepCount5Min()
            this.recentSteps10Minutes = StepService.getRecentStepCount10Min()
            this.recentSteps15Minutes = StepService.getRecentStepCount15Min()
            this.recentSteps30Minutes = StepService.getRecentStepCount30Min()
            this.recentSteps60Minutes = StepService.getRecentStepCount60Min()
            this.recentSteps180Minutes = StepService.getRecentStepCount180Min()
        }
        // Heart rate
        try {
            val hr5 = repository.getHeartRatesFromTimeToTime(timeMillis5, timeMillisNow)
            this.averageBeatsPerMinute = hr5.map { it.beatsPerMinute.toInt() }.run { if (isNotEmpty()) average() else 80.0 }
        } catch (_: Exception) { this.averageBeatsPerMinute = 80.0 }
        try {
            val hr60 = repository.getHeartRatesFromTimeToTime(timeMillis60, timeMillisNow)
            this.averageBeatsPerMinute60 = hr60.map { it.beatsPerMinute.toInt() }.run { if (isNotEmpty()) average() else 80.0 }
        } catch (_: Exception) { this.averageBeatsPerMinute60 = 80.0 }
        try {
            val hr180 = repository.getHeartRatesFromTimeToTime(timeMillis180, timeMillisNow)
            this.averageBeatsPerMinute180 = hr180.map { it.beatsPerMinute.toInt() }.run { if (isNotEmpty()) average() else 80.0 }
        } catch (_: Exception) { this.averageBeatsPerMinute180 = 80.0 }
    }

    /** MaxIOB + MaxSMB definition (Phase 1) */
    private fun determineMaxIOBandMaxSMB(profile: Profile) {
        // MaxIOB Definition -> Phase1
        if (targetBg < 122) {
            if ((LocalTime.now().run { hour in 12..22 }) && bg < 130) {
                this.maxIob = ((bg / (10 * hourOfDay)) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
            } else {
                if (bg >= 170) this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
                if (bg >= 170 && iob > 2) {
                    val plusAction = (bg / 100) - 1
                    this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0) + plusAction
                }
                if (bg < 170 && bg >= 140) this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
                if (bg < 170 && bg >= 140 && delta > 1 && iob > 2) {
                    val plusAction = (bg / 100) - 1.1
                    this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0) + plusAction
                }
                if (bg < 140) this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
                if (bg < 140 && delta > 4.5 && iob > 4 && (LocalTime.now().run { hour in 6..7 })) {
                    val plusAction = (bg / 100) - 1.2
                    this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0) + plusAction
                }
                if (bg < 140 && delta > 1 && iob > 2.5 && (LocalTime.now().run { hour in 11..13 })) {
                    val plusAction = (bg / 100) - 1.1
                    this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0) + plusAction
                }
                if (bg < 140 && delta > 4.5 && iob > 4 && (LocalTime.now().run { hour in 18..19 })) {
                    val plusAction = (bg / 100) - 1.2
                    this.maxIob = ((bg / 130) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0) + plusAction
                }
            }
        } else {
            this.maxIob = ((bg / 95) + (delta / 70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
        }
        MaxIobProfround = ((this.maxIob * 100).roundToInt() / 100.0)
        // C3 (01/Ago/2026): maxSMBTEST removido (nunca consumido — cálculo morto com maxIobTEST=0)
        // MaxSMB Definition -> Phase1
        if (targetBg < 79 && delta >= 0) {
            this.maxSMB = (this.maxIob / 1.8) + sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0)
        } else {
            this.maxSMB = (this.maxIob / 2.2) + sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0)
        }
        // C3 (01/Ago/2026): maxSMB2 removido — maxSMB já é o valor final aqui

        // ════════════════════════════════════════════════════════════════
        // Proposta #3 — Teto Noturno Contextual (Tarciso, Jul/2026)
        // ════════════════════════════════════════════════════════════════
        // Reduz maxIob e maxSMB no período noturno quando há contexto
        // de hiper recente, evitando overshoot de SMB → hipo.
        // Condições separam: pós-hiper (corte), refeição tardia (sem corte),
        // e noturno estável (redução moderada).
        // ════════════════════════════════════════════════════════════════
        val isNight = LocalTime.now().run { hour in 22..23 || hour in 0..5 }
        if (isNight) {
            val nightMultiplier = when {
                // Condição 1 — Pós-hiper noturno com IOB já baixo
                // Corte drástico: BG estável/target, hiper recente, IOB expirou
                bgAltoUltimas4h && iob < 1.0 && bg in 90.0..130.0 && delta < 1.5 -> 0.4
                // Condição 2 — Noturno subindo (provável refeição tardia)
                // Sem corte: precisa de insulina para evitar hiper
                delta > 1.5 && bg > 130.0 -> 1.0
                // Condição 3 — Noturno estável sem hiper recente
                // Redução moderada: prevenção com flexibilidade
                bg in 90.0..130.0 && delta in -1.0..1.0 -> 0.7
                // Outros casos: sem corte
                else -> 1.0
            }
            this.maxIob *= nightMultiplier
            this.maxSMB *= nightMultiplier
        }
    }

    init {
        injector.androidInjector().inject(this)
    }

    companion object {
        // L5 (01/Ago/2026): versão única usada no reason (Loop Log/Nightscout).
        // Atualize AQUI a cada release — evita divergência com o build real.
        // 05/Ago: lockout pós-alarme + target 140 noturno (isNight 22-5)
        // 11/Ago: Fase 2 — rede neural de confiança do BG ligada ao SMB (INPUT_SIZE 16,
        //         travas 5/6/7, piso 0.8 digestão, features direção/janela de refeição)
        private const val BUILD_VERSION = "246 / 13-Sep-2026"

        // PD gains
        private const val KP = 0.0075
        private const val KD = 0.015

        // TIR thresholds
        private const val TIR_LOW_MIN = 65.0
        private const val TIR_LOW_MAX = 95.0
        private const val TIR_HIGH_THRESHOLD = 140.0

        // bgImpact calculation
        private const val BG_BASELINE = 100.0
        private const val BG_IMPACT_DIVISOR = 4.0
        private const val BG_IMPACT_CLAMP_MIN = 93.0
        private const val BG_IMPACT_CLAMP_MAX = 110.0

        // ISF sensibility clamp bounds
        private const val ISF_CLAMP_MIN = 30.0
        private const val ISF_CLAMP_MAX = 50.0

        // bgAdjustment bounds
        private const val ADJUSTMENT_FLOOR = 0.2
        private const val ADJUSTMENT_HIGH_TARGET = 0.35
        private const val TARGET_BG_CLAMP_LOW = 120.0
        private const val TARGET_BG_CLAMP_HIGH = 140.0
        private const val DELTA_DIVISOR = 25.0

        // Max IOB / SMB floors
        private const val MAX_IOB_FLOOR = 1.1
        private const val DYN_MAX_SMB_FLOOR = 0.1
        private const val MAX_SMB_BUFFER = 1.15

        // Sensor change protection — 2 fases temporais (08/Ago/2026)
        private const val SENSOR_PROTECTION_FACTOR_STRONG = 0.70   // FASE 1 (0-6h): -30%
        private const val SENSOR_PROTECTION_FACTOR_MILD = 0.85     // FASE 2 (6-12h): -15%
        // 12h+: sem trava (0%)

        // Hypo trigger reduction factor (same 25% reduction as sensor change)
        private const val HYPO_REDUCTION_FACTOR = 0.75

        // Night protection hours
        // Janela noturna UNIFICADA (M1, 01/Ago/2026): 22h-5h59
        // Antes: NIGHT_HOUR_START=23/END=4 (nightTrigger) vs hardcoded 22-5 nos demais guards
        // (slowDeclineNoturno, hypoRecovery, smbExhaustion, Rate Limiter, Teto Noturno) —
        // às 22h e às 05h os guards discordavam. Agora todos usam 22-5.
        private const val NIGHT_HOUR_START = 22
        private const val NIGHT_HOUR_END = 5

        // BG target baseline (used in bgImpact_target formula)
        private const val BG_TARGET_BASELINE = 95.0

        // Dynamic ISF clamp bounds (adjustFactorsdynisfBasedOnBgAndHypo)
        private const val ISF_DYN_CLAMP_MIN = 90.0
        private const val ISF_DYN_CLAMP_MAX = 110.0

        // Variable sensitivity clamp for dynISF mode
        private const val SENSITIVITY_ISF_CLAMP_MIN = 65.0
        private const val SENSITIVITY_ISF_CLAMP_MAX = 200.0

        // Hipo trigger states (Item 5 — replaces magic numbers 0.0/1.0/2.0)
        private const val HIPO_NONE = 0.0
        private const val HIPO_ACTIVE = 1.0
        private const val HIPO_SLOW = 2.0

        // DinMaxIob denominators (Item 10 — replaces magic numbers 45.0/40.0)
        private const val DIN_MAX_IOB_DENOM_HIGH = 45.0
        private const val DIN_MAX_IOB_DENOM_LOW = 40.0

        // CSV rotation: max 2 MB per file (~10k lines, ~1-2 weeks)
        private const val MAX_CSV_SIZE_BYTES = 2 * 1024 * 1024L

        // Key de SharedPreferences para timestamps de alarme de low glucose
        private const val KEY_LOW_GLUCOSE_ALARM_TIMESTAMPS = "low_glucose_alarm_timestamps"
    }

    /**
     * Rotaciona o CSV se excedeu o tamanho máximo.
     * Renomeia o atual para .bak (sobrescreve se existir) e reseta o flag de header.
     */
    private fun rotateCsvIfNeeded(file: File, isHb: Boolean) {
        if (!file.exists()) return
        if (file.length() <= MAX_CSV_SIZE_BYTES) return

        val backup = File(file.absolutePath + ".bak")
        try {
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
            aapsLogger.debug(LTag.APS, "CSV rotated: ${file.name} → .bak (${file.length()} bytes)")
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "Failed to rotate CSV ${file.name}: ${e.message}")
        }
    }
}


