package app.aaps.plugins.main.general.overview.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ==========================================
// CORES GLASS THEME (compatível com dark/light)
// ==========================================
@Composable
fun statsCardBg(isDark: Boolean): Color = if (isDark) Color(0xE8182038) else Color(0xFFFFFFFF)
@Composable
fun statsBorder(isDark: Boolean): Color = if (isDark) Color(0x38FFFFFF) else Color(0xD0CBD5E1)
@Composable
fun statsTextPrimary(isDark: Boolean): Color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
@Composable
fun statsTextMuted(isDark: Boolean): Color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
@Composable
fun statsBarBg(isDark: Boolean): Color = if (isDark) Color(0x20FFFFFF) else Color(0xFFF1F5F9)

val StatsTeal = Color(0xFF0D9488)
val StatsIndigo = Color(0xFF6366F1)
val StatsEmerald = Color(0xFF10B981)
val StatsAmber = Color(0xFFF59E0B)
val StatsRose = Color(0xFFEF4444)

// ==========================================
// TELA PRINCIPAL DE ESTATÍSTICAS
// ==========================================
@Composable
fun GlycoStatsScreen(
    uiState: GlycoStatsUiState,
    onSelectDay: (Int) -> Unit,
    onSelectTirTarget: (TirTarget) -> Unit,
    onBack: () -> Unit,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val selectedTdd = uiState.selectedTddDay
    val selectedTir = uiState.selectedTirDay

    // Cor de fundo sólida (mesma da barra inferior)
    val backgroundColor = if (isDark) Color(0xFF070E1B) else Color(0xFFE8EEF8)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = StatsTeal)
            }
        } else {
            // Card 1: Dose Diária Total (TDD)
            GlassStatsCard(isDark = isDark) {
                WeeklyTddSection(
                    days = uiState.tddDays,
                    selectedIndex = uiState.selectedDayIndex,
                    avgTdd = uiState.averageTdd,
                    isDark = isDark,
                    onSelectDay = onSelectDay
                )
            }

            // Card 2: Tempo no Alvo (TIR)
            GlassStatsCard(isDark = isDark) {
                WeeklyTirSection(
                    days = uiState.tirDays,
                    selectedIndex = uiState.selectedDayIndex,
                    avgTir = uiState.averageTir,
                    avgTirTime = uiState.formattedAvgTirTime(),
                    tirTarget = uiState.tirTarget,
                    selectedDay = selectedTir,
                    isDark = isDark,
                    onSelectDay = onSelectDay,
                    onSelectTirTarget = onSelectTirTarget
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ==========================================
// CARD GLASS
// ==========================================
@Composable
fun GlassStatsCard(
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val bgBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(Color(0xE8182038), Color(0xF00C1520)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xF8F0F4FA)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    val borderColor = if (isDark) Color(0x38FFFFFF) else Color(0xD8FFFFFF)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 18.dp else 12.dp,
                    shape = shape,
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0x440F172A),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.40f) else Color(0x220F172A)
                )
                .clip(shape)
                .background(bgBrush)
                .border(1.dp, borderColor, shape)
                .padding(16.dp),
            content = content
        )

        // Linha de reflexo specular superior
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(1.dp)
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            if (isDark) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Brilho interno superior (glow)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ==========================================
// CARD 1: DOSE DIÁRIA TOTAL (TDD)
// ==========================================
@Composable
fun WeeklyTddSection(
    days: List<DayTddRecord>,
    selectedIndex: Int,
    avgTdd: Float,
    isDark: Boolean,
    onSelectDay: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Total Daily Dose (TDD)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = statsTextPrimary(isDark)
            )
            Text(
                "Last 7 days • Avg: ${"%.1f".format(avgTdd)} U/day",
                fontSize = 12.sp,
                color = statsTextMuted(isDark)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsLegendTag("Basal", StatsTeal, isDark)
            StatsLegendTag("Bolus", StatsIndigo, isDark)
        }
    }

    Spacer(Modifier.height(18.dp))

    // Gráfico de Barras Basal / Bolus Empilhadas
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        val maxDose = (days.maxOfOrNull { it.total } ?: 30f).coerceAtLeast(10f) * 1.15f
        days.forEachIndexed { index, day ->
            val isSelected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectDay(index) }
                    .then(
                        if (isSelected) {
                            Modifier.background(Color(0xFF0D9488).copy(alpha = 0.12f))
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "${day.total.roundToInt()}U",
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) StatsTeal else statsTextMuted(isDark)
                )
                Spacer(Modifier.height(4.dp))

                // Container da barra
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(statsBarBg(isDark)),
                    contentAlignment = Alignment.Center
                ) {
                    // Barra interna (sempre 10dp de largura)
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(statsBarBg(isDark)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val basalHeightFraction = if (maxDose > 0) (day.basal / maxDose).coerceIn(0f, 1f) else 0f
                        val bolusHeightFraction = if (maxDose > 0) (day.bolus / maxDose).coerceIn(0f, 1f) else 0f

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(bolusHeightFraction)
                                    .background(StatsIndigo)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(basalHeightFraction)
                                    .background(StatsTeal)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    day.dayName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) StatsTeal else statsTextMuted(isDark)
                )
            }
        }
    }
}

// ==========================================
// CARD 2: TEMPO NO ALVO (TIR)
// ==========================================
@Composable
fun WeeklyTirSection(
    days: List<DayTirRecord>,
    selectedIndex: Int,
    avgTir: Int,
    avgTirTime: String,
    tirTarget: TirTarget,
    selectedDay: DayTirRecord?,
    isDark: Boolean,
    onSelectDay: (Int) -> Unit,
    onSelectTirTarget: (TirTarget) -> Unit
) {
    val isTight = tirTarget == TirTarget.TIGHT

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Time in Range (TIR)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = statsTextPrimary(isDark)
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .background(statsBarBg(isDark), RoundedCornerShape(10.dp))
                        .padding(2.dp)
                ) {
                    StatsTirTargetButton(
                        label = "70–140",
                        isSelected = isTight,
                        isDark = isDark,
                        onClick = { onSelectTirTarget(TirTarget.TIGHT) }
                    )
                    StatsTirTargetButton(
                        label = "70–180",
                        isSelected = !isTight,
                        isDark = isDark,
                        onClick = { onSelectTirTarget(TirTarget.STANDARD) }
                    )
                }
            }
        }
        Text(
            "Last 7 days • Avg (${tirTarget.label}): ${avgTir}% ($avgTirTime)",
            fontSize = 12.sp,
            color = statsTextMuted(isDark)
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatsLegendTag("Low", StatsRose, isDark)
                StatsLegendTag("Target", StatsEmerald, isDark)
                StatsLegendTag("High", StatsAmber, isDark)
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    // Gráfico de Barras Empilhadas do TIR
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(165.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectDay(index) }
                    .then(
                        if (isSelected) {
                            Modifier.background(Color(0xFF10B981).copy(alpha = 0.12f))
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "${day.inRangePercent}%",
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) StatsEmerald else statsTextPrimary(isDark)
                )
                Text(
                    day.formattedTimeInRange(),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) StatsEmerald else statsTextMuted(isDark)
                )

                Spacer(Modifier.height(4.dp))

                // Container da barra
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(95.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(statsBarBg(isDark)),
                    contentAlignment = Alignment.Center
                ) {
                    // Barra interna (sempre 10dp de largura)
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(statsBarBg(isDark))
                    ) {
                        val highFrac = (day.abovePercent / 100f).coerceIn(0f, 1f)
                        val targetFrac = (day.inRangePercent / 100f).coerceIn(0f, 1f)
                        val lowFrac = (day.belowPercent / 100f).coerceIn(0f, 1f)

                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(highFrac.coerceAtLeast(0.01f))
                                    .background(StatsAmber)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(targetFrac.coerceAtLeast(0.01f))
                                    .background(StatsEmerald)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(lowFrac.coerceAtLeast(0.01f))
                                    .background(StatsRose)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    day.dayName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) StatsEmerald else statsTextMuted(isDark)
                )
            }
        }
    }

    // Detalhe do Dia Selecionado
    if (selectedDay != null) {
        Spacer(Modifier.height(12.dp))

        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(statsBarBg(isDark))
                .border(1.dp, statsBorder(isDark), shape)
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedDay.dateLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = statsTextPrimary(isDark)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatsEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Goal: ≥ ${if (isTight) "50%" else "70%"}",
                            fontSize = 11.sp,
                            color = statsTextMuted(isDark)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Barra Horizontal Proporcional do Dia
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                ) {
                    if (selectedDay.belowPercent > 0)
                        Box(Modifier.weight(selectedDay.belowPercent.toFloat()).fillMaxHeight().background(StatsRose))
                    if (selectedDay.inRangePercent > 0)
                        Box(Modifier.weight(selectedDay.inRangePercent.toFloat()).fillMaxHeight().background(StatsEmerald))
                    if (selectedDay.abovePercent > 0)
                        Box(Modifier.weight(selectedDay.abovePercent.toFloat()).fillMaxHeight().background(StatsAmber))
                }

                Spacer(Modifier.height(12.dp))

                // 3 Caixas: Baixo, Alvo, Alto
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsTirMetricBox(
                        title = "Low (<70)",
                        percent = "${selectedDay.belowPercent}%",
                        time = selectedDay.formattedTimeLow(),
                        color = StatsRose,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    StatsTirMetricBox(
                        title = "Target (${tirTarget.label})",
                        percent = "${selectedDay.inRangePercent}%",
                        time = selectedDay.formattedTimeInRange(),
                        color = StatsEmerald,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    StatsTirMetricBox(
                        title = "High (${if (isTight) ">140" else ">180"})",
                        percent = "${selectedDay.abovePercent}%",
                        time = selectedDay.formattedTimeHigh(),
                        color = StatsAmber,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ==========================================
// COMPONENTES AUXILIARES
// ==========================================
@Composable
private fun StatsTirTargetButton(label: String, isSelected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) StatsEmerald else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else statsTextMuted(isDark)
        )
    }
}

@Composable
private fun StatsTirMetricBox(
    title: String,
    percent: String,
    time: String,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(statsBarBg(isDark), RoundedCornerShape(10.dp))
            .border(1.dp, statsBorder(isDark), RoundedCornerShape(10.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(color, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
            Spacer(Modifier.height(2.dp))
            Text(percent, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = statsTextPrimary(isDark))
            Text(time, fontSize = 9.sp, color = statsTextMuted(isDark))
        }
    }
}

@Composable
private fun StatsLegendTag(label: String, color: Color, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = statsTextMuted(isDark))
    }
}
