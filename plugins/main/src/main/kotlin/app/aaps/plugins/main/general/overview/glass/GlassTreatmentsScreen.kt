package app.aaps.plugins.main.general.overview.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTreatmentsScreen(
    uiState: GlassTreatmentsState,
    onSelectTab: (TreatmentTab) -> Unit,
    onBack: () -> Unit,
    onItemLongClick: (GlassTreatmentItem) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onInspectDismiss: () -> Unit,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Cor de fundo sólida (mesma da barra inferior)
    val backgroundColor = if (isDark) Color(0xFF070E1B) else Color(0xFFE8EEF8)
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        GlassTreatmentsHeader(
            isDark = isDark,
            onBack = onBack
        )

        CategoryFilterGrid(
            selectedTab = uiState.selectedTab,
            onTabSelected = onSelectTab,
            isDark = isDark
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                )
            }
        } else {
            val filteredItems = uiState.items.filter { it.tab == uiState.selectedTab }

            val groupedItems = filteredItems.groupBy { it.dateSection }

            val fadeBottomColor = if (isDark) Color(0xFF070E1B) else Color(0xFFE8EEF8)

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedItems.forEach { (dateSection, items) ->
                        item(key = "header_$dateSection") {
                            TimelineDateHeader(
                                dateSection = dateSection,
                                eventCount = items.size,
                                isDark = isDark
                            )
                        }

                        items(
                            items = items,
                            key = { it.id }
                        ) { item ->
                            TreatmentTimelineCard(
                                item = item,
                                isDark = isDark,
                                onLongClick = {
                                    // User Entry items cannot be deleted
                                    if (item.tab != TreatmentTab.USER_ENTRY) {
                                        onItemLongClick(item)
                                    }
                                }
                            )
                        }

                        item(key = "spacer_$dateSection") {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    fadeBottomColor
                                )
                            )
                        )
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (uiState.showDeleteDialog && uiState.itemToDelete != null) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = {
                Text(
                    text = "Delete record?",
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            },
            text = {
                Text(
                    text = "Do you want to permanently delete \"${uiState.itemToDelete.title}\"?",
                    color = textMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = onDeleteConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text("Cancel", color = textMuted)
                }
            },
            containerColor = cardBg
        )
    }

    // Detail Bottom Sheet
    uiState.inspectedItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = onInspectDismiss,
            containerColor = cardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            TreatmentDetailSheet(
                item = item,
                isDark = isDark,
                onDelete = {
                    onInspectDismiss()
                    onItemLongClick(item)
                },
                onClose = onInspectDismiss
            )
        }
    }
}

@Composable
private fun GlassTreatmentsHeader(
    isDark: Boolean,
    onBack: () -> Unit
) {
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Treatments",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun CategoryFilterGrid(
    selectedTab: TreatmentTab,
    onTabSelected: (TreatmentTab) -> Unit,
    isDark: Boolean
) {
    val tabs = listOf(
        TreatmentTab.BOLUS,
        TreatmentTab.TEMP_BASAL,
        TreatmentTab.CAREPORTAL,
        TreatmentTab.TEMP_TARGET,
        TreatmentTab.PROFILE_SWITCH,
        TreatmentTab.USER_ENTRY
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.chunked(3).forEach { rowTabs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowTabs.forEach { tab ->
                    CategoryPillButton(
                        tab = tab,
                        isSelected = selectedTab == tab,
                        isDark = isDark,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPillButton(
    tab: TreatmentTab,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillBg = if (isSelected) {
        tab.brandColor
    } else {
        if (isDark) Color(0xFF1E293B) else Color.White
    }

    val textColor = if (isSelected) {
        Color.White
    } else {
        if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
    }

    val borderColor = if (isSelected) {
        Color.Transparent
    } else {
        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = pillBg,
        border = if (isSelected) null else BorderStroke(1.dp, borderColor),
        shadowElevation = if (isSelected) 2.dp else 1.dp,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White.copy(alpha = 0.3f) else tab.brandColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else tab.brandColor,
                    modifier = Modifier.size(10.dp)
                )
            }

            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = tab.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimelineDateHeader(
    dateSection: String,
    eventCount: Int,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = dateSection.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0).copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$eventCount events",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreatmentTimelineCard(
    item: GlassTreatmentItem,
    isDark: Boolean,
    onLongClick: () -> Unit = {}
) {
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val (iconVector, iconTint, iconBoxBg) = resolveItemVisuals(item.actionType, isDark)

    val isBolus = item.tab == TreatmentTab.BOLUS

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155).copy(alpha = 0.7f) else Color(0xFFE2E8F0).copy(alpha = 0.7f)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            )
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false,
                ambientColor = Color(0xFF0F172A).copy(alpha = 0.04f),
                spotColor = Color(0xFF0F172A).copy(alpha = 0.05f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isBolus) {
                // Bolus-specific layout: 3 lines
                Column(modifier = Modifier.weight(1f)) {
                    // Line 1: Type label + Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.bolusTypeLabel ?: item.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = item.formattedTime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)
                            )
                        }
                    }

                    // Line 2: Dose - IOB
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.primaryMetric ?: "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
                        )
                        if (item.iobContrib > 0.01) {
                            Text(
                                text = "- IOB: ${String.format("%.2f", item.iobContrib)}U",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMuted
                            )
                        }
                    }

                    // Line 3: Sync status
                    if (item.hasNS || item.hasPH) {
                        Spacer(modifier = Modifier.height(3.dp))
                        val syncParts = mutableListOf<String>()
                        if (item.hasNS) syncParts.add("NS")
                        if (item.hasPH) syncParts.add("Pump")
                        Text(
                            text = "Synchronized with ${syncParts.joinToString(" and ")}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Default layout for non-bolus items
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.primaryMetric != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.primaryMetric,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when (item.tab) {
                                    TreatmentTab.BOLUS -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
                                    TreatmentTab.TEMP_BASAL -> if (isDark) Color(0xFFC084FC) else Color(0xFF6B21A8)
                                    else -> if (isDark) Color(0xFF67E8F9) else Color(0xFF0E7490)
                                }
                            )

                            if (item.secondaryMetric != null) {
                                Text(
                                    text = item.secondaryMetric,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textMuted
                                )
                            }
                        }
                    }

                    if (item.subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (item.primaryMetric == null) textColor.copy(alpha = 0.85f) else textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.formattedTime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
private fun TreatmentDetailSheet(
    item: GlassTreatmentItem,
    isDark: Boolean,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "${item.dateSection} at ${item.formattedTime}",
                    fontSize = 13.sp,
                    color = textMuted
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = textMuted)
            }
        }

        if (item.primaryMetric != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Metric: ${item.primaryMetric} ${item.secondaryMetric.orEmpty()}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            )
        }

        if (!item.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.subtitle,
                fontSize = 14.sp,
                color = textColor
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Delete This Record")
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun resolveItemVisuals(
    actionType: ActionType,
    isDark: Boolean
): Triple<ImageVector, Color, Color> {
    return when (actionType) {
        // Bolus SMB - Arrow down (syringe)
        ActionType.BOLUS_SMB -> Triple(
            Icons.Default.ArrowDownward,
            Color(0xFF0D9488),
            if (isDark) Color(0xFF134E4A).copy(alpha = 0.4f) else Color(0xFFF0FDFA)
        )
        // Bolus de Refeição - Restaurant/food
        ActionType.MEAL_BOLUS -> Triple(
            Icons.Default.Restaurant,
            Color(0xFF06B6D4),
            if (isDark) Color(0xFF164E63).copy(alpha = 0.4f) else Color(0xFFECFEFF)
        )
        // Bolus genérico
        ActionType.BOLUS -> Triple(
            Icons.Default.ArrowDownward,
            Color(0xFF0D9488),
            if (isDark) Color(0xFF134E4A).copy(alpha = 0.4f) else Color(0xFFF0FDFA)
        )
        // Temp Basal - Trending up (pulse/wave)
        ActionType.TEMP_BASAL -> Triple(
            Icons.Default.TrendingUp,
            Color(0xFF7C3AED),
            if (isDark) Color(0xFF581C87).copy(alpha = 0.4f) else Color(0xFFFAF5FF)
        )
        // Temp Target - Adjust (target circles)
        ActionType.TEMP_TARGET -> Triple(
            Icons.Default.Adjust,
            Color(0xFFF59E0B),
            if (isDark) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFFFFFBEB)
        )
        // Care Portal - Edit/pencil
        ActionType.CAREPORTAL -> Triple(
            Icons.Default.Edit,
            Color(0xFF0D9488),
            if (isDark) Color(0xFF134E4A).copy(alpha = 0.4f) else Color(0xFFF0FDFA)
        )
        // Profile Switch - Person
        ActionType.PROFILE_SWITCH -> Triple(
            Icons.Default.Person,
            Color(0xFF7C3AED),
            if (isDark) Color(0xFF581C87).copy(alpha = 0.4f) else Color(0xFFFAF5FF)
        )
        // User Entry - Security/shield
        ActionType.USER_ENTRY -> Triple(
            Icons.Default.Security,
            Color(0xFF2563EB),
            if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.4f) else Color(0xFFEFF6FF)
        )
        // Bomba Reconectada - Sync
        ActionType.DEVICE_RECONNECTED -> Triple(
            Icons.Default.Sync,
            Color(0xFF059669),
            if (isDark) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFECFDF5)
        )
        // Auto-Adjust / Proteção - Shield/check
        ActionType.AUTO_ADJUST -> Triple(
            Icons.Default.Shield,
            Color(0xFF2563EB),
            if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.4f) else Color(0xFFEFF6FF)
        )
        // Alerta de Monitoramento - Notifications/bell (amber/warning)
        ActionType.ALERT_MONITORING -> Triple(
            Icons.Default.Notifications,
            Color(0xFFF59E0B),
            if (isDark) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFFFFFBEB)
        )
        // Alert crítico - Notifications/bell (red/alert)
        ActionType.ALERT -> Triple(
            Icons.Default.Warning,
            Color(0xFFEF4444),
            if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.4f) else Color(0xFFFEF2F2)
        )
        // Ajuste Dinâmico do Loop - Tune/settings
        ActionType.LOOP_ADJUST -> Triple(
            Icons.Default.Tune,
            Color(0xFF6366F1),
            if (isDark) Color(0xFF312E81).copy(alpha = 0.4f) else Color(0xFFEEF2FF)
        )
        // Substituição de Bateria - BatteryFull
        ActionType.BATTERY_REPLACEMENT -> Triple(
            Icons.Default.BatteryFull,
            Color(0xFF059669),
            if (isDark) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFECFDF5)
        )
        // Troca de Cânula / Infusor - Science/flask
        ActionType.CANNULA_INFUSOR_CHANGE -> Triple(
            Icons.Default.Science,
            Color(0xFF2563EB),
            if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.4f) else Color(0xFFEFF6FF)
        )
        // Sensor Change - Monitor/sensor
        ActionType.SENSOR_CHANGE -> Triple(
            Icons.Default.Monitor,
            Color(0xFF0D9488),
            if (isDark) Color(0xFF134E4A).copy(alpha = 0.4f) else Color(0xFFF0FDFA)
        )
        // Site Change - Medical services
        ActionType.SITE_CHANGE -> Triple(
            Icons.Default.MedicalServices,
            Color(0xFF0E7490),
            if (isDark) Color(0xFF164E63).copy(alpha = 0.4f) else Color(0xFFECFEFF)
        )
        // Note - Note icon
        ActionType.NOTE -> Triple(
            Icons.Default.Note,
            Color(0xFF64748B),
            if (isDark) Color(0xFF334155).copy(alpha = 0.4f) else Color(0xFFF8FAFC)
        )
    }
}

private fun Modifier.shadow(
    elevation: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
    clip: Boolean = true,
    ambientColor: Color = Color.Black,
    spotColor: Color = Color.Black
): Modifier = this
