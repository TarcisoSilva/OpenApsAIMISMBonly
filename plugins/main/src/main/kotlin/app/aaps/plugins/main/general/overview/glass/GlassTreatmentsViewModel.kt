package app.aaps.plugins.main.general.overview.glass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.main.extensions.iobCalc
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.database.impl.AppRepository
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.transactions.InvalidateBolusTransaction
import app.aaps.database.impl.transactions.InvalidateCarbsTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryBasalTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryTargetTransaction
import app.aaps.database.impl.transactions.InvalidateTherapyEventTransaction
import app.aaps.database.impl.transactions.InvalidateProfileSwitchTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlassTreatmentsViewModel : ViewModel() {

    private var repository: AppRepository? = null
    private var dateUtil: DateUtil? = null
    private var uel: UserEntryLogger? = null
    private var activePlugin: ActivePlugin? = null
    private var profile: Profile? = null

    private val _uiState = MutableStateFlow(GlassTreatmentsState())
    val uiState: StateFlow<GlassTreatmentsState> = _uiState.asStateFlow()

    private val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000

    fun loadData(repository: AppRepository, dateUtil: DateUtil, uel: UserEntryLogger, activePlugin: ActivePlugin, profile: Profile) {
        this.repository = repository
        this.dateUtil = dateUtil
        this.uel = uel
        this.activePlugin = activePlugin
        this.profile = profile
        loadTreatments()
    }

    fun selectTab(tab: TreatmentTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun promptDelete(item: GlassTreatmentItem) {
        _uiState.update { it.copy(showDeleteDialog = true, itemToDelete = item) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, itemToDelete = null) }
    }

    fun confirmDelete() {
        val state = _uiState.value
        val item = state.itemToDelete ?: return
        val repo = repository ?: return
        val userEntryLogger = uel ?: return

        // User Entry cannot be deleted
        if (item.tab == TreatmentTab.USER_ENTRY) {
            _uiState.update { it.copy(showDeleteDialog = false, itemToDelete = null) }
            return
        }

        viewModelScope.launch {
            // Log the action to UserEntry
            withContext(Dispatchers.IO) {
                try {
                    when (item.tab) {
                        TreatmentTab.BOLUS -> {
                            userEntryLogger.log(
                                UserEntry.Action.BOLUS_REMOVED,
                                UserEntry.Sources.Treatments,
                                ValueWithUnit.Timestamp(item.timestamp),
                                ValueWithUnit.Insulin(item.primaryMetric?.replace(" U", "")?.toDoubleOrNull() ?: 0.0)
                            )
                            repo.runTransactionForResult(InvalidateBolusTransaction(item.databaseId)).blockingGet()
                        }
                        TreatmentTab.CAREPORTAL -> {
                            if (item.id.startsWith("carbs_")) {
                                userEntryLogger.log(
                                    UserEntry.Action.CARBS_REMOVED,
                                    UserEntry.Sources.Treatments,
                                    ValueWithUnit.Timestamp(item.timestamp),
                                    ValueWithUnit.Gram(item.primaryMetric?.replace("g", "")?.toIntOrNull() ?: 0)
                                )
                                repo.runTransactionForResult(InvalidateCarbsTransaction(item.databaseId)).blockingGet()
                            } else {
                                userEntryLogger.log(
                                    UserEntry.Action.CAREPORTAL_REMOVED,
                                    UserEntry.Sources.Treatments,
                                    item.title,
                                    item.timestamp
                                )
                                repo.runTransactionForResult(InvalidateTherapyEventTransaction(item.databaseId)).blockingGet()
                            }
                        }
                        TreatmentTab.TEMP_BASAL -> {
                            userEntryLogger.log(
                                UserEntry.Action.TEMP_BASAL_REMOVED,
                                UserEntry.Sources.Treatments,
                                ValueWithUnit.Timestamp(item.timestamp)
                            )
                            repo.runTransactionForResult(InvalidateTemporaryBasalTransaction(item.databaseId)).blockingGet()
                        }
                        TreatmentTab.TEMP_TARGET -> {
                            userEntryLogger.log(
                                UserEntry.Action.TT_REMOVED,
                                UserEntry.Sources.Treatments,
                                ValueWithUnit.Timestamp(item.timestamp)
                            )
                            repo.runTransactionForResult(InvalidateTemporaryTargetTransaction(item.databaseId)).blockingGet()
                        }
                        TreatmentTab.PROFILE_SWITCH -> {
                            userEntryLogger.log(
                                UserEntry.Action.PROFILE_SWITCH_REMOVED,
                                UserEntry.Sources.Treatments,
                                item.primaryMetric ?: "",
                                item.timestamp
                            )
                            repo.runTransactionForResult(InvalidateProfileSwitchTransaction(item.databaseId)).blockingGet()
                        }
                        TreatmentTab.USER_ENTRY -> { /* Should not reach here */ }
                    }
                } catch (_: Exception) { }
            }

            _uiState.update {
                it.copy(
                    items = it.items.filterNot { it.id == item.id },
                    showDeleteDialog = false,
                    itemToDelete = null
                )
            }
        }
    }

    fun inspectItem(item: GlassTreatmentItem?) {
        _uiState.update { it.copy(inspectedItem = item) }
    }

    private fun loadTreatments() {
        val repo = repository ?: return
        val du = dateUtil ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val items = withContext(Dispatchers.IO) {
                try {
                    val now = System.currentTimeMillis()
                    val fromTime = now - thirtyDaysMillis

                    val boluses = repo.getBolusesDataFromTime(fromTime, false).blockingGet()
                    val carbs = repo.getCarbsDataFromTime(fromTime, false).blockingGet()
                    val tempBasals = repo.getTemporaryBasalsDataFromTime(fromTime, false).blockingGet()
                    val tempTargets = repo.getTemporaryTargetDataFromTime(fromTime, false).blockingGet()
                    val therapyEvents = repo.getTherapyEventDataFromTime(fromTime, false).blockingGet()
                    val profileSwitches = repo.getProfileSwitchDataFromTime(fromTime, false).blockingGet()
                    val userEntries = repo.getUserEntryFilteredDataFromTime(fromTime).blockingGet()

                    val allItems = mutableListOf<GlassTreatmentItem>()

                    boluses.forEach { bolus ->
                        allItems.add(mapBolus(bolus, du))
                    }

                    carbs.forEach { carb ->
                        allItems.add(mapCarbs(carb, du))
                    }

                    tempBasals.forEach { tempBasal ->
                        allItems.add(mapTempBasal(tempBasal, du))
                    }

                    tempTargets.forEach { tempTarget ->
                        allItems.add(mapTempTarget(tempTarget, du))
                    }

                    therapyEvents.forEach { event ->
                        allItems.add(mapTherapyEvent(event, du))
                    }

                    profileSwitches.forEach { profile ->
                        allItems.add(mapProfileSwitch(profile, du))
                    }

                    userEntries.forEach { entry ->
                        allItems.add(mapUserEntry(entry, du))
                    }

                    allItems.sortedByDescending { it.timestamp }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            _uiState.update {
                it.copy(
                    items = items,
                    isLoading = false
                )
            }
        }
    }

    private fun mapBolus(bolus: Bolus, du: DateUtil): GlassTreatmentItem {
        val now = System.currentTimeMillis()
        val isToday = isSameDay(bolus.timestamp, now)
        val isYesterday = isSameDay(bolus.timestamp, now - 86400000L)

        // Bolus type label
        val bolusTypeLabel = when (bolus.type) {
            Bolus.Type.SMB -> "SMB"
            Bolus.Type.NORMAL -> "Meal"
            Bolus.Type.PRIMING -> "Prime"
        }

        // IOB calculation
        val ap = activePlugin
        val prof = profile
        val iobContrib = if (ap != null && prof != null && bolus.isValid && bolus.type != Bolus.Type.PRIMING) {
            try {
                val iob = bolus.iobCalc(ap, now, prof.dia)
                iob.iobContrib
            } catch (_: Exception) { 0.0 }
        } else 0.0

        // NS and PH sync status
        val hasNS = bolus.interfaceIDs.nightscoutId != null
        val hasPH = bolus.interfaceIDs.pumpSerial != null && bolus.interfaceIDs.pumpId != null

        return GlassTreatmentItem(
            id = "bolus_${bolus.id}",
            databaseId = bolus.id,
            tab = TreatmentTab.BOLUS,
            timestamp = bolus.timestamp,
            formattedTime = du.timeString(bolus.timestamp),
            dateSection = dateSectionText(bolus.timestamp, du),
            title = bolusTypeLabel,
            primaryMetric = "${String.format("%.2f", bolus.amount)}U",
            secondaryMetric = if (iobContrib > 0.01) "IOB: ${String.format("%.2f", iobContrib)}U" else null,
            subtitle = null,
            actionType = if (bolus.type == Bolus.Type.SMB) ActionType.BOLUS_SMB else ActionType.MEAL_BOLUS,
            isValid = bolus.isValid,
            bolusTypeLabel = bolusTypeLabel,
            iobContrib = iobContrib,
            hasNS = hasNS,
            hasPH = hasPH
        )
    }

    private fun mapCarbs(carbs: Carbs, du: DateUtil): GlassTreatmentItem {
        return GlassTreatmentItem(
            id = "carbs_${carbs.id}",
            databaseId = carbs.id,
            tab = TreatmentTab.CAREPORTAL,
            timestamp = carbs.timestamp,
            formattedTime = du.timeString(carbs.timestamp),
            dateSection = dateSectionText(carbs.timestamp, du),
            title = "Carbs",
            primaryMetric = "${carbs.amount}g",
            subtitle = carbs.notes,
            actionType = ActionType.CAREPORTAL,
            isValid = carbs.isValid
        )
    }

    private fun mapTempBasal(tempBasal: TemporaryBasal, du: DateUtil): GlassTreatmentItem {
        val rateText = if (tempBasal.isAbsolute) {
            "${tempBasal.rate} U/h"
        } else {
            "${tempBasal.rate}%"
        }

        return GlassTreatmentItem(
            id = "tempbasal_${tempBasal.id}",
            databaseId = tempBasal.id,
            tab = TreatmentTab.TEMP_BASAL,
            timestamp = tempBasal.timestamp,
            formattedTime = du.timeString(tempBasal.timestamp),
            dateSection = dateSectionText(tempBasal.timestamp, du),
            title = "Temp Basal",
            primaryMetric = rateText,
            secondaryMetric = "${tempBasal.duration / 60000} min",
            actionType = ActionType.TEMP_BASAL,
            isValid = tempBasal.isValid
        )
    }

    private fun mapTempTarget(tempTarget: TemporaryTarget, du: DateUtil): GlassTreatmentItem {
        return GlassTreatmentItem(
            id = "temptarget_${tempTarget.id}",
            databaseId = tempTarget.id,
            tab = TreatmentTab.TEMP_TARGET,
            timestamp = tempTarget.timestamp,
            formattedTime = du.timeString(tempTarget.timestamp),
            dateSection = dateSectionText(tempTarget.timestamp, du),
            title = "Temp Target",
            primaryMetric = "${tempTarget.lowTarget} - ${tempTarget.highTarget}",
            secondaryMetric = "${tempTarget.duration / 60000} min",
            actionType = ActionType.TEMP_TARGET,
            isValid = tempTarget.isValid
        )
    }

    private fun mapTherapyEvent(event: TherapyEvent, du: DateUtil): GlassTreatmentItem {
        val actionType = when (event.type) {
            TherapyEvent.Type.CANNULA_CHANGE,
            TherapyEvent.Type.TUBE_CHANGE -> ActionType.CANNULA_INFUSOR_CHANGE
            TherapyEvent.Type.INSULIN_CHANGE -> ActionType.CANNULA_INFUSOR_CHANGE
            TherapyEvent.Type.PUMP_BATTERY_CHANGE -> ActionType.BATTERY_REPLACEMENT
            TherapyEvent.Type.SENSOR_CHANGE,
            TherapyEvent.Type.SENSOR_STARTED,
            TherapyEvent.Type.SENSOR_STOPPED -> ActionType.SENSOR_CHANGE
            TherapyEvent.Type.FINGER_STICK_BG_VALUE -> ActionType.ALERT_MONITORING
            TherapyEvent.Type.EXERCISE,
            TherapyEvent.Type.SICKNESS,
            TherapyEvent.Type.STRESS,
            TherapyEvent.Type.ALCOHOL,
            TherapyEvent.Type.CORTISONE -> ActionType.NOTE
            TherapyEvent.Type.ANNOUNCEMENT -> ActionType.ALERT_MONITORING
            TherapyEvent.Type.QUESTION -> ActionType.NOTE
            TherapyEvent.Type.NOTE -> ActionType.NOTE
            TherapyEvent.Type.APS_OFFLINE -> ActionType.DEVICE_RECONNECTED
            TherapyEvent.Type.DAD_ALERT -> ActionType.ALERT
            TherapyEvent.Type.BATTERY_EMPTY -> ActionType.BATTERY_REPLACEMENT
            TherapyEvent.Type.RESERVOIR_EMPTY -> ActionType.ALERT
            TherapyEvent.Type.OCCLUSION -> ActionType.ALERT
            TherapyEvent.Type.PUMP_STOPPED,
            TherapyEvent.Type.PUMP_STARTED,
            TherapyEvent.Type.PUMP_PAUSED -> ActionType.DEVICE_RECONNECTED
            TherapyEvent.Type.LEAKING_INFUSION_SET -> ActionType.CANNULA_INFUSOR_CHANGE
            else -> ActionType.CAREPORTAL
        }

        return GlassTreatmentItem(
            id = "therapy_${event.id}",
            databaseId = event.id,
            tab = TreatmentTab.CAREPORTAL,
            timestamp = event.timestamp,
            formattedTime = du.timeString(event.timestamp),
            dateSection = dateSectionText(event.timestamp, du),
            title = event.type.text,
            subtitle = event.note,
            primaryMetric = if (event.duration > 0) "${event.duration / 60000} min" else null,
            actionType = actionType,
            isValid = event.isValid
        )
    }

    private fun mapProfileSwitch(profile: ProfileSwitch, du: DateUtil): GlassTreatmentItem {
        return GlassTreatmentItem(
            id = "profile_${profile.id}",
            databaseId = profile.id,
            tab = TreatmentTab.PROFILE_SWITCH,
            timestamp = profile.timestamp,
            formattedTime = du.timeString(profile.timestamp),
            dateSection = dateSectionText(profile.timestamp, du),
            title = "Profile Switch",
            primaryMetric = profile.profileName,
            secondaryMetric = if (profile.duration > 0) "${profile.duration / 60000} min" else null,
            actionType = ActionType.PROFILE_SWITCH,
            isValid = profile.isValid
        )
    }

    private fun mapUserEntry(entry: UserEntry, du: DateUtil): GlassTreatmentItem {
        val now = System.currentTimeMillis()
        val isToday = isSameDay(entry.timestamp, now)
        val isYesterday = isSameDay(entry.timestamp, now - 86400000L)

        val actionText = entry.action.name.replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }

        return GlassTreatmentItem(
            id = "userentry_${entry.id}",
            databaseId = entry.id,
            tab = TreatmentTab.USER_ENTRY,
            timestamp = entry.timestamp,
            formattedTime = du.timeString(entry.timestamp),
            dateSection = dateSectionText(entry.timestamp, du),
            title = actionText,
            subtitle = entry.note.ifBlank { null },
            actionType = ActionType.USER_ENTRY,
            isValid = true
        )
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun dateSectionText(timestamp: Long, du: DateUtil): String {
        val now = System.currentTimeMillis()
        val dateStr = du.dateStringShort(timestamp)
        return when {
            isSameDay(timestamp, now) -> "Today - $dateStr"
            isSameDay(timestamp, now - 86400000L) -> "Yesterday - $dateStr"
            else -> dateStr
        }
    }
}
