package app.aaps.plugins.main.general.overview.glass

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class TreatmentTab(
    val title: String,
    val icon: ImageVector,
    val brandColor: Color
) {
    BOLUS("Bolus", Icons.Default.ArrowDownward, Color(0xFF2563EB)),
    TEMP_BASAL("Temp Basal", Icons.Default.TrendingUp, Color(0xFF9333EA)),
    TEMP_TARGET("Temp Target", Icons.Default.Adjust, Color(0xFFF59E0B)),
    CAREPORTAL("Care Portal", Icons.Default.Edit, Color(0xFF0D9488)),
    PROFILE_SWITCH("Profile", Icons.Default.Person, Color(0xFF9333EA)),
    USER_ENTRY("User Entry", Icons.Default.Security, Color(0xFF2563EB))
}

enum class ActionType {
    BOLUS_SMB,
    MEAL_BOLUS,
    BOLUS,
    TEMP_BASAL,
    TEMP_TARGET,
    CAREPORTAL,
    PROFILE_SWITCH,
    USER_ENTRY,
    NOTE,
    DEVICE_RECONNECTED,
    AUTO_ADJUST,
    ALERT_MONITORING,
    ALERT,
    LOOP_ADJUST,
    BATTERY_REPLACEMENT,
    CANNULA_INFUSOR_CHANGE,
    SENSOR_CHANGE,
    SITE_CHANGE
}

data class GlassTreatmentItem(
    val id: String,
    val databaseId: Long,
    val tab: TreatmentTab,
    val timestamp: Long,
    val formattedTime: String,
    val dateSection: String,
    val title: String,
    val primaryMetric: String? = null,
    val secondaryMetric: String? = null,
    val subtitle: String? = null,
    val actionType: ActionType = ActionType.NOTE,
    val isValid: Boolean = true,
    val bolusTypeLabel: String? = null,
    val iobContrib: Double = 0.0,
    val hasNS: Boolean = false,
    val hasPH: Boolean = false
)

data class GlassTreatmentsState(
    val selectedTab: TreatmentTab = TreatmentTab.BOLUS,
    val items: List<GlassTreatmentItem> = emptyList(),
    val isLoading: Boolean = true,
    val isDarkTheme: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val itemToDelete: GlassTreatmentItem? = null,
    val inspectedItem: GlassTreatmentItem? = null
)
