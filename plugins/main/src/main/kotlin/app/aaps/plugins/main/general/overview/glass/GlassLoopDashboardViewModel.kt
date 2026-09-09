package app.aaps.plugins.main.general.overview.glass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.logging.AAPSLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlassLoopDashboardViewModel : ViewModel() {

    private var activePlugin: ActivePlugin? = null
    private var dateUtil: DateUtil? = null
    private var sp: SP? = null
    private var aapsLogger: AAPSLogger? = null
    private var iobCobCalculator: IobCobCalculator? = null
    private var glucoseStatusProvider: GlucoseStatusProvider? = null
    private var tddCalculator: TddCalculator? = null

    private val _uiState = MutableStateFlow(GlassLoopDashboardState())
    val uiState: StateFlow<GlassLoopDashboardState> = _uiState.asStateFlow()

    fun init(
        activePlugin: ActivePlugin,
        dateUtil: DateUtil,
        sp: SP,
        aapsLogger: AAPSLogger,
        iobCobCalculator: IobCobCalculator,
        glucoseStatusProvider: GlucoseStatusProvider,
        tddCalculator: TddCalculator,
        isDark: Boolean
    ) {
        this.activePlugin = activePlugin
        this.dateUtil = dateUtil
        this.sp = sp
        this.aapsLogger = aapsLogger
        this.iobCobCalculator = iobCobCalculator
        this.glucoseStatusProvider = glucoseStatusProvider
        this.tddCalculator = tddCalculator
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun refreshData() {
        val ap = activePlugin ?: return
        val du = dateUtil ?: return
        val prefs = sp ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val newState = withContext(Dispatchers.IO) {
                try {
                    val result = ap.activeAPS.lastAPSResult
                    val html = result?.toSpanned()?.toString() ?: ""

                    val smb = result?.smb ?: 0.0
                    val targetBg = result?.targetBG ?: 0.0
                    val targetProtection = result?.targetProtection ?: 0.0

                    val glucoseStatus = glucoseStatusProvider?.glucoseStatusData
                    val bg = glucoseStatus?.glucose ?: 0.0
                    val delta = glucoseStatus?.delta ?: 0.0
                    val shortAvgDelta = glucoseStatus?.shortAvgDelta ?: 0.0
                    val longAvgDelta = glucoseStatus?.longAvgDelta ?: 0.0

                    val iobCalc = iobCobCalculator?.calculateIobFromBolus()
                    val iob = (iobCalc?.iob ?: 0.0) + (iobCalc?.basaliob ?: 0.0)

                    val tdd7 = tddCalculator?.calculateDaily(-7, 0)?.totalAmount ?: 0.0
                    val tdd7PerHour = tdd7 / 24.0

                    val autoMode = prefs.getBoolean("auto_mode", true)

                    // Parse constraintStr from HTML
                    val maxIobProfile = parseHtmlDouble(html, "MaxIOB", "->", 0)
                    val maxIobDynamic = parseHtmlDouble(html, "MaxIOB", "->", 1)
                    val maxSmbProfile = parseHtmlDouble(html, "MaxSMB", "->", 0)
                    val maxSmbDynamic = parseHtmlDouble(html, "MaxSMB", "->", 1)
                    val isf = parseHtmlLabelValue(html, "ISF:")
                    val stableBg = parseIsfStableBg(html)
                    val reactFactorProfile = parseArrowValue(html, "React Factor", 0)
                    val reactFactorAdjusted = parseArrowValue(html, "React Factor", 1)
                    val hourlyFactorProfile = parseArrowValue(html, "Hourly Factor", 0)
                    val hourlyFactorAdjusted = parseArrowValue(html, "Hourly Factor", 1)

                    // Parse mealStr
                    val tddStatus = parseHtmlKeyValue(html, "TDD Adjust:")
                    val tirStatus = parseHtmlKeyValue(html, "BG Adjust:")
                    val tirLow1h = parseHtmlPercent(html, "TIR Low:", 0)
                    val tirLow24h = parseHtmlPercent(html, "TIR Low:", 1)
                    val lowAlarms1h = parseHtmlAlarmCount(html, "Low Glucose Alarms:", 0)
                    val lowAlarms24h = parseHtmlAlarmCount(html, "Low Glucose Alarms:", 1)

                    // Parse profileStr for hour/weekend
                    val hourOfDay = parseHtmlLabelInt(html, "Hour:")
                    val isWeekend = html.contains("Weekend: 1")

                    // Parse profileStr
                    val steps5m = parseHtmlLabelInt(html, "5m Steps:")
                    val steps30m = parseHtmlLabelInt(html, "30m Steps:")
                    val steps180m = parseHtmlLabelInt(html, "180m Steps:")

                    val protectionStr = if (targetProtection > 0) "(ON)" else "(OFF)"

                    // Last run time
                    val lastRunTime = if (result?.date ?: 0 > 0) {
                        du.timeString(result?.date ?: 0)
                    } else ""

                    GlassLoopDashboardState(
                        lastRunTime = lastRunTime,
                        autoMode = autoMode,
                        requestedSMB = smb,
                        glucose = bg,
                        delta5m = delta,
                        shortAvgDelta = shortAvgDelta,
                        longAvgDelta = longAvgDelta,
                        iob = iob,
                        targetBg = targetBg,
                        predictedBg = targetProtection,
                        protection = protectionStr,
                        maxIobProfile = maxIobProfile,
                        maxIobDynamic = maxIobDynamic,
                        maxSmbProfile = maxSmbProfile,
                        maxSmbDynamic = maxSmbDynamic,
                        isf = isf,
                        stableBg = stableBg,
                        reactFactorProfile = reactFactorProfile,
                        reactFactorAdjusted = reactFactorAdjusted,
                        hourlyFactorProfile = hourlyFactorProfile,
                        hourlyFactorAdjusted = hourlyFactorAdjusted,
                        tddStatus = tddStatus,
                        tirStatus = tirStatus,
                        tirLow1h = tirLow1h,
                        tirLow24h = tirLow24h,
                        lowGlucoseAlarms1h = lowAlarms1h,
                        lowGlucoseAlarms24h = lowAlarms24h,
                        tdd7DaysPerHour = tdd7PerHour,
                        steps5m = steps5m,
                        steps30m = steps30m,
                        steps180m = steps180m,
                        hourOfDay = hourOfDay,
                        isWeekend = isWeekend,
                        buildVersion = "AIMI.1 ML.2",
                        aimiVersion = "Chg. Ver. 240"
                    )
                } catch (e: Exception) {
                    aapsLogger?.error("GlassLoopDashboard: Error refreshing data", e)
                    _uiState.value
                }
            }

            _uiState.update {
                newState.copy(isLoading = false)
            }
        }
    }

    private fun parseIsfStableBg(html: String): Int {
        return try {
            val line = html.lines().firstOrNull { it.contains("Stable BG:") } ?: return 0
            val match = Regex("Stable BG:\\s*(\\d+)").find(line)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun parseHtmlDouble(html: String, label: String, separator: String, index: Int): Double {
        return try {
            val line = html.lines().firstOrNull { it.contains(label) } ?: return 0.0
            val parts = line.split(separator).map { it.trim().replace(Regex("[^0-9.\\-]"), "") }
            if (parts.size > index + 1) parts[index + 1].toDoubleOrNull() ?: 0.0 else 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun parseHtmlLabelValue(html: String, label: String): Double {
        return try {
            val line = html.lines().firstOrNull { it.contains(label) } ?: return 0.0
            val match = Regex("(\\d+(?:\\.\\d+)?)").find(line)
            match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun parseHtmlLabelInt(html: String, label: String): Int {
        return parseHtmlLabelValue(html, label).toInt()
    }

    private fun parseArrowValue(html: String, label: String, index: Int): Double {
        return try {
            val line = html.lines().firstOrNull { it.contains(label) } ?: return 0.0
            val numbers = Regex("(\\d+(?:\\.\\d+)?)").findAll(line).map { it.value.toDouble() }.toList()
            if (numbers.size > index) numbers[index] else 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun parseHtmlKeyValue(html: String, key: String): String {
        return try {
            val line = html.lines().firstOrNull { it.contains(key) } ?: return ""
            val afterKey = line.substringAfter(key).trim()
            afterKey.split(Regex("[<\\s]")).firstOrNull()?.trim() ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parseHtmlPercent(html: String, label: String, index: Int): Double {
        return try {
            val line = html.lines().firstOrNull { it.contains(label) } ?: return 0.0
            val numbers = Regex("(\\d+(?:\\.\\d+)?)").findAll(line).map { it.value.toDouble() }.toList()
            if (numbers.size > index) numbers[index] else 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun parseHtmlAlarmCount(html: String, label: String, index: Int): Int {
        return parseHtmlPercent(html, label, index).toInt()
    }
}
