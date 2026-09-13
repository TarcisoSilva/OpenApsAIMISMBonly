package app.aaps.plugins.main.general.overview.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun GlassLoopDashboardScreen(
    uiState: GlassLoopDashboardState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    isDark: Boolean
) {
    val isDarkMode = isDark

    val bgColorTop = if (isDarkMode) Color(0xFF070E1B) else Color(0xFFF1F5F9)
    val bgColorBot = if (isDarkMode) Color(0xFF0B1424) else Color(0xFFE8EEF8)
    val cardBgStart = if (isDarkMode) Color(0x24FFFFFF) else Color(0xF5FFFFFF)
    val cardBgEnd = if (isDarkMode) Color(0x0EFFFFFF) else Color(0xE0EEF2FA)
    val borderCard = if (isDarkMode) Color(0x26FFFFFF) else Color(0xB8CBD5E1)
    val textBright = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val cellBg = if (isDarkMode) Color(0x12FFFFFF) else Color(0xFFF1F5F9)
    val cellBorder = if (isDarkMode) Color(0x1AFFFFFF) else Color(0xFFE2E8F0)
    val skyBlue = Color(0xFF38BDF8)
    val emerald = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val coral = Color(0xFFF97316)
    val indigo = Color(0xFF6366F1)
    val iconBgBlue = if (isDarkMode) Color(0xFF1E3A5F) else Color(0xFFDBEAFE)
    val iconBgGreen = if (isDarkMode) Color(0xFF0D3320) else Color(0xFFD1FAE5)
    val iconBgAmber = if (isDarkMode) Color(0xFF3D2E0A) else Color(0xFFFEF3C7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColorTop, bgColorBot, bgColorTop)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // HEADER CARD
            GlassCardInner(isDarkMode, cardBgStart, cardBgEnd, borderCard) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Loop Dashboard", color = textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (uiState.lastRunTime.isNotEmpty()) {
                            Text("Last run: Today at ${uiState.lastRunTime}", color = textMuted, fontSize = 11.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PUMP", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        val smbColor = if (uiState.requestedSMB > 0) emerald else emerald
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(emerald.copy(alpha = 0.12f))
                                .border(1.dp, emerald.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${String.format("%.1f", uiState.requestedSMB)} U", color = emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("requested", color = emerald.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // CARD 1: GLUCOSE & DYNAMIC FACTORS
            SectionCard(
                isDarkMode, cardBgStart, cardBgEnd, borderCard,
                icon = { Icon(Icons.Default.Bolt, null, tint = skyBlue, modifier = Modifier.size(16.dp)) },
                iconBg = iconBgBlue,
                title = "Glucose & Dynamic Factors",
                badge = {
                    val autoModeColor = if (uiState.autoMode) emerald else amber
                    val autoModeText = if (uiState.autoMode) "Auto Mode: ON" else "Auto Mode: OFF"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(autoModeColor.copy(alpha = 0.12f))
                            .border(1.dp, autoModeColor.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(autoModeText, color = autoModeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassCardInner(isDarkMode, cellBg, cellBg, cellBorder, Modifier.weight(1f).fillMaxHeight()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("GLUCOSE", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(String.format("%.0f", uiState.glucose), color = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Text("mg/dL", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val deltaColor = if (uiState.delta5m >= 0) emerald else Color(0xFFEF4444)
                                        Text(String.format("%.2f", uiState.delta5m), color = deltaColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("5m delta", color = textMuted, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                        MetricCell(
                            isDarkMode, cellBg, cellBorder, Modifier.weight(1f).fillMaxHeight(),
                            label = "DELTA (SHORT / LONG)",
                            value = "${String.format("%.2f", uiState.shortAvgDelta)} / ${String.format("%.2f", uiState.longAvgDelta)}",
                            unit = "",
                            valueColor = when {
                                uiState.shortAvgDelta >= 0 && uiState.longAvgDelta >= 0 -> emerald
                                uiState.shortAvgDelta < 0 && uiState.longAvgDelta < 0 -> Color(0xFFEF4444)
                                else -> amber
                            },
                            valueFontSize = 13.sp,
                            subContent = {
                                Text("15m / 40m avg", color = textMuted, fontSize = 9.sp)
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCell(
                            isDarkMode, cellBg, cellBorder, Modifier.weight(1f),
                            label = "IOB",
                            labelColor = indigo,
                            value = String.format("%.2f", uiState.iob),
                            unit = "U",
                            valueColor = indigo,
                            subContent = {
                                Text("Active Insulin", color = textMuted, fontSize = 9.sp)
                            }
                        )
                        MetricCell(
                            isDarkMode, cellBg, cellBorder, Modifier.weight(1f),
                            label = "TARGET & PROTECTION",
                            value = String.format("%.0f", uiState.targetBg),
                            unit = "mg/dL",
                            subContent = {
                                val protColor = if (uiState.protection == "(ON)") emerald else textMuted
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Protection", color = textMuted, fontSize = 9.sp)
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(protColor.copy(alpha = 0.1f)).padding(horizontal = 4.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${String.format("%.0f", uiState.predictedBg)} ${uiState.protection}", color = protColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        )
                    }
                    GlassCardInner(isDarkMode, cellBg, cellBg, cellBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TDD 7 Days", color = textMuted, fontSize = 11.sp)
                            Text(
                                "${String.format("%.2f", uiState.tdd7DaysPerHour)} U/h",
                                color = textBright, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // CARD 2: KEY FACTORS
            SectionCard(
                isDarkMode, cardBgStart, cardBgEnd, borderCard,
                icon = { Icon(Icons.Default.Tune, null, tint = emerald, modifier = Modifier.size(16.dp)) },
                iconBg = iconBgGreen,
                title = "Key Factors",
                badge = {
                    val dayLabel = if (uiState.isWeekend) "Weekend" else "Weekday"
                    val hourLabel = String.format("%02d", uiState.hourOfDay)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (isDarkMode) Color(0x1AFFFFFF) else Color(0xFFE2E8F0))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("${hourLabel}h • $dayLabel", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArrowMetricCell(isDarkMode, cellBg, cellBorder, Modifier.weight(1f), "MAX IOB",
                            from = String.format("%.2f", uiState.maxIobProfile), to = String.format("%.2f", uiState.maxIobDynamic), unit = "U")
                        ArrowMetricCell(isDarkMode, cellBg, cellBorder, Modifier.weight(1f), "MAX SMB",
                            from = String.format("%.2f", uiState.maxSmbProfile), to = String.format("%.2f", uiState.maxSmbDynamic), unit = "U")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCell(
                            isDarkMode, cellBg, cellBorder, Modifier.weight(1f),
                            label = "ISF SENSITIVITY",
                            value = "${uiState.isf.roundToInt()}",
                            unit = "mg/dL/U",
                            valueFontSize = 13.sp
                        )
                        MetricCell(
                            isDarkMode, cellBg, cellBorder, Modifier.weight(1f),
                            label = "STABLE BG",
                            value = "${uiState.stableBg}",
                            unit = if (uiState.stableBg == 0) "Normal" else "",
                            unitColor = if (uiState.stableBg == 0) emerald else textMuted,
                            valueFontSize = 13.sp
                        )
                    }
                    GlassCardInner(isDarkMode, cellBg, cellBg, cellBorder) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ArrowRow("React Factor", uiState.reactFactorProfile.roundToInt().toString(), uiState.reactFactorAdjusted.roundToInt().toString(), textMuted, textBright)
                            ArrowRow("Hourly Factor", uiState.hourlyFactorProfile.roundToInt().toString(), uiState.hourlyFactorAdjusted.roundToInt().toString(), textMuted, textBright)
                        }
                    }
                }
            }

            // CARD 3: SAFETY & ALARMS
            SectionCard(
                isDarkMode, cardBgStart, cardBgEnd, borderCard,
                icon = { Icon(Icons.Default.Shield, null, tint = amber, modifier = Modifier.size(16.dp)) },
                iconBg = iconBgAmber,
                title = "Safety & Alarms"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SafetyRow("BG / TDD Adjust", uiState.tirStatus.ifEmpty { uiState.tddStatus }, textBright)
                    SafetyRow("Low TIR (1h / 24h)", "${String.format("%.0f", uiState.tirLow1h)}% / ${String.format("%.0f", uiState.tirLow24h)}%", emerald)
                    SafetyRow("Low Glucose Alarms (1h / 24h)", "${uiState.lowGlucoseAlarms1h} / ${uiState.lowGlucoseAlarms24h}", emerald)
                    SafetyRow("Recent Activity / Steps", "${uiState.steps5m} steps (5m)", textMuted, showDivider = false)
                }
            }

            // FOOTER
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Requested ${String.format("%.1f", uiState.requestedSMB)}u to pump, ${uiState.buildVersion}, ${uiState.aimiVersion}",
                    color = textMuted, fontSize = 10.sp
                )
                Text("GlycoCalm Intelligent Automated Insulin Delivery System", color = textMuted.copy(alpha = 0.7f), fontSize = 9.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom fade gradient
        val fadeBottomColor = if (isDarkMode) Color(0xFF070E1B) else Color(0xFFE8EEF8)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, fadeBottomColor)
                    )
                )
        )
    }
}

@Composable
private fun SectionCard(
    isDark: Boolean,
    cardBgStart: Color,
    cardBgEnd: Color,
    borderCard: Color,
    icon: @Composable () -> Unit,
    iconBg: Color,
    title: String,
    badge: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    GlassCardInner(isDark, cardBgStart, cardBgEnd, borderCard) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                        icon()
                    }
                    Text(title.uppercase(), color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
                badge?.invoke()
            }
            Divider(color = if (isDark) Color(0x1AFFFFFF) else Color(0xFFE2E8F0), thickness = 0.5.dp)
            content()
        }
    }
}

@Composable
private fun MetricCell(
    isDark: Boolean,
    cellBg: Color,
    cellBorder: Color,
    modifier: Modifier,
    label: String,
    labelColor: Color = Color.Unspecified,
    value: String,
    unit: String = "",
    valueColor: Color = Color.Unspecified,
    unitColor: Color = Color.Unspecified,
    valueFontSize: TextUnit = 20.sp,
    subContent: @Composable (() -> Unit)? = null
) {
    val textBright = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val finalValueColor = if (valueColor != Color.Unspecified) valueColor else textBright
    val finalUnitColor = if (unitColor != Color.Unspecified) unitColor else textMuted

    GlassCardInner(isDark, cellBg, cellBg, cellBorder, modifier) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = if (labelColor != Color.Unspecified) labelColor else textMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value, color = finalValueColor, fontSize = valueFontSize, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) Text(unit, color = finalUnitColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
            subContent?.invoke()
        }
    }
}

@Composable
private fun ArrowMetricCell(
    isDark: Boolean,
    cellBg: Color,
    cellBorder: Color,
    modifier: Modifier,
    label: String,
    from: String,
    to: String,
    unit: String
) {
    val textBright = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val emerald = Color(0xFF10B981)
    val skyBlue = Color(0xFF38BDF8)

    GlassCardInner(isDark, cellBg, cellBg, cellBorder, modifier) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(from, color = textMuted, fontSize = 11.sp)
                Text("→", color = skyBlue, fontSize = 11.sp)
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(emerald.copy(alpha = 0.12f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("$to $unit", color = emerald, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ArrowRow(label: String, from: String, to: String, mutedColor: Color, brightColor: Color) {
    val skyBlue = Color(0xFF38BDF8)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = mutedColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(from, color = mutedColor, fontSize = 11.sp)
            Text("→", color = skyBlue, fontSize = 11.sp)
            Text(to, color = brightColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SafetyRow(label: String, value: String, valueColor: Color, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (showDivider) Divider(color = Color(0x0DFFFFFF), thickness = 0.5.dp)
    }
}
