package app.aaps.plugins.main.general.overview.glass

import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.core.main.extensions.fromConstant
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class GlassSensorInsertState(
    val isDarkTheme: Boolean = true,
    val eventTimestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val formattedDate: String = "",
    val formattedTime: String = ""
)

class GlassSensorInsertViewModel : ViewModel() {

    private var repository: AppRepository? = null
    private var dateUtil: DateUtil? = null
    private var uel: UserEntryLogger? = null
    private var aapsLogger: AAPSLogger? = null
    private var profileFunction: ProfileFunction? = null

    private val _uiState = MutableStateFlow(GlassSensorInsertState())
    val uiState: StateFlow<GlassSensorInsertState> = _uiState.asStateFlow()
    private val disposable = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }

    fun init(
        repository: AppRepository,
        dateUtil: DateUtil,
        uel: UserEntryLogger,
        aapsLogger: AAPSLogger,
        profileFunction: ProfileFunction,
        isDark: Boolean
    ) {
        this.repository = repository
        this.dateUtil = dateUtil
        this.uel = uel
        this.aapsLogger = aapsLogger
        this.profileFunction = profileFunction
        _uiState.update {
            it.copy(
                isDarkTheme = isDark,
                eventTimestamp = System.currentTimeMillis()
            ).let { state ->
                state.copy(
                    formattedDate = formatDate(state.eventTimestamp),
                    formattedTime = formatTime(state.eventTimestamp)
                )
            }
        }
    }

    fun setTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.eventTimestamp
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }
        val newTs = cal.timeInMillis
        _uiState.update {
            it.copy(
                eventTimestamp = newTs,
                formattedDate = formatDate(newTs),
                formattedTime = formatTime(newTs)
            )
        }
    }

    fun updateTime(hourOfDay: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.eventTimestamp
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val newTs = cal.timeInMillis
        _uiState.update {
            it.copy(
                eventTimestamp = newTs,
                formattedDate = formatDate(newTs),
                formattedTime = formatTime(newTs)
            )
        }
    }

    fun save() {
        val repo = repository ?: return
        val logger = aapsLogger ?: return
        val entryLogger = uel ?: return
        val pf = profileFunction ?: return

        val state = _uiState.value
        if (state.isSaving || state.isSaved) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        try {
            val eventTimestamp = state.eventTimestamp - (state.eventTimestamp % 1000)

            val therapyEvent = TherapyEvent(
                timestamp = eventTimestamp,
                type = TherapyEvent.Type.SENSOR_CHANGE,
                glucoseUnit = TherapyEvent.GlucoseUnit.fromConstant(pf.getUnits())
            )

            if (state.notes.isNotEmpty()) {
                therapyEvent.note = state.notes
            }

            therapyEvent.enteredBy = "AndroidAPS"

            val valuesWithUnit = mutableListOf<ValueWithUnit?>()
            valuesWithUnit.add(ValueWithUnit.Timestamp(eventTimestamp))
            valuesWithUnit.add(ValueWithUnit.TherapyEventType(therapyEvent.type))
            if (state.notes.isNotEmpty()) {
                valuesWithUnit.add(ValueWithUnit.SimpleString(state.notes))
            }

            disposable += io.reactivex.rxjava3.core.Completable.fromAction {
                repo.runTransaction(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent))
            }
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe(
                    {
                        entryLogger.log(UserEntry.Action.CAREPORTAL, UserEntry.Sources.SensorInsert, state.notes, valuesWithUnit)
                        _uiState.update { it.copy(isSaving = false, isSaved = true) }
                    },
                    { e ->
                        logger.error(LTag.DATABASE, "Error saving sensor insert event", e)
                        _uiState.update { it.copy(isSaving = false, errorMessage = "Error saving: ${e.message}") }
                    }
                )
        } catch (e: Exception) {
            logger.error(LTag.DATABASE, "Error saving sensor insert event", e)
            _uiState.update { it.copy(isSaving = false, errorMessage = "Error saving: ${e.message}") }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(timestamp)
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(timestamp)
    }
}
