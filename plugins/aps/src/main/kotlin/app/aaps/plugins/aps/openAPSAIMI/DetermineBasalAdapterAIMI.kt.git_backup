package app.aaps.plugins.aps.openAPSAIMI

import android.os.Environment
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
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.aps.APSResultObject
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.R.string.key_oaps_aimi_morning_factor
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
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.Float as Float1


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
    // private var lastHourTIRabove120: Double = 0.0
    // private var lastHourTIRabove140: Double = 0.0
    //private var lastHourTIRabove160: Double = 0.0


    // Tarciso DynamicAdjuts - Next line added
    private var lastHourTIRLow: Double = 0.0
    private var lastHourTIRAbove95: Double = 0.0
    private var bg = 0.0



    // Tarciso changed targetBg from 100 to 95     private var targetBg = 100.0f
    //private var targetTEST = 90.0
    private var targetBg = 90.0
    // Tarciso changed normalBgThreshold from 150 to 130    ( private var normalBgThreshold = 150.0f)
    private var normalBgThreshold = 120.0
    private var sensorChangeEvents = 0
    private var delta = 0.0
    private var shortAvgDelta = 0.0
    private var longAvgDelta = 0.0
    private var lastsmbtime = 0
    // private var hipotime: Long = 0
    private var lastmealtime = 0
    private var accelerating_up: Int = 0
    private var deccelerating_up: Int = 0
    private var accelerating_down: Int = 0
    private var deccelerating_down: Int = 0
    private var stable: Int = 0
    private var stable2: Int = 0
    private var bgstatus: Int = 0
    private var ProfileISF = 0.0
    // Tarciso
    private var DinMaxIob = 0.0
    private var DynMaxSmb = 0.0
    private var maxSMB = 0.0
    private var adjustDynIsf = 0.0
    private var MealEvent: List<TherapyEvent>? = null
    private var SensorChange: List<TherapyEvent>? = null
    private var TherapyNote: List<TherapyEvent>? = null
    private var TherapyNoteok: List<TherapyEvent>? = null
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
    private var maxSMB2 = 0.0
    // private var TIR120 = 0.0
    // private var TIR140 = 0.0
    // private var TIR160 = 0.0
    private var hourlyfactor: Double = 0.0
    private var hipoTrigger = 0.0
    private var mealTrigger = 0.0

    private var maxIob = 0.0
    private var maxIobTEST = 0.0
    private var maxSMBTEST = 0.0
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

    override var currentTempParam: String? = null
    override var iobDataParam: String? = null
    override var glucoseStatusParam: String? = null
    override var profileParam: String? = null
    override var mealDataParam: String? = null
    override var scriptDebug = ""

    private var now: Long = 0
    private var bgTime: Long = 0


    // private var previusbgTime: Long = -1 // Armazena o tempo da leitura anterior
    // private var bgAcceleration: Float = 0.0f // Armazena a aceleração calculada



    @Suppress("SpellCheckingInspection")
    override operator fun invoke(): APSResultObject {
        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal <<<")

        val predictedSMB = calculateSMBFromModel()
        var smbToGive = predictedSMB
        val morningfactor = SafeParse.stringToDouble(sp.getString(key_oaps_aimi_morning_factor, "50")) / 100.0
        val afternoonfactor = SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_afternoon_factor, "50")) / 100.0
        val eveningfactor = SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_evening_factor, "50")) / 100.0
        val hyperfactor = SafeParse.stringToDouble(sp.getString(R.string.key_oaps_aimi_hyper_factor, "50")) / 100.0
        val dynISFadjust2 = SafeParse.stringToDouble(sp.getString(R.string.key_DynISFAdjust, "120")) / 100.0



        val (adjustedMorningFactor, adjustedAfternoonFactor, adjustedEveningFactor) =
            adjustFactorsBasedOnBgAndHypo(bg.toDouble(), delta.toDouble(), morningfactor, afternoonfactor, eveningfactor)

        // Adjustments Phase 1 - User Notes -> Next Phase 2

        // Tarciso hipo protection
        // var isEventActive: Boolean = false // Flag para controlar se o evento está ativo

        // Tarciso Delta Lock
        //if (delta >= 9) {delta = 6.0}

        // Tarciso New Sensor Added lock
        if (SensorChange.toString().contains("SENSOR_CHANGE") && !TherapyNote.toString().contains("SENSOR_OK", ignoreCase = true)) {
            // if "SENSOR_CHANGE" in SensorChange and "SENSOR_OK" not in TherapyNote:
            maxIob = (maxIob * 0.75f)
            maxSMB = (maxSMB * 0.75f)
        }

        if (TherapyNote.toString().contains("SENSOR_OK", ignoreCase = true)) {
            maxIob = maxIob
            maxSMB = maxSMB
        }


        this.lastHourTIRLow = tirCalculator.averageTIR(tirCalculator.calculateHour(65.0, 95.0))?.belowPct()!!
        this.lastHourTIRAbove95 = tirCalculator.averageTIR(tirCalculator.calculateHour(65.0, 90.0))?.abovePct()!!
        this.currentTIRLow = tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 95.0))?.belowPct()!!
        this.currentTIRRange = tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 95.0))?.inRangePct()!!
        this.currentTIRAbove = tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 95.0))?.abovePct()!!

        // Adjustments Phase 2 - maxIOB and maxSMB adjustment  -> Next Phase 3
        // Tarciso maxIOB and maxSMB adjustment based on Hipo triggers
        var nightprotection = "OFF"
        if (lastHourTIRLow > 0) {
            maxIob = (maxIob * 0.75f)
            maxSMB = (maxSMB * 0.75f)
            targetBg = 120.0
            TirStatus = "IOB/SMB reduced (hipo)"
            hipoTrigger = 1.0

        } else if (currentTIRLow > 20) {
            maxIob = (maxIob * 0.95f)
            maxSMB = (maxSMB * 0.95f)
            TirStatus = "IOB/SMB reduced (hipo)"
            targetBg = 120.0
            hipoTrigger = 1.0

        } else if (targetBg >= 120) {
            TirStatus = "IOB/SMB reduced (hipo)"
            hipoTrigger = 1.0

        } else if (sp.getBoolean(R.string.night_protection, false)===true && LocalTime.now().run { (hour in 22..23 || hour in 0..5) }) {
            // Night Protection
            hipoTrigger = 1.0
            nightprotection = "ON"
        }



        // Tarciso BG Adjustment
        if (bg > 90 && delta >= -1.5 && hipoTrigger == 0.0 && delta < 9 && bg < 170) {
            if (LocalTime.now().run { (hour in 22..23 || hour in 0..5) }) {
                if (bg > 130) {
                    /* var incrementFactor = (bg * 1.8) / (sp.getDouble(R.string.key_openapsaimi_power_smb1, 110.0) - delta)
                        if (incrementFactor < 1.1) {
                            incrementFactor = 1.1
                        }
                        maxIob = (maxIob * incrementFactor)
                        maxSMB = (maxSMB * incrementFactor) */

                    // Codigo original antes de colocar o smbpower valor fixo de 140
                    // var incrementFactor1 = bg - (sp.getDouble(R.string.key_openapsaimi_power_smb1, 110.0) / variableSensitivity)
                    var incrementFactor1 = bg - (140 / variableSensitivity)
                    var incrementFactor2 = (bg * 1.8) / (incrementFactor1 - delta)
                    maxIob = (maxIob * incrementFactor2)
                    maxSMB = (maxSMB * incrementFactor2)
                    TirStatus = "IOB/SMB (+) (std)"

                } else {
                    // Codigo original antes de colocar o smbpower valor fixo de 140
                    // var incrementFactor = (bg * 1.8) / sp.getDouble(R.string.key_openapsaimi_power_smb2, 170.0)
                    var incrementFactor = (bg * 1.8) / 140
                    if (incrementFactor < 1.1) {
                        incrementFactor = 1.1
                    }
                    maxIob = (maxIob * incrementFactor)
                    maxSMB = (maxSMB * incrementFactor)
                    TirStatus = "IOB/SMB (+) (std)"

                }

            } else {
                // var incrementFactor1 = bg - (sp.getDouble(R.string.key_openapsaimi_power_smb1, 110.0)/ 70 )

                // Codigo original antes de colocar o smbpower valor fixo de 140
                // var incrementFactor1 = bg - (sp.getDouble(R.string.key_openapsaimi_power_smb1, 110.0) / variableSensitivity)
                var incrementFactor1 = bg - (140 / variableSensitivity)
                var incrementFactor2 = (bg * 1.8) / (incrementFactor1 - delta)
                var incrementFactor3 = (bg * 2) / (incrementFactor1 - delta)
                var selector = incrementFactor2
            selector = when{
                stable2 == 1 && bg > 145 && delta >= 0.5 -> incrementFactor3
                bg > 130 && delta >= 1 -> incrementFactor3
                else -> incrementFactor2
            }
                maxIob = (maxIob * selector)
                maxSMB = (maxSMB * selector)


                /*if (stable2 == 1 && bg > 135 && delta >= 0.5){
                    maxIob = (maxIob * incrementFactor3)
                    maxSMB = (maxSMB * incrementFactor3)
                } else {
                    maxIob = (maxIob * incrementFactor2)
                    maxSMB = (maxSMB * incrementFactor2)
                }

                if (bg > 125 && delta > 1){
                    maxIob = (maxIob * incrementFactor3)
                    maxSMB = (maxSMB * incrementFactor3)
                } else {
                    maxIob = (maxIob * incrementFactor2)
                    maxSMB = (maxSMB * incrementFactor2)
                }*/



                if ((LocalTime.now().run { hour in 14..22 }) && bg < 145) {
                    TirStatus = "IOB/SMB (+) / 3-10pm (-)"
                } else if (targetBg < 79) {
                    TirStatus = "IOB/SMB (+) (meal)"
                } else {
                    TirStatus = "IOB/SMB (+) (std)"
                }

            }
        }









        // Adjustments Phase 3 - maxIOB and maxSMB adjustment

        // Tarciso Dynamic Max IOB
        DinMaxIob = ((bg / 100.0) * (bg / 45.0) + (delta / 2.0))
        // val MaxlimitIOB = maxIob * 1.10
        // val MaxlimitIOBmeal = maxIob * 1.12
        if (DinMaxIob < 1.0) {
            DinMaxIob = 1.0
        } else if (DinMaxIob < maxIob && delta > 0 && bg > 140) {
            DinMaxIob = ((bg / 100.0) * (bg / 40.0) + (delta / 2.0))
        } else if (DinMaxIob < maxIob) {
            DinMaxIob
        } else {
            DinMaxIob = maxIob.toDouble()
        }

        // Tarciso Dynamic Max SMB
        DynMaxSmb = (((bg / 200) * (bg / 100)) + (delta / 2)).toDouble()
        val MaxlimitSMB = maxSMB * 1.15
        if (DynMaxSmb < 0.1) {
            DynMaxSmb = 0.1
        } else if (DynMaxSmb < maxSMB) {
            DynMaxSmb = DynMaxSmb
        } else if (DynMaxSmb > maxSMB && bg > 149 && delta >= 3) {
            DynMaxSmb = MaxlimitSMB
        } else {
            DynMaxSmb = maxSMB.toDouble()
        }



        smbToGive = when {
            // Tarciso DynamicAdjuts
            hourOfDay in 1..4   -> smbToGive * adjustedEveningFactor.toFloat()
            hourOfDay in 5..10  -> smbToGive * adjustedMorningFactor.toFloat()
            hourOfDay in 11..21 -> smbToGive * adjustedAfternoonFactor.toFloat()
            hourOfDay in 22..23 -> smbToGive * adjustedEveningFactor.toFloat()
            bg > 180            -> (smbToGive * hyperfactor).toFloat()
            else                -> smbToGive
        }



        smbToGive = applySafetyPrecautions(smbToGive)
        smbToGive = roundToPoint05(smbToGive)

        logDataToCsv(predictedSMB, smbToGive)
        logDataToCsvHB(predictedSMB, smbToGive)

        // Rounded to better view in Loop Log
        val maxIobround = (maxIob * 100).roundToInt() / 100.0
        val DinMaxIobround = (DinMaxIob * 100).roundToInt() / 100.0
        val MaxSMBround = (maxSMB * 100).roundToInt() / 100.0
        val DinMaxSMBround = (DynMaxSmb * 100).roundToInt() / 100.0
        MaxSMBProfround = ((maxSMB2 * 100).roundToInt() / 100.0)

        val Autosense = (tddBLast4Hrs / 4) / bg
        val Autosense2 = (iob / bg) / 2


        this.protection = when {
            predictedBg < targetBg && hipoTrigger == 1.0 && delta >= sp.getDouble(R.string.key_openapsaimi_power_hipo, 110.0) -> "(OFF)"
            predictedBg < targetBg && hipoTrigger == 0.0 && delta >= sp.getDouble(R.string.key_openapsaimi_power_std, 110.0) -> "(OFF)"
            predictedBg < targetBg -> "(ON)"
            else -> {"(OFF)"}
        }



        // val futureBG2 = (bg+(delta*1.5))-((iob/1.5)*variableSensitivity)

        val constraintStr = " MaxIOB: $MaxIobProfround -> $maxIobround -> ${DinMaxIobround} <br/> MaxSMB: $MaxSMBProfround -> $MaxSMBround -> ${DinMaxSMBround} <br/>" +

            "React Factor:   ${(dynISFadjust2 * 100).roundToInt() } -> ${(adjustDynIsf * 100).roundToInt() } <br/>" +
            "Hourly Factor:   ${(hourlyfactor).roundToInt()  } -> ${(adjustedMorningFactor * 100 ).roundToInt() } <br/>" +
            //"maxSMBTest:   ${(maxSMBTEST ) } " +
            //"(5 - 11) Morning Factor: ${(morningfactor * 100).roundToInt() } -> ${(adjustedMorningFactor * 100 ).roundToInt() } <br/>" +
            //"(11 - 22) Aft. Factor: ${(afternoonfactor * 100).roundToInt() } -> ${(adjustedAfternoonFactor * 100).roundToInt() } <br/>" +
            // "(22 - 5) Evening Factor: ${(eveningfactor * 100).roundToInt() } -> ${(adjustedEveningFactor * 100).roundToInt() } <br/>" +
            // "ISF:   ${ProfileISF.roundToInt()} -> ${variableSensitivity.roundToInt()} <br/>" +

            "ISF: ${variableSensitivity.roundToInt()} / Stable BG:   $stable2 "

        // "Meal Bolus: ${lastmealtime} MinAgo"
        val glucoseStr = " bg: $bg <br/> targetBg: ${(targetBg).roundToInt()} $targetStatus <br/> Protection: ${(predictedBg).roundToInt()} $protection <br/>" + // futureBgT:
            // "futureBg TESTE: ${futureBG2.roundToInt()} <br/> " +// $predictedBgT <br/>
            "delta: $delta <br/> short avg delta: $shortAvgDelta <br/> long avg delta: $longAvgDelta <br/>"//+
            //" accelerating_up: $accelerating_up <br/> deccelerating_up: $deccelerating_up <br/> accelerating_down: $accelerating_down <br/> deccelerating_down: $deccelerating_down <br/> stable:
        //$stable"
        val iobStr = " IOB: ${roundToPoint05(iob.toFloat())} <br/> tdd 7d/h: ${roundToPoint05(tdd7DaysPerHour.toFloat())} <br/> " +
            "tdd 2d/h : ${roundToPoint05(tdd2DaysPerHour.toFloat())} <br/> " +
            "tdd Last4h : ${roundToPoint05(tddBLast4Hrs.toFloat())}<br/>" +
            "Auto Sense TDD Test:   ${roundToPoint05((Autosense * 100).toFloat()) } <br/>"+
            "Auto Sense IOB Test:   ${roundToPoint05((Autosense2 * 100).toFloat()) } <br/>"
            //"tdd daily/h : ${roundToPoint05(tddPerHour)} <br/> " +
            //"tdd 24h/h : ${roundToPoint05(tdd24HrsPerHour)}<br/>" +
            //"tdd Last4h : ${roundToPoint05(tddBLast4Hrs)}<br/>"
            //"basalaimi : $basalaimi <br/> basalsmb : $basalSMB <br/>"
        val profileStr = " Hour: $hourOfDay / Weekend: $weekend <br/>" +
            " 5m Steps: $recentSteps5Minutes / 10m Steps: $recentSteps10Minutes <br/> 15m Steps: $recentSteps15Minutes /" +
            " 30m Steps: $recentSteps30Minutes <br/> 60m Steps: $recentSteps60Minutes / 180m Steps: $recentSteps180Minutes <br/>" //+
            //" Heart Beat/mim(average 5 min) : $averageBeatsPerMinute <br/> Heart Beat/min(average 180 min) : $averageBeatsPerMinute180"

        var mealStr = "Bg Adjust: ${TirStatus}<br/> TDD Adjust: $TDDStatus<br/>" +
            "Night Protection: $nightprotection<br/>" +
            "todayTIRLow: ${(currentTIRLow).roundToInt()}%<br/> lastHourLow: ${(lastHourTIRLow).roundToInt()}%<br/>"//+

            //"Delta Adjustment: $DeltaStatus<br/>"+ // tags180to240minAgo: $tags180to240minAgo<br/> " +
            // "lastHourAbove: ${(lastHourTIRAbove95).roundToInt()}%<br/> todayTIRAbove: ${(currentTIRAbove).roundToInt()}%<br/> todayTIRRange: ${(currentTIRRange).roundToInt()}%<br/> " +
            // "todayTIRLow: ${(currentTIRLow).roundToInt()}%<br/> lastHourLow: ${(lastHourTIRLow).roundToInt()}%<br/>"
        val reason = "Requested ${smbToGive}u to the pump" +
            ",<br/>AIMI.1 ML.2, 14/11/2023 <br/>" +
            "Chg. Ver. 172"
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


    private fun logDataToCsv(predictedSMB: Float1, smbToGive: Float1) {
        val dateStr = dateUtil.dateAndTimeString(dateUtil.now())

        val headerRow = "dateStr,dateLong,hourOfDay,weekend," +
            "bg,targetBg,iob,cob,lastCarbAgeMin,futureCarbs,delta,shortAvgDelta,longAvgDelta," +
            "tdd7DaysPerHour,tdd2DaysPerHour,tddPerHour,tdd24HrsPerHour," +
            "recentSteps5Minutes,recentSteps10Minutes,recentSteps15Minutes,recentSteps30Minutes,recentSteps60Minutes,recentSteps180Minutes," +
            "tags0to60minAgo,tags60to120minAgo,tags120to180minAgo,tags180to240minAgo," +
            "predictedSMB,maxIob,maxSMB,smbGiven\n"
        val valuesToRecord = "$dateStr,${dateUtil.now()},$hourOfDay,$weekend," +
            "$bg,$targetBg,$iob,$cob,$lastCarbAgeMin,$futureCarbs,$delta,$shortAvgDelta,$longAvgDelta," +
            "$tdd7DaysPerHour,$tdd2DaysPerHour,$tddPerHour,$tdd24HrsPerHour," +
            "$recentSteps5Minutes,$recentSteps10Minutes,$recentSteps15Minutes,$recentSteps30Minutes,$recentSteps60Minutes,$recentSteps180Minutes," +
            "$tags0to60minAgo,$tags60to120minAgo,$tags120to180minAgo,$tags180to240minAgo," +
            "$predictedSMB,${DinMaxIob},${DynMaxSmb},$smbToGive"

        val file = File(path, "AAPS/oapsaimi_records.csv")
        if (!file.exists()) {
            file.createNewFile()
            file.appendText(headerRow)
        }
        file.appendText(valuesToRecord + "\n")
    }

    private fun logDataToCsvHB(predictedSMB: Float1, smbToGive: Float1) {
        val dateStr = dateUtil.dateAndTimeString(dateUtil.now())

        val headerRow = "dateStr,dateLong,hourOfDay,weekend," +
            "bg,targetBg,iob,cob,lastCarbAgeMin,futureCarbs,delta,shortAvgDelta,longAvgDelta," +
            "accelerating_up,deccelerating_up,accelerating_down,deccelerating_down,stable," +
            "tdd7DaysPerHour,tdd2DaysPerHour,tddDailyPerHour,tdd24HrsPerHour," +
            "recentSteps5Minutes,recentSteps10Minutes,recentSteps15Minutes,recentSteps30Minutes,recentSteps60Minutes,averageBeatsPerMinute, averageBeatsPerMinute180," +
            "tags0to60minAgo,tags60to120minAgo,tags120to180minAgo,tags180to240minAgo," +
            "variableSensitivity,lastbolusage,predictedSMB,maxIob,maxSMB,smbGiven\n"
        val valuesToRecord = "$dateStr,${dateUtil.now()},$hourOfDay,$weekend," +
            "$bg,$targetBg,$iob,$cob,$lastCarbAgeMin,$futureCarbs,$delta,$shortAvgDelta,$longAvgDelta," +
            "$accelerating_up,$deccelerating_up,$accelerating_down,$deccelerating_down,$stable," +
            "$tdd7DaysPerHour,$tdd2DaysPerHour,$tddPerHour,$tdd24HrsPerHour," +
            "$recentSteps5Minutes,$recentSteps10Minutes,$recentSteps15Minutes,$recentSteps30Minutes,$recentSteps60Minutes,$recentSteps180Minutes," +
            "$averageBeatsPerMinute, $averageBeatsPerMinute180," +
            "$tags0to60minAgo,$tags60to120minAgo,$tags120to180minAgo,$tags180to240minAgo," +
            "$variableSensitivity,$predictedSMB,$DinMaxIob,$DynMaxSmb,$smbToGive"

        val file = File(path, "AAPS/oapsaimiHB_records.csv")
        if (!file.exists()) {
            file.createNewFile()
            file.appendText(headerRow)
        }
        file.appendText(valuesToRecord + "\n")
    }
    private fun applySafetyPrecautions(smbToGiveParam: Float1): Float1 {
        var smbToGive = smbToGiveParam

        // Vérifier les conditions de sécurité critiques
        if (isCriticalSafetyCondition()) {
            return 0.0f  // Arrêt immédiat si une condition de sécurité critique est remplie
        }

        // Ajustements basés sur des conditions spécifiques
        smbToGive = applySpecificAdjustments(smbToGive)

        smbToGive = finalizeSmbToGive(smbToGive)

        // Appliquer les limites maximum
        smbToGive = applyMaxLimits(smbToGive)


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
            result = (DinMaxIob - iob).toFloat()
        }
        // result = (result + mealTrigger).toFloat()
        return result
    }






    // Tarciso start finger stick BG event
    // Check for finger stick BG events in the last 10 minutes
    private fun isBGfingerEvent(): List<TherapyEvent>? {
        val tenMinutesAgo = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)
        }.timeInMillis

        val BGfingerEvent = repository.getTherapyEventDataFromTime(tenMinutesAgo, TherapyEvent.Type.FINGER_STICK_BG_VALUE, true).blockingGet()

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
        ).blockingGet()
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
        ).blockingGet()
        // Verifica se houve eventos de mudança de sensor nas últimas 24 horas
        return NoteEvents
    }

    private fun isNoteEventok(): List<TherapyEvent>? {
        val twentyfourHoursAgo = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15)
            //timeInMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        }.timeInMillis
        // Busca eventos do tipo SENSOR_OK nas últimas 24 horas
        var NoteEvents = repository.getTherapyEventDataFromTime(
            twentyfourHoursAgo,
            TherapyEvent.Type.NOTE,
            true
        ).blockingGet()
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
        val predictionhipo = hipoTrigger == 1.0 && predictedBg < targetBg && delta <= sp.getDouble(R.string.key_openapsaimi_power_hipo, 110.0) //Hipo
        val prediction = hipoTrigger == 0.0 && predictedBg < targetBg && delta < sp.getDouble(R.string.key_openapsaimi_power_std, 110.0) // Standard
        //val predictionhipo2 = hipoTrigger > 0 && predictedBg < targetBg && delta <= 1.5 && bg <= 140
        val interval = predictedBg < targetBg && delta > 10 && iob >= maxSMB && lastsmbtime < 10
        val targetinterval = targetBg >= 130 && delta > 0 && iob >= maxSMB && lastsmbtime < 15

        // Tarciso: New trigger: Between 11:00 PM and 05:00 AM, and delta greater than 15
        val nightTrigger = LocalTime.now().run { (hour in 23..23 || hour in 0..4) } && delta > 15 && cob == 0.0

        // Tarciso: New trigger: avoid SMB after calibration with delta < 3
        // val isNewCalibration = XdripCalibration.toString().contains("FINGER_STICK_BG_VALUE", ignoreCase = true) && delta > 1

        return belowMinThreshold || belowTargetAndDropping || belowTargetAndStableButNoCob ||
            droppingFast || droppingFastAtHigh || droppingVeryFast || prediction || interval || targetinterval ||
            // droppingFast || droppingFastAtHigh || droppingVeryFast || targetinterval || dropping ||
             nightTrigger  || predictionhipo  //|| predictionhipo2// || isNewCalibration // Tarciso: Trigger added for the specified conditions

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

    private fun roundToPoint001(number: Float1): Float1 {
        return (number * 1000.0).roundToInt() / 1000.0f
    }

    private fun calculateSMBFromModel(): Float1 {
        val selectedModelFile: File?
        val modelInputs: FloatArray

        when {
            modelHBFile.exists() -> {
                selectedModelFile = modelHBFile
                modelInputs = floatArrayOf(
                    hourOfDay.toFloat(), weekend.toFloat(),
                    bg.toFloat(), targetBg.toFloat(), iob.toFloat(), delta.toFloat(), shortAvgDelta.toFloat(), longAvgDelta.toFloat(),
                    tdd7DaysPerHour.toFloat(), tdd2DaysPerHour.toFloat(), tddPerHour.toFloat(), tdd24HrsPerHour.toFloat(), averageBeatsPerMinute.toFloat()
                )
            }

            modelFile.exists()   -> {
                selectedModelFile = modelFile
                modelInputs = floatArrayOf(
                    hourOfDay.toFloat(), weekend.toFloat(),
                    bg.toFloat(), targetBg.toFloat(), iob.toFloat(), delta.toFloat(), shortAvgDelta.toFloat(), longAvgDelta.toFloat(),
                    tdd7DaysPerHour.toFloat(), tdd2DaysPerHour.toFloat(), tddPerHour.toFloat(), tdd24HrsPerHour.toFloat()
                )
            }

            else                 -> {
                aapsLogger.error(LTag.APS, "NO Model found at specified location")
                return 0.0F
            }
        }

        val interpreter = Interpreter(selectedModelFile!!)
        val output = arrayOf(floatArrayOf(0.0F))
        interpreter.run(modelInputs, output)
        interpreter.close()
        var smbToGive = output[0][0].toString().replace(',', '.').toDouble()

        val formatter = DecimalFormat("#.####", DecimalFormatSymbols(Locale.US))
        smbToGive = formatter.format(smbToGive).toDouble()
        return smbToGive.toFloat()
    }


    private fun calculateAdjustedDelayFactor(
        bg: Float1, recentSteps180Minutes: Int, averageBeatsPerMinute60: Float1, averageBeatsPerMinute180: Float1
    ): Float1 {
        // Seuil pour une activité physique significative basée sur les étapes
        val stepActivityThreshold = 1500

        // Seuil d'augmentation de la fréquence cardiaque indiquant une activité accrue
        val heartRateIncreaseThreshold = 1.2  // par exemple, une augmentation de 20%

        // Seuil à partir duquel l'efficacité de l'insuline commence à diminuer
        val insulinSensitivityDecreaseThreshold = 1.5 * normalBgThreshold

        // Déterminer si une activité physique significative a eu lieu
        val increasedPhysicalActivity = recentSteps180Minutes > stepActivityThreshold

        // Calculer le changement relatif de la fréquence cardiaque
        val heartRateChange = averageBeatsPerMinute60 / averageBeatsPerMinute180

        // Indicateur d'une augmentation possible de la fréquence cardiaque due à l'exercice
        val increasedHeartRateActivity = heartRateChange >= heartRateIncreaseThreshold

        // Calculer le facteur de base avant de prendre en compte l'activité physique
        val baseFactor = when {
            bg <= normalBgThreshold -> 1f
            bg <= insulinSensitivityDecreaseThreshold -> 1f - ((bg - normalBgThreshold) / (insulinSensitivityDecreaseThreshold - normalBgThreshold))
            else -> 0.5f // Arbitraire, à ajuster en fonction de la physiologie individuelle
        }

        // Si une activité physique est détectée (soit par les étapes, soit par la fréquence cardiaque),
        // nous ajustons le facteur de retard pour augmenter la sensibilité à l'insuline.
        return if (increasedPhysicalActivity || increasedHeartRateActivity) {
            (baseFactor.toFloat() * 0.8f).coerceAtLeast(0.5f)  // Ici, nous utilisons 0.8f pour indiquer qu'il s'agit d'un Float
        } else {
            baseFactor.toFloat()  // Cela devrait déjà être un Float
        }
    }


    private fun calculateInsulinEffect(
        bg: Float1,
        iob: Float1,
        variableSensitivity: Float1,
        cob: Float1,
        normalBgThreshold: Float1,
        recentSteps180Min: Int,
        averageBeatsPerMinute60: Float1,
        averageBeatsPerMinute180: Float1
    ): Float1 {
        // Calculer l'effet initial de l'insuline
        var insulinEffect = iob * variableSensitivity

        // Si des glucides sont présents, nous pourrions vouloir ajuster l'effet de l'insuline pour tenir compte de l'absorption des glucides.
        // Tarciso original => if (cob > 0), next line changed
        if (lastmealtime < 240 || LocalTime.now().run { hour in 10..20 }) {
            // Ajustement hypothétique basé sur la présence de glucides. Ce facteur doit être déterminé par des tests/logique métier.
            insulinEffect *= 0.85f
        }

        // Calculer le facteur de retard ajusté en fonction de l'activité physique
        val adjustedDelayFactor = calculateAdjustedDelayFactor(
            normalBgThreshold,
            recentSteps180Min,
            averageBeatsPerMinute60,
            averageBeatsPerMinute180
        )

        // Appliquer le facteur de retard ajusté à l'effet de l'insuline
        insulinEffect *= adjustedDelayFactor

        // Tarciso Removi o codigo abaixo como teste do futureBG
       // if (bg > normalBgThreshold) {
            // Si la glycémie est élevée, l'effet de l'insuline peut être différent. Ajustez selon la logique métier/test.
        //    insulinEffect *= 1.1f
       // }

        return insulinEffect
    }


    private fun predictFutureBg(
        bg: Float1,
        iob: Float1,  // Insuline active (IOB)
        variableSensitivity: Float1,  // Facteur de sensibilité à l'insuline (ISF)
        cob: Float1,  // Glucides à bord (COB)
        CI: Float1,  // Rapport insuline/glucides (ICR)
    ): Double {
        // Temps moyen d'absorption des glucides en heures
        val averageCarbAbsorptionTime = 2.5f

        // Convertir le temps d'absorption en minutes pour le calcul
        val absorptionTimeInMinutes = averageCarbAbsorptionTime * 60

        // Calculer l'effet de l'insuline sur la baisse de la glycémie
        val insulinEffect = calculateInsulinEffect(
            bg,
            iob,
            variableSensitivity,
            cob,
            normalBgThreshold.toFloat(),
            recentSteps180Minutes,
            averageBeatsPerMinute60.toFloat(),
            averageBeatsPerMinute180.toFloat()
        )

        // Calculer l'effet des glucides sur l'augmentation de la glycémie
        // en supposant que 'absorptionTime' représente la période de temps pendant laquelle les glucides sont absorbés
        val carbEffect = (cob / absorptionTimeInMinutes) * CI

        // Prédire la glycémie future
        // var futureBg = (bg - insulinEffect + carbEffect) <- Codigo Original

        // Version 171
        // var futureBg = (bg+(delta*1.5))-((iob/1.6)*variableSensitivity)

        // Version 172
        var futureBg = (bg+(delta*1.5))-((iob/1.9)*variableSensitivity)

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
        if (LocalTime.now().run { hour in 6..22 } && bg <= 115 && delta > 0) {
            futureBg = targetBg + 2

        }

        if (LocalTime.now().run { hour in 6..22 } && iob <= 0.5 && delta > 0) {
            futureBg = targetBg + 2

        }

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
        delta: Double,
        morningFactor: Double,
        afternoonFactor: Double,
        eveningFactor: Double
    ): Triple<Double, Double, Double> {

        //val baseAdjustment = ((bg.toDouble() / 200) * (bg.toDouble() / 200)) + (delta.toDouble() / 4)

        // var bgAdjustmentM = baseAdjustment
        // val maxBgAdjustmentM = morningFactor * 1.40
          //hourlyfactor

        // var bgAdjustmentM = ((bg / (bg * 2)) * (bg /((morningFactor*100) * 2) )) + (delta / 25)
        var bgAdjustmentM = (((bg / (bg * 2)) * (bg /((hourlyfactor) * 2) )) + (delta / 25))
        val maxBgAdjustmentM = hourlyfactor * 2.5
        if (bgAdjustmentM > maxBgAdjustmentM) bgAdjustmentM = maxBgAdjustmentM
        if (bgAdjustmentM < 0.2) {
            bgAdjustmentM = 0.2
        } else if (targetBg >= 120) {
            bgAdjustmentM = 0.5
        }

        // var bgAdjustmentA = ((bg / (bg * 2)) * (bg / ((afternoonFactor*100)* 2) )) + (delta / 25)
        var bgAdjustmentA = ((bg / (bg * 2)) * (bg / ((hourlyfactor)* 2) )) + (delta / 25)
        // val maxBgAdjustmentA = afternoonFactor * 2.5
        val maxBgAdjustmentA = hourlyfactor * 2.5
        if (bgAdjustmentA > maxBgAdjustmentA) bgAdjustmentA = maxBgAdjustmentA
        if (bgAdjustmentA < 0.2) {
            bgAdjustmentA = 0.2
        } else if (targetBg >= 120) {
            bgAdjustmentA = 0.5
        }

        var bgAdjustmentE = ((bg / (bg * 2)) * (bg / ((hourlyfactor) * 2) )) + (delta / 25)
        val maxBgAdjustmentE = hourlyfactor * 2
        if (bgAdjustmentE > maxBgAdjustmentE) bgAdjustmentE = maxBgAdjustmentE
        if (bgAdjustmentE < 0.2) {
            bgAdjustmentE = 0.2
        } else if (targetBg >= 120) {
            bgAdjustmentE = 0.5
        }

        return Triple(bgAdjustmentM.toDouble(), bgAdjustmentA.toDouble(), bgAdjustmentE.toDouble())
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
        var isfadjust = (0.5 * Math.pow(bg.toDouble() / ((dynISFadjust * 100)/1.34), 1.15)) + (delta.toDouble() / 15)
        val maxdynISFadjust = 2

        if (isfadjust > maxdynISFadjust.toDouble()) {
            isfadjust = maxdynISFadjust.toDouble()
        } else if (isfadjust < 0.2) {
            isfadjust = 0.2
        } else if (targetBg >= 120) {
            isfadjust = dynISFadjust.toDouble()
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

        val iobCalcs = iobCobCalculator.calculateIobFromBolus()

        this.iob = iobCalcs.iob + iobCalcs.basaliob.toFloat()
        // this.iob = iobCalcs.iob.toFloat() + iobCalcs.basaliob.toFloat()
        this.bg = glucoseStatus.glucose
        this.bgTime = glucoseStatus.date
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
        // nowMinutes = round(nowMinutes * 100) / 100  // Arrondi à 2 décimales

        //val circadianSensitivity = (0.00000379 * nowMinutes.pow(5)) -
        //    (0.00016422 * nowMinutes.pow(4)) +
        //    (0.00128081 * nowMinutes.pow(3)) +
        //    (0.02533782 * nowMinutes.pow(2)) -
        //    (0.33275556 * nowMinutes) +
        //    1.38581503

        //val circadianSmb = round(
        //    ((0.00000379 * delta * nowMinutes.pow(5)) -
        //        (0.00016422 * delta * nowMinutes.pow(4)) +
        //        (0.00128081 * delta * nowMinutes.pow(3)) +
        //        (0.02533782 * delta * nowMinutes.pow(2)) -
        //        (0.33275556 * delta * nowMinutes) +
        //        1.38581503) * 100
        // ) / 100  // Arrondi à 2 décimales



        //TARGET BG DEFINITION:

        // New algo tests
        //val bgImpact = 100.0 - ( ( (bg - 90.0) / 3 ) + (delta / 0.5) )

        // if ((LocalTime.now().run { hour in 14..22 }) && bg < 145) {
        // bgImpact = 100.0 -((bg - 90.0)/3)
        // }


        var bgImpact: Double = 0.0
        if(bg >120 && delta > 1){
            bgImpact = 100.0 -( ( (bg - 95.0) /4)+(delta/0.5) )
            targetStatus = " (+)"
        } else {
            bgImpact = 100.0 -((bg - 95.0)/4)
            targetStatus = " (std)"
        }

        this.targetBg = when {
            this.targetBg == 140.0 -> {
                140.0
            }
            this.targetBg == 75.0 -> {
                75.0
            }

            recentSteps5Minutes > 0 && recentSteps30Minutes >= 1500 -> {
                110.0
            }

            recentSteps5Minutes > 0 && recentSteps180Minutes > 2500  -> {
                110.0
            }

            (LocalTime.now().run { hour == 11 }) && bg > 90 -> {
                85.0
            }

            bgImpact > 0 -> {
                bgImpact.coerceIn(85.0, 119.0)
            }


            //bg >= 90 && delta >= 3 -> {
            //    var hyperTarget = kotlin.math.max(85.0, profile.getTargetLowMgdl() - (bg - profile.getTargetLowMgdl()) / 3).roundToInt()
            //    hyperTarget = (hyperTarget * kotlin.math.min(circadianSensitivity, 1.0)).toInt()
            //    kotlin.math.max(hyperTarget, 85).toDouble()
            //}

            //circadianSmb > 0.1 && bg < 95 -> {
            //    val hypoTarget = 100 * kotlin.math.max(1.0, circadianSensitivity)
            //    (hypoTarget + circadianSmb).toDouble()
            //}

            else -> this.targetBg // or any default value you want to assign
        }





        this.accelerating_up = if (delta > 2 && delta - longAvgDelta > 2) 1 else 0
        this.deccelerating_up = if (delta > 0 && (delta < shortAvgDelta || delta < longAvgDelta)) 1 else 0
        this.accelerating_down = if (delta < -2 && delta - longAvgDelta < -2) 1 else 0
        this.deccelerating_down = if (delta < 0 && (delta > shortAvgDelta || delta > longAvgDelta)) 1 else 0
        this.stable = if (delta>-3 && delta<3 && shortAvgDelta>-3 && shortAvgDelta<3 && longAvgDelta>-3 && longAvgDelta<3) 1 else 0
        this.stable2 = if (delta>-2 && delta<3 && shortAvgDelta>-1.5 && shortAvgDelta<3 && longAvgDelta>-1.5 && longAvgDelta<3) 1 else 0
        bgstatus = when {
            accelerating_up == 1  -> 2
            deccelerating_up == 1 -> 1
            stable == 1 -> 0
            deccelerating_down ==1 -> -1
            accelerating_down == 1 -> -2
            else -> 0
        }



        hourlyfactor = when{
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
            else -> 50.0 /100
        }


















        // Tarciso TDD Section Start

        val tdd7P = SafeParse.stringToDouble(sp.getString(R.string.key_tdd7, "35"))
        var tdd7Days = tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        if (tdd7Days == 0.0f || tdd7Days < tdd7P) tdd7Days = tdd7P.toFloat()

        // Tarciso TDD Adjustment based on TIR
        if (lastHourTIRLow > 0) {tdd7Days = (tdd7Days / 1.1).toFloat()
            TDDStatus = "TDD was reduced"
        } else if (currentTIRLow > 20) {tdd7Days = (tdd7Days / 1.09).toFloat()
            TDDStatus = "TDD was reduced"
        }
        if (delta >= 0 && bg > 90 && LocalTime.now().run { hour in 7..20 })  {
            val normalizedTIR = lastHourTIRAbove95.coerceIn(1.0, 30.0) // Limits from 1 to 30.
            var incrementFactor = 1.0f + ((normalizedTIR / 100.0f)/1.4).toFloat()
            if (incrementFactor < 1.1){
                incrementFactor = 1.1f
            }
            tdd7Days = (tdd7Days * incrementFactor).toFloat()
            TDDStatus = "TDD was increased"
        }
        this.tdd7DaysPerHour = tdd7Days.toDouble() / 24

        var tdd2Days = tddCalculator.averageTDD(tddCalculator.calculate(2, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        if (tdd2Days == 0.0f || tdd2Days < (tdd7P/1.82) || tdd2Days > (tdd7P/1.83)) tdd2Days = (tdd7Days/1.82).toFloat()
        this.tdd2DaysPerHour = tdd2Days.toDouble() / 24
        val tddLast4H = tdd2DaysPerHour.toDouble() * 4

        var tddDaily = tddCalculator.averageTDD(tddCalculator.calculate(1, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        if (tddDaily == 0.0f || tddDaily < (tdd7P/1.82) || tddDaily > (tdd7P/1.83)) tddDaily = (tdd7Days/1.82).toFloat()
        this.tddPerHour = tddDaily.toDouble() / 24

        var tdd24Hrs = tddCalculator.calculateDaily(-24, 0)?.totalAmount?.toFloat() ?: 0.0f
        if (tdd24Hrs == 0.0f || tdd24Hrs < (tdd7P/1.82) || tdd24Hrs > (tdd7P/1.83)) tdd24Hrs = (tdd7Days/1.82).toFloat()
        this.tdd24HrsPerHour = tdd24Hrs.toDouble() / 24
        val tddLast8to4H  = tdd24HrsPerHour.toDouble() * 4
        tddBLast4Hrs = (tddCalculator.calculateDaily(-4, 0)?.totalAmount?.toFloat() ?: 0.0f).toDouble()



        val tddWeightedFromLast8H = ((1.4 * tddLast4H) + (0.6 * tddLast8to4H)) * 3
        var tdd = (tddWeightedFromLast8H * 0.60) + (tdd7Days.toDouble() * 0.20) + (tddDaily.toDouble() * 0.20)
        // var tdd = (tddWeightedFromLast8H * 0.33) + (tdd7Days.toDouble() * 0.34) + (tddDaily.toDouble() * 0.33)

        val dynISFadjust = SafeParse.stringToDouble(sp.getString(R.string.key_DynISFAdjust, "120")) / 100.0
        val dynISFadjusthyper = SafeParse.stringToDouble(sp.getString(R.string.key_DynISFAdjusthyper, "150")) / 100.0
        //Tarciso Added line
        adjustDynIsf = adjustFactorsdynisfBasedOnBgAndHypo(dynISFadjust.toFloat()).toDouble()
        // Original line => val adjustDynIsf = adjustFactorsdynisfBasedOnBgAndHypo(lastHourTIRLow.toFloat(), dynISFadjust.toFloat())


        tdd = if (bg > 180) tdd * dynISFadjusthyper else tdd * adjustDynIsf
        // Tarciso Original line below. Changed line above
        // = > Original line => tdd = if (bg > 180) tdd * dynISFadjusthyper else tdd * dynISFadjust


        if (tdd.isInfinite()) {
            tdd = tdd7P
        }
        // TDD Section END



        val insulin = activePlugin.activeInsulin
        val insulinDivisor = when {
            insulin.peak >= 35 -> 55 // lyumjev peak: 45
            insulin.peak > 45  -> 65 // ultra rapid peak: 55
            else               -> 75 // rapid peak: 75

        }

        // this.variableSensitivity = Round.roundTo(1800 / ((tdd/0.98) * (ln((glucoseStatus.glucose / insulinDivisor) + 1))), 0.1)
         this.variableSensitivity = Round.roundTo(1800 / (tdd * (ln((glucoseStatus.glucose / insulinDivisor) + 1))), 0.1)












        // Tarciso DynamicAdjuts. Next line add
        //  chenged the min paramter from 80 to 65. See next as the orinal line and then the changed line
        //this.lastHourTIRLow = tirCalculator.averageTIR(tirCalculator.calculateHour(80.0,140.0))?.belowPct()!!
        // this.lastHourTIRLow = tirCalculator.averageTIR(tirCalculator.calculateHour(65.0,140.0))?.belowPct()!!



        // this.currentTIRLow = tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.belowPct()!!
        // this.currentTIRRange = tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.inRangePct()!!
        // this.currentTIRAbove = tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.abovePct()!!

        val beatsPerMinuteValues: List<Int>
        val beatsPerMinuteValues60: List<Int>
        val beatsPerMinuteValues180: List<Int>
        val timeMillisNow = System.currentTimeMillis()
        val timeMillis5 = System.currentTimeMillis() - 5 * 60 * 1000 // 5 minutes en millisecondes
        val timeMillis10 = System.currentTimeMillis() - 10 * 60 * 1000 // 10 minutes en millisecondes
        val timeMillis15 = System.currentTimeMillis() - 15 * 60 * 1000 // 15 minutes en millisecondes
        val timeMillis30 = System.currentTimeMillis() - 30 * 60 * 1000 // 30 minutes en millisecondes
        val timeMillis60 = System.currentTimeMillis() - 60 * 60 * 1000 // 60 minutes en millisecondes
        val timeMillis180 = System.currentTimeMillis() - 180 * 60 * 1000 // 180 minutes en millisecondes
        val stepsCountList5 = repository.getLastStepsCountFromTimeToTime(timeMillis5, timeMillisNow)
        val stepsCount5 = stepsCountList5?.steps5min ?: 0

        val stepsCountList10 = repository.getLastStepsCountFromTimeToTime(timeMillis10, timeMillisNow)
        val stepsCount10 = stepsCountList10?.steps10min ?: 0

        val stepsCountList15 = repository.getLastStepsCountFromTimeToTime(timeMillis15, timeMillisNow)
        val stepsCount15 = stepsCountList15?.steps15min ?: 0

        val stepsCountList30 = repository.getLastStepsCountFromTimeToTime(timeMillis30, timeMillisNow)
        val stepsCount30 = stepsCountList30?.steps30min ?: 0

        val stepsCountList60 = repository.getLastStepsCountFromTimeToTime(timeMillis60, timeMillisNow)
        val stepsCount60 = stepsCountList60?.steps60min ?: 0

        val stepsCountList180 = repository.getLastStepsCountFromTimeToTime(timeMillis180, timeMillisNow)
        val stepsCount180 = stepsCountList180?.steps180min ?: 0
        if (sp.getBoolean(R.string.count_steps_watch, false)===true) {
            this.recentSteps5Minutes = stepsCount5
            this.recentSteps10Minutes = stepsCount10
            this.recentSteps15Minutes = stepsCount15
            this.recentSteps30Minutes = stepsCount30
            this.recentSteps60Minutes = stepsCount60
            this.recentSteps180Minutes = stepsCount180
        }else{
            this.recentSteps5Minutes = StepService.getRecentStepCount5Min()
            this.recentSteps10Minutes = StepService.getRecentStepCount10Min()
            this.recentSteps15Minutes = StepService.getRecentStepCount15Min()
            this.recentSteps30Minutes = StepService.getRecentStepCount30Min()
            this.recentSteps60Minutes = StepService.getRecentStepCount60Min()
            this.recentSteps180Minutes = StepService.getRecentStepCount180Min()
        }
        try {
            val heartRates = repository.getHeartRatesFromTimeToTime(timeMillis5,timeMillisNow)
            beatsPerMinuteValues = heartRates.map { it.beatsPerMinute.toInt() } // Extract beatsPerMinute values from heartRates
            this.averageBeatsPerMinute = if (beatsPerMinuteValues.isNotEmpty()) {
                beatsPerMinuteValues.average()
            } else {
                80.0 // or some other default value
            }

        } catch (e: Exception) {
            // Log that watch is not connected
            //beatsPerMinuteValues = listOf(80)
            this.averageBeatsPerMinute = 80.0
        }
        try {
            val heartRates = repository.getHeartRatesFromTimeToTime(timeMillis60,timeMillisNow)
            beatsPerMinuteValues60 = heartRates.map { it.beatsPerMinute.toInt() } // Extract beatsPerMinute values from heartRates
            this.averageBeatsPerMinute60 = if (beatsPerMinuteValues60.isNotEmpty()) {
                beatsPerMinuteValues60.average()
            } else {
                80.0 // or some other default value
            }

        } catch (e: Exception) {
            // Log that watch is not connected
            //beatsPerMinuteValues = listOf(80)
            this.averageBeatsPerMinute = 80.0
        }
        try {

            val heartRates180 = repository.getHeartRatesFromTimeToTime(timeMillis180,timeMillisNow)
            beatsPerMinuteValues180 = heartRates180.map { it.beatsPerMinute.toInt() } // Extract beatsPerMinute values from heartRates
            this.averageBeatsPerMinute180 = if (beatsPerMinuteValues180.isNotEmpty()) {
                beatsPerMinuteValues180.average()
            } else {
                80.0 // or some other default value
            }

        } catch (e: Exception) {
            // Log that watch is not connected
            //beatsPerMinuteValues180 = listOf(80)
            this.averageBeatsPerMinute180 = 80.0
        }
        if (tdd2Days != null && tdd2Days != 0.0f) {
            this.basalaimi = (tdd2Days / SafeParse.stringToDouble(sp.getString(R.string.key_aimiweight, "50")))
        } else {
            this.basalaimi = (tdd7P / SafeParse.stringToDouble(sp.getString(R.string.key_aimiweight, "50")))
        }
        if (tdd2Days != null && tdd2Days != 0.0f) {
            this.CI = (450 / tdd2Days).toDouble()
        } else {

            this.CI = (450 / tdd7P)
        }

        val choKey = SafeParse.stringToDouble(sp.getString(R.string.key_cho, "50"))
        if (CI != 0.0 && CI != Double.POSITIVE_INFINITY && CI != Double.NEGATIVE_INFINITY) {
            this.aimilimit = (choKey / CI)
        } else {
            this.aimilimit = (choKey / profile.getIc())
        }
        val timenow = LocalTime.now()
        val sixAM = LocalTime.of(6, 0)
        if (averageBeatsPerMinute != 0.0) {
            this.basalaimi = when {
                averageBeatsPerMinute >= averageBeatsPerMinute180 && recentSteps5Minutes > 100 && recentSteps10Minutes > 200 -> (basalaimi * 0.65)
                averageBeatsPerMinute180 != 80.0 && averageBeatsPerMinute > averageBeatsPerMinute180 && bg >= 130 && recentSteps10Minutes === 0 && timenow > sixAM -> (basalaimi * 1.3)
                averageBeatsPerMinute180 != 80.0 && averageBeatsPerMinute < averageBeatsPerMinute180 && recentSteps10Minutes === 0 && bg >= 110 -> (basalaimi * 1.2)
                else -> basalaimi
            }
        }

        this.b30upperbg = SafeParse.stringToDouble(sp.getString(R.string.key_B30_upperBG, "130"))
        this.b30upperdelta = SafeParse.stringToDouble(sp.getString(R.string.key_B30_upperdelta, "10"))
        val b30duration = SafeParse.stringToDouble(sp.getString(R.string.key_B30_duration, "20"))

        this.basalSMB = (((basalaimi * delta) / 60) * b30duration)

        if (delta < b30upperdelta && delta > 1 && bg < b30upperbg && lastsmbtime > 20) {
            this.basaloapsaimirate = basalSMB.toDouble()
        }else if (predictedBg > targetBg && bg > targetBg && lastsmbtime > 20){
            this.basaloapsaimirate = basalSMB.toDouble()
        }else{
            this.basaloapsaimirate = 0.0
        }

        //val variableSensitivityDouble = variableSensitivity.toDoubleSafely()
        //if (variableSensitivityDouble != null) {
        //    if (recentSteps5Minutes > 100 && recentSteps10Minutes > 200 && bg < 130 && delta < 10|| recentSteps180Minutes > 1500 && bg < 130 && delta < 10) variableSensitivity *= 1.5f
        //    if (recentSteps30Minutes > 500 && recentSteps5Minutes >= 0 && recentSteps5Minutes < 100 && bg < 130 && delta < 10) variableSensitivity *= 1.3f

    //} else {
    //    variableSensitivity = profile.getIsfMgdl().toFloat()
    //}
        val getlastBolusSMB = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.SMB).blockingGet()
        val lastBolusSMBTime = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.timestamp else 0L
        this.lastsmbtime = ((now - lastBolusSMBTime) / (60 * 1000)).toDouble().roundToInt().toLong().toInt()

        // Tarciso Meal Mode.
        val getlastBolusMEAL = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL).blockingGet()
        val lastBolusMEALTime = if (getlastBolusMEAL is ValueWrapper.Existing) getlastBolusMEAL.value.timestamp else 0L
        this.lastmealtime = ((now - lastBolusMEALTime) / (60 * 1000)).toDouble().roundToInt().toLong().toInt()


        // Platto Solution
        //var fakeDelta = 0.0
        //if (lastHourTIRabove140 > 50 && delta >= 0.5){
        //    fakeDelta =(lastHourTIRabove140/150).coerceIn(0.01, 0.10)
        //}



        // Tarciso Changing MAXIOB to Profile IC
        // this.maxIob = sp.getDouble(R.string.key_openapssmb_max_iob, 5.0).toFloat()
        // this.maxSMB = sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0).toFloat()

        //MAXIOB Definition. -> Phase1
        // this.maxIob = (bg/100)+(delta/70)+ sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)

        if (targetBg < 120) {
               if ((LocalTime.now().run { hour in 12..22 }) && bg < 145){
                   //Normal MaxIOB -> Phase1 (reduced from 14hs) + Phase2 + Phase3
                   this.maxIob = ((bg/(10*hourOfDay)) + (delta/70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
                } else {
                    //Normal MaxIOB -> Phase1 (no reduction) + Phase2 + Phase3
                    this.maxIob = ((bg/130) + (delta/70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
                }

        } else {
            // Hipo MaxIob -> Phase1
            this.maxIob = ((bg/95) + (delta/70)) + sp.getDouble(R.string.key_openapssmb_max_iob, 5.0)
        }





















        MaxIobProfround = ((this.maxIob * 100).roundToInt() / 100.0)

        this.maxSMBTEST = (maxIobTEST/2.2)+ sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0)


        //MAXSMB Definition -> Phase1
        if (targetBg < 79 && delta >=0) {
            this.maxSMB = (this.maxIob / 1.8) + (sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0))
            this.maxSMBTEST = (profile.getIc().toFloat() / 1.2).toFloat() + sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0)
            maxSMB2 = this.maxSMB
            // this.profile.put("Meal Time", "Value was increased (maxiob = $maxIob) (maxsmb = $maxSMB)")
        } else {
            this.maxSMB = (this.maxIob / 2.2) + (sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0))
            this.maxSMBTEST = (profile.getIc().toFloat() / 2.5).toFloat() + sp.getDouble(R.string.key_openapsaimi_max_smb, 1.0)
            maxSMB2 = this.maxSMB
        // this.profile.put("Meal interval Protection", "Value was reduced (maxsmb = $maxSMB)")
        }

     

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
            variableSensitivity = profile.getIsfMgdl()
             }




        // ********************************************************************
        // ************           IMPORTANT - WARNING            **************
        // ********************************************************************
        // DELTA LOCK

        // Tarciso Add chnage to variableSensitivity. This is the next line. Id delta <0 then variableSensitivity = (Profile.ISF * TDD)
        // if (delta <0) variableSensitivity = (1800 / ((tdd.toDouble()/8) * (ln((glucoseStatus.glucose / insulinDivisor) + 1)))).toFloat()


        // This is a important safety lock. If delta is < than this condition no SMB will be delivered.
        // if (delta < 0) variableSensitivity = profile.getIsfMgdl() * (tdd.toFloat()/6)


        if (delta <= -1.3) variableSensitivity = profile.getIsfMgdl() * (tdd.toFloat()/6)

        // END OF DELTA LOCK






            this.predictedBg = predictFutureBg(bg.toFloat(), iob.toFloat(), variableSensitivity.toFloat(), cob.toFloat(), CI.toFloat())
        // Tarciso Next line added to test Profile ISF
        ProfileISF = profile.getIsfMgdl()

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
        if (SensorChange.toString().contains("SENSOR_CHANGE")) {
            this.profile.put("sensor_change", "New sensor, MaxIob, MaxSMB reduced by 25%. Add new note with SENSOR_OK to restore values")
        }

        TherapyNote = isNoteEvent()
        if (TherapyNote.toString().contains("SENSOR_OK", ignoreCase = true)) {
            this.profile.put("sensor_change", "Sensor OK, MaxIob and MaxSMB restored to preferences value.")
        }


        BGfinger = isBGfingerEvent()
        if (BGfinger.toString().contains("FINGER_STICK_BG_VALUE", ignoreCase = true)) {
            this.profile.put("BGfinger", "Finger Stick BG.")
            this.profile.put("BGfinger", "$BGfinger")
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
        notes.replace(","," ")
        notes.replace("."," ")
        notes.replace("!"," ")
        notes.replace("a"," ")
        notes.replace("an"," ")
        notes.replace("and"," ")
        notes.replace("\\s+"," ")
        return notes
    }

    init {
        injector.androidInjector().inject(this)
    }
}


