package app.aaps.plugins.main.general.overview.glass

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GlassNotificationItem(
    val id: Int,
    val text: String,
    val level: Int,
    val date: Long
)

data class GlassUiState(
    val currentBg: String = "--",
    val rawBg: Float = 0f,
    val delta: Int = 0,
    val trend: String = "→",
    val trendArrowRes: Int = 0,
    val timeAgo: String = "--",
    val unit: String = "mg/dL",
    val sensorReservoir: String = "--",
    val batteryLife: String = "--",
    val sensorLife: String = "--",
    val sensorAge: String = "--",
    val sensorAgeColor: Int = 0xFF94A3B8.toInt(),
    val isLoopActive: Boolean = true,
    val loopStatusText: String = "Loop",
    val loopIconRes: Int = 0,
    val loopTimeRemaining: String = "",
    val iob: Float = 0f,
    val cob: Float = 0f,
    val targetBg: Int = 100,
    val targetText: String = "--",
    val isTempTargetActive: Boolean = false,
    val basalPercent: Int = 0,
    val selectedRangeHours: Int = 6,
    val isDarkMode: Boolean = true,
    val lowLine: Float = 70f,
    val highLine: Float = 180f,
    val bgReadings: List<BgReadingPoint> = emptyList(),
    val iobReadings: List<IobReadingPoint> = emptyList(),
    val treatments: List<TreatmentPoint> = emptyList(),
    val stats: GlycemicStatsData = GlycemicStatsData(),
    val pumpStatus: String = "",
    val notifications: List<GlassNotificationItem> = emptyList()
)

data class BgReadingPoint(val progress: Float, val value: Float)
data class IobReadingPoint(val progress: Float, val iob: Float)
data class TreatmentPoint(val progress: Float, val isCarb: Boolean, val label: String, val timestamp: Long = 0L)

data class GlycemicStatsData(
    val timeInRange: Float = 0f,
    val timeBelow: Float = 0f,
    val timeAbove: Float = 0f,
    val gmi: Float = 0f,
    val cv: Float = 0f,
    val avgBg: Float = 0f
)

class GlassOverviewViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GlassUiState())
    val uiState: StateFlow<GlassUiState> = _uiState.asStateFlow()

    fun initTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkMode = isDark) }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkMode = !it.isDarkMode) }
    }

    fun setTimeRange(hours: Int) {
        _uiState.update { it.copy(selectedRangeHours = hours) }
    }

    fun updateRange(lowLine: Float, highLine: Float) {
        _uiState.update { it.copy(lowLine = lowLine, highLine = highLine) }
    }

    fun updateBg(bg: String, rawBg: Float, delta: Int, trend: String, trendArrowRes: Int, timeAgo: String, unit: String) {
        _uiState.update {
            it.copy(
                currentBg = bg,
                rawBg = rawBg,
                delta = delta,
                trend = trend,
                trendArrowRes = trendArrowRes,
                timeAgo = timeAgo,
                unit = unit
            )
        }
    }

    fun updatePumpAndSensors(
        iob: Float,
        cob: Float,
        reservoir: String,
        battery: String,
        sensorLife: String,
        isLoopActive: Boolean,
        loopStatusText: String = "Loop"
    ) {
        _uiState.update {
            it.copy(
                iob = iob,
                cob = cob,
                sensorReservoir = reservoir,
                batteryLife = battery,
                sensorLife = sensorLife,
                isLoopActive = isLoopActive,
                loopStatusText = loopStatusText
            )
        }
    }

    fun updateBasalAndTarget(basalPercent: Int, targetBg: Int, targetText: String, isTempTargetActive: Boolean) {
        _uiState.update {
            it.copy(
                basalPercent = basalPercent,
                targetBg = targetBg,
                targetText = targetText,
                isTempTargetActive = isTempTargetActive
            )
        }
    }

    fun updatePumpStatus(status: String) {
        _uiState.update { it.copy(pumpStatus = status) }
    }

    fun updateLoopStatus(loopIconRes: Int, loopStatusText: String, loopTimeRemaining: String) {
        _uiState.update {
            it.copy(
                loopIconRes = loopIconRes,
                loopStatusText = loopStatusText,
                loopTimeRemaining = loopTimeRemaining
            )
        }
    }

    fun updateSensorAge(sensorAge: String, sensorAgeColor: Int) {
        _uiState.update {
            it.copy(sensorAge = sensorAge, sensorAgeColor = sensorAgeColor)
        }
    }

    fun updateNotifications(notifications: List<GlassNotificationItem>) {
        _uiState.update { it.copy(notifications = notifications) }
    }

    fun updateGraphData(
        bgReadings: List<BgReadingPoint>,
        iobReadings: List<IobReadingPoint>,
        treatments: List<TreatmentPoint>
    ) {
        _uiState.update {
            it.copy(
                bgReadings = bgReadings,
                iobReadings = iobReadings,
                treatments = treatments
            )
        }
    }
}
