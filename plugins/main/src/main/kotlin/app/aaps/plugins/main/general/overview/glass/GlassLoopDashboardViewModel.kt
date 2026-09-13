package app.aaps.plugins.main.general.overview.glass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.logging.AAPSLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class GlassLoopDashboardViewModel : ViewModel() {

    private var activePlugin: ActivePlugin? = null
    private var dateUtil: DateUtil? = null
    private var sp: SP? = null
    private var aapsLogger: AAPSLogger? = null
    private var iobCobCalculator: IobCobCalculator? = null
    private var glucoseStatusProvider: GlucoseStatusProvider? = null

    private val _uiState = MutableStateFlow(GlassLoopDashboardState())
    val uiState: StateFlow<GlassLoopDashboardState> = _uiState.asStateFlow()

    private val _refreshTrigger = Channel<Unit>(Channel.CONFLATED)
    private var lastRefreshTime = 0L
    private val MIN_REFRESH_INTERVAL_MS = 3000L

    fun init(
        activePlugin: ActivePlugin,
        dateUtil: DateUtil,
        sp: SP,
        aapsLogger: AAPSLogger,
        iobCobCalculator: IobCobCalculator,
        glucoseStatusProvider: GlucoseStatusProvider,
        isDark: Boolean
    ) {
        this.activePlugin = activePlugin
        this.dateUtil = dateUtil
        this.sp = sp
        this.aapsLogger = aapsLogger
        this.iobCobCalculator = iobCobCalculator
        this.glucoseStatusProvider = glucoseStatusProvider
        _uiState.update { it.copy(isDarkTheme = isDark) }

        viewModelScope.launch {
            _refreshTrigger.receiveAsFlow()
                .collect {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastRefreshTime
                    if (elapsed < MIN_REFRESH_INTERVAL_MS) {
                        delay(MIN_REFRESH_INTERVAL_MS - elapsed)
                    }
                    lastRefreshTime = System.currentTimeMillis()
                    doRefresh()
                }
        }
    }

    fun refreshData() {
        _refreshTrigger.trySend(Unit)
    }

    private suspend fun doRefresh() {
        val ap = activePlugin ?: return
        val du = dateUtil ?: return
        val prefs = sp ?: return

        _uiState.update { it.copy(isLoading = true) }

        val newState = withContext(Dispatchers.IO) {
            val result = try { ap.activeAPS.lastAPSResult } catch (_: Exception) { null }
            val html = try { result?.toSpanned()?.toString() ?: "" } catch (_: Exception) { "" }

            val smb = result?.smb ?: 0.0
            val targetBg = result?.targetBG ?: 0.0
            val targetProtection = result?.targetProtection ?: 0.0

            val glucoseStatus = try { glucoseStatusProvider?.glucoseStatusData } catch (_: Exception) { null }
            val bg = glucoseStatus?.glucose ?: 0.0
            val delta = glucoseStatus?.delta ?: 0.0
            val shortAvgDelta = glucoseStatus?.shortAvgDelta ?: 0.0
            val longAvgDelta = glucoseStatus?.longAvgDelta ?: 0.0

            val iob = try {
                val iobCalc = iobCobCalculator?.calculateIobFromBolus()
                (iobCalc?.iob ?: 0.0) + (iobCalc?.basaliob ?: 0.0)
            } catch (_: Exception) { 0.0 }

            val tdd7PerHour = if (html.isNotBlank()) parseHtmlTddPerHour(html, "tdd 7d/h") else 0.0

            try {
                if (html.isBlank()) throw IllegalStateException("empty APS html")

                val autoMode = prefs.getBoolean("auto_mode", true)
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
                val tddStatus = parseHtmlKeyValue(html, "TDD Adjust:")
                val tirStatus = parseHtmlKeyValue(html, "BG Adjust:")
                val tirLow1h = parseHtmlPercent(html, "TIR Low:", 0)
                val tirLow24h = parseHtmlPercent(html, "TIR Low:", 1)
                val lowAlarms1h = parseHtmlAlarmCount(html, "Low Glucose Alarms:", 0)
                val lowAlarms24h = parseHtmlAlarmCount(html, "Low Glucose Alarms:", 1)
                val hourOfDay = parseHtmlLabelInt(html, "Hour:")
                val isWeekend = html.contains("Weekend: 1")
                val steps5m = parseHtmlLabelInt(html, "5m Steps:")
                val steps30m = parseHtmlLabelInt(html, "30m Steps:")
                val steps180m = parseHtmlLabelInt(html, "180m Steps:")
                val protectionStr = if (targetProtection > targetBg) "(ON)" else "(OFF)"
                val lastRunTime = if (result?.date ?: 0 > 0) du.timeString(result?.date ?: 0) else ""

                GlassLoopDashboardState(
                    lastRunTime = lastRunTime, autoMode = autoMode, requestedSMB = smb,
                    glucose = bg, delta5m = delta, shortAvgDelta = shortAvgDelta, longAvgDelta = longAvgDelta,
                    iob = iob, targetBg = targetBg, predictedBg = targetProtection, protection = protectionStr,
                    maxIobProfile = maxIobProfile, maxIobDynamic = maxIobDynamic,
                    maxSmbProfile = maxSmbProfile, maxSmbDynamic = maxSmbDynamic,
                    isf = isf, stableBg = stableBg,
                    reactFactorProfile = reactFactorProfile, reactFactorAdjusted = reactFactorAdjusted,
                    hourlyFactorProfile = hourlyFactorProfile, hourlyFactorAdjusted = hourlyFactorAdjusted,
                    tddStatus = tddStatus, tirStatus = tirStatus,
                    tirLow1h = tirLow1h, tirLow24h = tirLow24h,
                    lowGlucoseAlarms1h = lowAlarms1h, lowGlucoseAlarms24h = lowAlarms24h,
                    tdd7DaysPerHour = tdd7PerHour, steps5m = steps5m, steps30m = steps30m,
                    steps180m = steps180m, hourOfDay = hourOfDay, isWeekend = isWeekend,
                    buildVersion = parseHtmlBuildVersion(html), aimiVersion = parseHtmlChgVer(html)
                )
            } catch (e: Exception) {
                aapsLogger?.error("GlassLoopDashboard: Error refreshing data", e)
                val prev = _uiState.value
                prev.copy(
                    autoMode = try { prefs.getBoolean("auto_mode", true) } catch (_: Exception) { prev.autoMode },
                    glucose = bg, delta5m = delta, shortAvgDelta = shortAvgDelta, longAvgDelta = longAvgDelta,
                    iob = iob, targetBg = targetBg, predictedBg = targetProtection,
                    protection = if (targetProtection > targetBg) "(ON)" else "(OFF)",
                    tdd7DaysPerHour = tdd7PerHour, isLoading = false
                )
            }
        }

        _uiState.update {
            newState.copy(isLoading = false)
        }
    }

    private fun parseHtmlBuildVersion(html: String): String {
        return try {
            val line = html.lines().firstOrNull { it.contains("AIMI.1") } ?: return "AIMI.1 ML.2"
            line.substring(line.indexOf("AIMI.1")).substringBefore(",").trim()
                .ifEmpty { "AIMI.1 ML.2" }
        } catch (e: Exception) { "AIMI.1 ML.2" }
    }

    private fun parseHtmlChgVer(html: String): String {
        return try {
            val line = html.lines().firstOrNull { it.contains("Chg. Ver.") } ?: return "Chg. Ver. --"
            line.substring(line.indexOf("Chg. Ver.")).trim()
        } catch (e: Exception) { "Chg. Ver. --" }
    }

    private fun parseIsfStableBg(html: String): Int {        return try {
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

    private fun parseHtmlTddPerHour(html: String, label: String): Double {
        return try {
            val line = html.lines().firstOrNull { it.contains(label) } ?: return 0.0
            val afterLabel = line.substringAfter(label)
            val match = Regex("(\\d+(?:\\.\\d+)?)").find(afterLabel)
            match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }
}
