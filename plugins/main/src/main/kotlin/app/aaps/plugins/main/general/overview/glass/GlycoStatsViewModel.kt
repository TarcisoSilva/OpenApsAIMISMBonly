package app.aaps.plugins.main.general.overview.glass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.TotalDailyDose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

enum class TirTarget(val label: String, val low: Double, val high: Double) {
    TIGHT("70-140", 70.0, 140.0),
    STANDARD("70-180", 70.0, 180.0)
}

data class DayTddRecord(
    val timestamp: Long,
    val dayName: String,
    val dateLabel: String,
    val basal: Float,
    val bolus: Float,
    val isToday: Boolean = false
) {
    val total: Float get() = basal + bolus
}

data class DayTirRecord(
    val timestamp: Long,
    val dayName: String,
    val dateLabel: String,
    val belowPercent: Int,
    val inRangePercent: Int,
    val abovePercent: Int,
    val isToday: Boolean = false
) {
    fun formattedTimeInRange(): String {
        val totalMinutes = ((inRangePercent / 100f) * 1440).roundToInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours == 0) "${minutes}m" else if (minutes == 0) "${hours}h" else "${hours}h ${minutes.toString().padStart(2, '0')}m"
    }

    fun formattedTimeLow(): String {
        val totalMinutes = ((belowPercent / 100f) * 1440).roundToInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours == 0) "${minutes}m" else "${hours}h ${minutes}m"
    }

    fun formattedTimeHigh(): String {
        val totalMinutes = ((abovePercent / 100f) * 1440).roundToInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours == 0) "${minutes}m" else "${hours}h ${minutes}m"
    }
}

data class GlycoStatsUiState(
    val tddDays: List<DayTddRecord> = emptyList(),
    val tirDays: List<DayTirRecord> = emptyList(),
    val selectedDayIndex: Int = 6,
    val tirTarget: TirTarget = TirTarget.STANDARD,
    val isLoading: Boolean = true
) {
    val selectedTddDay: DayTddRecord? get() = tddDays.getOrNull(selectedDayIndex)
    val selectedTirDay: DayTirRecord? get() = tirDays.getOrNull(selectedDayIndex)
    val averageTdd: Float get() = if (tddDays.isNotEmpty()) tddDays.map { it.total }.average().toFloat() else 0f
    val averageTir: Int get() = if (tirDays.isNotEmpty()) tirDays.map { it.inRangePercent }.average().roundToInt() else 0

    fun formattedAvgTirTime(): String {
        val totalMinutes = ((averageTir / 100f) * 1440).roundToInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes.toString().padStart(2, '0')}m"
    }
}

class GlycoStatsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GlycoStatsUiState())
    val uiState: StateFlow<GlycoStatsUiState> = _uiState.asStateFlow()

    private val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val dayNamesFull = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    // Referências para recálculo
    private var tirCalculator: TirCalculator? = null

    fun loadData(tddCalculator: TddCalculator, tirCalculator: TirCalculator, dateUtil: DateUtil) {
        this.tirCalculator = tirCalculator
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val tddDays = withContext(Dispatchers.IO) {
                calculateTddDays(tddCalculator, dateUtil)
            }

            val tirDays = withContext(Dispatchers.IO) {
                calculateTirDays(tirCalculator, TirTarget.STANDARD)
            }

            _uiState.update {
                it.copy(
                    tddDays = tddDays,
                    tirDays = tirDays,
                    selectedDayIndex = 6, // Today
                    isLoading = false
                )
            }
        }
    }

    private fun calculateTddDays(tddCalculator: TddCalculator, dateUtil: DateUtil): List<DayTddRecord> {
        val result = mutableListOf<DayTddRecord>()
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis

        // Calcula TDD para os últimos 7 dias
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis

            val tdd = try {
                tddCalculator.calculate(dayStart, dayEnd, true)
            } catch (e: Exception) {
                null
            }

            val basal = tdd?.basalAmount?.toFloat() ?: 0f
            val bolus = tdd?.bolusAmount?.toFloat() ?: 0f

            val dayCal = Calendar.getInstance()
            dayCal.timeInMillis = dayStart
            val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Dom, 1=Seg, ...

            val isToday = isSameDay(dayStart, today)
            val dayName = if (isToday) "Today" else dayNames[dayOfWeek]
            val dateLabel = if (isToday) "Today" else "${dayNamesFull[dayOfWeek]}, ${String.format("%02d/%02d/%d", dayCal.get(Calendar.DAY_OF_MONTH), dayCal.get(Calendar.MONTH) + 1, dayCal.get(Calendar.YEAR))}"

            result.add(
                DayTddRecord(
                    timestamp = dayStart,
                    dayName = dayName,
                    dateLabel = dateLabel,
                    basal = basal,
                    bolus = bolus,
                    isToday = isToday
                )
            )
        }
        return result
    }

    private fun calculateTirDays(tirCalculator: TirCalculator, target: TirTarget): List<DayTirRecord> {
        val result = mutableListOf<DayTirRecord>()
        val today = Calendar.getInstance().timeInMillis

        // Calcula TIR para os últimos 7 dias completos (não inclui hoje)
        val tirData = try {
            tirCalculator.calculate(7, target.low, target.high)
        } catch (e: Exception) {
            null
        }

        // Calcula TIR para hoje separadamente (dados parciais do dia)
        val tirToday = try {
            tirCalculator.calculateDaily(target.low, target.high)
        } catch (e: Exception) {
            null
        }

        // Para cada dia dos últimos 7 dias
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            val isToday = isSameDay(dayStart, today)

            // Para hoje, usa calculateDaily; para outros dias, usa calculate(7)
            val tir = if (isToday) {
                tirToday?.get(dayStart)
            } else {
                tirData?.get(dayStart)
            }

            val below = tir?.belowPct()?.roundToInt() ?: 0
            val inRange = tir?.inRangePct()?.roundToInt() ?: 0
            val above = tir?.abovePct()?.roundToInt() ?: 0

            val dayCal = Calendar.getInstance()
            dayCal.timeInMillis = dayStart
            val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK) - 1

            val dayName = if (isToday) "Today" else dayNames[dayOfWeek]
            val dateLabel = if (isToday) "Today" else "${dayNamesFull[dayOfWeek]}, ${String.format("%02d/%02d/%d", dayCal.get(Calendar.DAY_OF_MONTH), dayCal.get(Calendar.MONTH) + 1, dayCal.get(Calendar.YEAR))}"

            result.add(
                DayTirRecord(
                    timestamp = dayStart,
                    dayName = dayName,
                    dateLabel = dateLabel,
                    belowPercent = below,
                    inRangePercent = inRange,
                    abovePercent = above,
                    isToday = isToday
                )
            )
        }
        return result
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun selectDay(dayIndex: Int) {
        _uiState.update { it.copy(selectedDayIndex = dayIndex) }
    }

    fun setTirTarget(target: TirTarget) {
        val currentTarget = _uiState.value.tirTarget
        if (currentTarget == target) return // No change if already same target

        // Recalcula TIR com o novo target
        val calculator = tirCalculator ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, tirTarget = target) }

            val tirDays = withContext(Dispatchers.IO) {
                calculateTirDays(calculator, target)
            }

            _uiState.update {
                it.copy(
                    tirDays = tirDays,
                    isLoading = false
                )
            }
        }
    }
}
