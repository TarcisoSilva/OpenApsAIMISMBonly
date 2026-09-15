package app.aaps.plugins.main.general.overview.glass

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * GlassOverviewScreen
 * Tela principal em Jetpack Compose que implementa o design Glassmorphism
 * compatível com o OpenApsAIMI / AndroidAPS (branch dev-2).
 */
@Composable
fun GlassOverviewScreen(
    state: GlassUiState,
    onToggleTheme: () -> Unit,
    onSelectRangeHours: (Int) -> Unit,
    onOpenWizard: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenTreatments: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLoopDialog: () -> Unit,
    onOpenLoopDashboard: () -> Unit,
    onOpenInsulin: () -> Unit,
    onOpenPump: () -> Unit,
    onOpenCannula: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenTempTarget: () -> Unit,
    onOpenTempBasal: () -> Unit,
    onOpenSensorInsert: () -> Unit,
    onRefreshData: () -> Unit,
    onDismissNotification: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = state.isDarkMode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF070E1B), Color(0xFF0B1424), Color(0xFF070E1B))
                    } else {
                        listOf(Color(0xFFF1F5F9), Color(0xFFE8EEF8), Color(0xFFE8EEF8))
                    }
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Card STATUS AGORA (Layout 3 colunas fiel ao applet)
            StatusAgoraCard(
                state = state,
                isDark = isDark,
                onToggleTheme = onToggleTheme,
                onOpenLoopDialog = onOpenLoopDialog,
                onOpenLoopDashboard = onOpenLoopDashboard,
                onOpenTarget = onOpenTempTarget,
                onOpenInsulin = onOpenInsulin,
                onOpenPump = onOpenPump,
                onOpenCannula = onOpenCannula,
                onOpenBattery = onOpenBattery,
                onOpenBasal = onOpenTempBasal,
                onOpenSensorInsert = onOpenSensorInsert,
                onRefresh = onRefreshData
            )

            // 2. Gráfico Glicêmico (BG) com Faixa do range do usuário, Curva Bezier e Gradiente
            BgChartCard(
                readings = state.bgReadings,
                treatments = state.treatments,
                timeRangeHours = state.selectedRangeHours,
                currentBg = state.currentBg,
                lowLine = state.lowLine,
                highLine = state.highLine,
                isDark = isDark
            )

            // 3. Gráfico de IOB (Insulina Ativa)
            IobChartCard(
                iobReadings = state.iobReadings,
                currentIob = state.iob,
                timeRangeHours = state.selectedRangeHours,
                isDark = isDark
            )

            // 4. Barra de Ações Rápidas e Filtros de Tempo
            TimeFilterBar(
                selectedHours = state.selectedRangeHours,
                onSelectHours = onSelectRangeHours,
                onOpenStats = onOpenStats,
                onOpenTreatment = onOpenTreatments,
                isDark = isDark
            )

            // Notificação de Status da Pump (SMB, Temp Basal, etc.)
            if (state.pumpStatus.isNotEmpty()) {
                PumpStatusNotification(
                    status = state.pumpStatus,
                    isDark = isDark,
                    onClick = onOpenPump
                )
            }

            // Espaço inferior suave para não colar na barra nativa de navegação do AndroidAPS
            Spacer(modifier = Modifier.height(16.dp))
        }

        // System Notifications - Overlay on top of cards
        if (state.notifications.isNotEmpty()) {
            NotificationsSection(
                notifications = state.notifications,
                isDark = isDark,
                onDismiss = onDismissNotification,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

// ==========================================
// CARD: STATUS AGORA (LAYOUT 3 COLUNAS - GlycoCalm Design)
// ==========================================
@Composable
fun StatusAgoraCard(
    state: GlassUiState,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenLoopDialog: () -> Unit,
    onOpenLoopDashboard: () -> Unit,
    onOpenTarget: () -> Unit,
    onOpenInsulin: () -> Unit,
    onOpenPump: () -> Unit,
    onOpenCannula: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenBasal: () -> Unit,
    onOpenSensorInsert: () -> Unit,
    onRefresh: () -> Unit
) {
    GlassContainer(
        isDark = isDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 3-Column Layout: Left (Pump) | Center (Glucose) | Right (CGM/Loop)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT COLUMN: Pump Status (Insulina, Cânula, Bateria)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassPill(
                        label = "Insulin",
                        value = if (state.reservoirLevelPercent >= 10) {
                            "${state.reservoirLevelPercent}% ${state.insulinAge}"
                        } else {
                            "${state.sensorReservoir} ${state.insulinAge}"
                        },
                        isDark = isDark,
                        valueColor = Color(state.reservoirColor),
                        modifier = Modifier.width(96.dp).clickable { onOpenPump() },
                        leadingIcon = {
                            val reservoirIconRes = when (state.reservoirLevelPercent) {
                                in 76..100 -> app.aaps.core.main.R.drawable.ic_reservoir_100
                                in 51..75  -> app.aaps.core.main.R.drawable.ic_reservoir_75
                                in 26..50  -> app.aaps.core.main.R.drawable.ic_reservoir_50
                                in 11..25  -> app.aaps.core.main.R.drawable.ic_reservoir_25
                                else       -> app.aaps.core.main.R.drawable.ic_reservoir_10
                            }
                            val reservoirTint = when (state.reservoirLevelPercent) {
                                in 26..100 -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                else       -> Color.Unspecified  // 25% and 10% have their own colors
                            }
                            Icon(
                                painter = painterResource(id = reservoirIconRes),
                                contentDescription = null,
                                tint = reservoirTint,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    GlassPill(
                        label = "Cannula",
                        value = state.cannulaAge,
                        isDark = isDark,
                        valueColor = Color(state.cannulaAgeColor),
                        modifier = Modifier.width(96.dp).clickable { onOpenCannula() },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = app.aaps.plugins.main.R.drawable.ic_syringe),
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    GlassPill(
                        label = "Battery",
                        value = state.batteryAge,
                        isDark = isDark,
                        valueColor = Color(state.batteryAgeColor),
                        modifier = Modifier.width(96.dp).clickable { onOpenBattery() },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = app.aaps.core.main.R.drawable.ic_cp_pump_battery),
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }

                // CENTER COLUMN: Glucose Display
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    val bgNum = state.rawBg
                    val glucoseColor = when {
                        bgNum < state.lowLine -> Color(0xFFEF4444)
                        bgNum > state.highLine -> Color(0xFFF59E0B)
                        else -> Color(0xFF22C55E)
                    }

                    // Large BG Value (top aligned with pill box tops)
                    Text(
                        text = state.currentBg,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = glucoseColor,
                        letterSpacing = (-1.5).sp,
                        lineHeight = 52.sp,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        modifier = Modifier.clickable { onOpenLoopDialog() }
                    )

                    // Unit
                    Text(
                        text = state.unit,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.padding(top = 1.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Delta with Arrow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (state.trendArrowRes != 0) {
                            Icon(
                                painter = painterResource(id = state.trendArrowRes),
                                contentDescription = state.trend,
                                tint = glucoseColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        val deltaText = if (state.delta >= 0) "+${state.delta}" else "${state.delta}"
                        Text(
                            text = deltaText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = glucoseColor,
                            modifier = Modifier.padding(start = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Time Ago
                    Text(
                        text = state.timeAgo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                }

                // RIGHT COLUMN: CGM & Loop (Sensor, Loop, Ajustes)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassPill(
                        label = "Sensor",
                        value = state.sensorAge,
                        isDark = isDark,
                        valueColor = Color(state.sensorAgeColor),
                        modifier = Modifier.width(96.dp).clickable { onOpenSensorInsert() },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = app.aaps.plugins.main.R.drawable.ic_glyco_sensor),
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    // Loop: pulsing dot when active, status icon (disconnect/paused/...) otherwise
                    GlassPill(
                        label = "Loop",
                        value = if (state.loopTimeRemaining.isNotEmpty()) state.loopTimeRemaining else state.loopStatusText,
                        isDark = isDark,
                        valueColor = if (state.loopTimeRemaining.isNotEmpty()) {
                            if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                        } else null,
                        modifier = Modifier.width(96.dp).clickable { onOpenLoopDashboard() },
                        leadingIcon = {
                            if (state.loopIconRes != 0) {
                                Icon(
                                    painter = painterResource(id = state.loopIconRes),
                                    contentDescription = state.loopStatusText,
                                    tint = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                val infiniteTransition = rememberInfiniteTransition()
                                val pulseAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 0.8f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E).copy(alpha = pulseAlpha), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    )
                                }
                            }
                        }
                    )
                    GlassPill(
                        label = "Theme",
                        value = if (isDark) "Dark" else "Light",
                        isDark = isDark,
                        modifier = Modifier.width(96.dp).clickable { onToggleTheme() },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = app.aaps.plugins.main.R.drawable.ic_glyco_settings),
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Row: IOB, Target, Basal T
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BottomMetricPill(
                    title = "IOB",
                    value = String.format(Locale.US, "%.2f U", state.iob),
                    isDark = isDark,
                    onClick = onOpenInsulin,
                    modifier = Modifier.weight(1f)
                )
                BottomMetricPill(
                    title = if (state.isTempTargetActive && state.targetText.isNotEmpty()) "" else "Target",
                    value = if (state.isTempTargetActive && state.targetText.isNotEmpty()) state.targetText else "${state.targetBg}",
                    isDark = isDark,
                    onClick = onOpenTarget,
                    modifier = Modifier.weight(1f),
                    accentColor = if (state.isTempTargetActive) Color(0xFFF4D700) else null
                )
                BottomMetricPill(
                    title = "Basal T",
                    value = "${state.basalPercent}%",
                    isDark = isDark,
                    onClick = onOpenBasal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ==========================================
// COMPONENTES AUXILIARES GLASSMORPHISM
// ==========================================
@Composable
fun GlassContainer(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(Color(0xEC1C2640), Color(0xF5080E18)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xF0E8EEF6)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    val borderColor = if (isDark) Color(0x40FFFFFF) else Color(0xE0FFFFFF)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 26.dp else 16.dp,
                shape = shape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color(0x660F172A),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.50f) else Color(0x400F172A)
            )
            .clip(shape)
            .background(backgroundBrush)
            .border(1.5.dp, borderColor, shape)
    ) {
        // Linha de reflexo specular superior (brilho intenso)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(1.5.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            if (isDark) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 1.0f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Brilho interno superior (glow mais forte)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.65f),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun GlassPill(
    label: String,
    value: String,
    isDark: Boolean,
    valueColor: Color? = null,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(13.dp)
    val bgBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x18FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xE4E8F0FA)))
    }
    val borderColor = if (isDark) Color(0x38FFFFFF) else Color(0xD0CBD5E1)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 6.dp else 3.dp,
                shape = shape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.50f) else Color(0x22000000)
            )
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    lineHeight = 11.sp
                )
                Text(
                    text = value,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor ?: if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
fun BottomMetricPill(
    title: String,
    value: String,
    isDark: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val bgBrush = if (accentColor != null) {
        if (isDark) {
            Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x14FFFFFF)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xE4EEF2FA)))
        }
    } else if (isDark) {
        Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x14FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xE4EEF2FA)))
    }
    val overlayColor = accentColor?.copy(alpha = if (isDark) 0.14f else 0.12f)
    val borderColor = accentColor?.copy(alpha = 0.50f) ?: if (isDark) Color(0x30FFFFFF) else Color(0xD0CBD5E1)
    val textColor = if (accentColor != null && !isDark) Color(0xFF303030) else accentColor ?: if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val valueColor = if (accentColor != null && !isDark) Color(0xFF303030) else accentColor ?: if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (accentColor != null) 8.dp else if (isDark) 7.dp else 4.dp,
                shape = shape,
                spotColor = if (accentColor != null) accentColor.copy(alpha = 0.35f) else if (isDark) Color.Black.copy(alpha = 0.50f) else Color(0x22000000)
            )
            .clip(shape)
            .background(bgBrush)
            .then(if (overlayColor != null) Modifier.background(overlayColor) else Modifier)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 7.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (title.isNotEmpty()) {
                Text(
                    text = "$title ",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}

// ==========================================
// GRÁFICO GLICÊMICO (CANVAS COM FAIXA DO RANGE DO USUÁRIO)
// ==========================================
@Composable
fun BgChartCard(
    readings: List<BgReadingPoint>,
    treatments: List<TreatmentPoint>,
    timeRangeHours: Int,
    currentBg: String,
    lowLine: Float,
    highLine: Float,
    isDark: Boolean
) {
    var touchedReading by remember { mutableStateOf<BgReadingPoint?>(null) }
    var selectedTreatment by remember { mutableStateOf<TreatmentPoint?>(null) }

    // Quando novos dados chegam, reseta o arrasto para a leitura atual
    LaunchedEffect(readings) {
        touchedReading = null
        selectedTreatment = null
    }

    GlassContainer(
        isDark = isDark,
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BG",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                )
                if (touchedReading != null) {
                    val pt = touchedReading!!
                    val now = java.time.LocalTime.now()
                    val hoursAgo = timeRangeHours * (1f - pt.progress)
                    val pointTime = now.minusMinutes((hoursAgo * 60).toLong())
                    val nearbyTreatment = treatments.minByOrNull { abs(it.progress - pt.progress) }
                    val isNearTreatment = nearbyTreatment != null && abs(nearbyTreatment!!.progress - pt.progress) < 0.025f
                    val timeStr = String.format(Locale.US, "%02d:%02d", pointTime.hour, pointTime.minute)
                    val bgStr = String.format(Locale.US, "%.0f mg/dL", pt.value)
                    val bgColor = when {
                        pt.value < lowLine -> Color(0xFFEF4444)
                        pt.value > highLine -> Color(0xFFF59E0B)
                        else -> Color(0xFF22C55E)
                    }
                    if (isNearTreatment) {
                        val t = nearbyTreatment!!
                        Text(
                            text = "$timeStr  ${t.label} - $bgStr",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = bgColor
                        )
                    } else {
                        Text(
                            text = "$timeStr  $bgStr",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = bgColor
                        )
                    }
                } else {
                    Text(
                        text = "$currentBg mg/dL",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            (currentBg.toFloatOrNull() ?: 0f) < lowLine -> Color(0xFFEF4444)
                            (currentBg.toFloatOrNull() ?: 0f) > highLine -> Color(0xFFF59E0B)
                            else -> Color(0xFF22C55E)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Canvas(modifier = Modifier
                .fillMaxSize()
                .pointerInput(readings, treatments, timeRangeHours) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val chartW = size.width - 65f
                        val chartH = size.height - 36f
                        val treatmentY = chartH - 8f
                        val p = (down.position.x / chartW).coerceIn(0f, 1f)
                        touchedReading = readings.minByOrNull { abs(it.progress - p) }

                        // Verifica se tocou perto de um marcador de tratamento (X + Y)
                        val nearTreatment = treatments.minByOrNull { abs(it.progress - p) }
                        if (nearTreatment != null
                            && abs(nearTreatment.progress - p) < 0.035f
                            && abs(down.position.y - treatmentY) < 50f
                        ) {
                            selectedTreatment = nearTreatment
                        } else {
                            selectedTreatment = null
                        }

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            if (event.changes.any { it.pressed }) {
                                val px = (event.changes.first().position.x / chartW).coerceIn(0f, 1f)
                                touchedReading = readings.minByOrNull { abs(it.progress - px) }
                                event.changes.first().consume()
                            } else {
                                break
                            }
                        }
                    }
                }
            ) {
                val w = size.width
                val h = size.height
                val paddingRight = 65f
                val chartW = w - paddingRight
                val chartH = h - 36f

                val minY = 30f
                val maxY = maxOf(
                    readings.maxByOrNull { it.value }?.value ?: highLine,
                    highLine
                ) + 40f

                fun mapY(bg: Float): Float {
                    val clamped = bg.coerceIn(minY, maxY)
                    return chartH - ((clamped - minY) / (maxY - minY)) * chartH
                }

                val yHigh = mapY(highLine)
                val yLow = mapY(lowLine)

                // 1. Faixa do Range do usuário (lowLine a highLine) com Gradiente verde
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF34D399).copy(alpha = if (isDark) 0.26f else 0.20f),
                            Color(0xFF34D399).copy(alpha = if (isDark) 0.08f else 0.04f)
                        ),
                        startY = yHigh,
                        endY = yLow
                    ),
                    topLeft = Offset(0f, yHigh),
                    size = Size(chartW, yLow - yHigh)
                )

                // 2. Linhas de Referência Horizontais (highLine e lowLine)
                val lineStroke = if (isDark) Color(0x2EFFFFFF) else Color(0x26000000)
                drawLine(color = lineStroke, start = Offset(0f, yHigh), end = Offset(chartW, yHigh), strokeWidth = 1f)
                drawLine(color = lineStroke, start = Offset(0f, yLow), end = Offset(chartW, yLow), strokeWidth = 1f)

                // Labels do Eixo Y na direita (highLine e lowLine)
                val textPaint = Paint().apply {
                    color = if (isDark) android.graphics.Color.argb(160, 255, 255, 255) else android.graphics.Color.argb(180, 71, 85, 105)
                    textSize = 28f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val scaleCenterX = chartW + paddingRight / 2f
                drawContext.canvas.nativeCanvas.drawText(highLine.toInt().toString(), scaleCenterX, yHigh + 8f, textPaint)
                drawContext.canvas.nativeCanvas.drawText(lowLine.toInt().toString(), scaleCenterX, yLow + 8f, textPaint)

                // 3. Linhas Verticais de Tempo e Labels
                val timeTicks = 6
                for (i in 0 until timeTicks) {
                    val x = (i.toFloat() / (timeTicks - 1)) * chartW
                    drawLine(
                        color = if (isDark) Color(0x14FFFFFF) else Color(0x0D000000),
                        start = Offset(x, 0f),
                        end = Offset(x, chartH),
                        strokeWidth = 0.8f
                    )
                }

                // 4. Curva de Glicemia e Área Preenchida - coloridas por zona do range
                if (readings.size >= 2) {
                    fun zoneColor(bg: Float): Color = when {
                        bg < lowLine -> Color(0xFFEF4444)   // abaixo do range → vermelho
                        bg > highLine -> Color(0xFFF59E0B)  // acima do range → âmbar
                        else -> Color(0xFF22C55E)           // dentro do range → verde
                    }

                    val firstX = readings.first().progress * chartW
                    val firstY = mapY(readings.first().value)

                    // Traço segmentado da curva, colorido por zona
                    for (i in 1 until readings.size) {
                        val prev = readings[i - 1]
                        val curr = readings[i]

                        val pX = prev.progress * chartW
                        val pY = mapY(prev.value)
                        val cX = curr.progress * chartW
                        val cY = mapY(curr.value)

                        val midX = (pX + cX) / 2f

                        val segPath = Path().apply {
                            moveTo(pX, pY)
                            cubicTo(midX, pY, midX, cY, cX, cY)
                        }
                        drawPath(
                            path = segPath,
                            color = zoneColor((prev.value + curr.value) / 2f),
                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Ponto da leitura atual na ponta direita (círculo brilhante com centro branco)
                    val lastX = readings.last().progress * chartW
                    val lastY = mapY(readings.last().value)
                    val lastColor = zoneColor(readings.last().value)
                    drawCircle(color = lastColor, radius = 10f, center = Offset(lastX, lastY))
                    drawCircle(color = Color.White, radius = 4.5f, center = Offset(lastX, lastY))
                }

                // 5. Marcadores de Tratamentos (+ Carbs, - Bolus/SMB)
                for (t in treatments) {
                    val tx = t.progress * chartW
                    val ty = chartH - 8f
                    if (t.isCarb) {
                        drawCircle(color = Color(0xFF38BDF8), radius = 10f, center = Offset(tx, ty))
                    } else {
                        // SMB mantém o azul atual; bolus manual em azul mais claro
                        val isSmb = t.label.startsWith("SMB")
                        drawCircle(
                            color = if (isSmb) Color(0xFF0284C7) else Color(0xFF7DD3FC),
                            radius = 10f,
                            center = Offset(tx, ty)
                        )
                    }
                }

                // 6. Labels de Horas no Eixo X (alinhados com o IOB)
                val hourPaint = Paint().apply {
                    color = if (isDark) android.graphics.Color.argb(130, 255, 255, 255) else android.graphics.Color.argb(150, 71, 85, 105)
                    textSize = 26f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val now = java.time.LocalTime.now()
                for (i in 0 until timeTicks) {
                    val x = (i.toFloat() / (timeTicks - 1)) * chartW
                    val progress = i.toFloat() / (timeTicks - 1)
                    val hoursAgo = timeRangeHours * (1f - progress)
                    val totalMinutes = (hoursAgo * 60).toLong()
                    val labelTime = now.minusMinutes(totalMinutes)
                    val label = String.format(Locale.US, "%02d", labelTime.hour)
                    drawContext.canvas.nativeCanvas.drawText(label, x, h - 6f, hourPaint)
                }

                // 7. Indicador de ponto tocado (linha pontilhada + círculo)
                touchedReading?.let { pt ->
                    val ptX = pt.progress * chartW
                    val ptY = mapY(pt.value)

                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(ptX, 0f),
                        end = Offset(ptX, chartH),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                    drawCircle(color = Color(0xFFF59E0B), radius = 10f, center = Offset(ptX, ptY))
                    drawCircle(color = Color.White, radius = 4f, center = Offset(ptX, ptY))
                }
            }
        }
    }

    // Modal de detalhes do tratamento (SMB/Bolus/Carbs)
    selectedTreatment?.let { t ->
        val timestamp = t.timestamp
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val dateStr = String.format(Locale.US, "%02d/%02d/%04d", cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR))
        val timeStr = String.format(Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))

        AlertDialog(
            onDismissRequest = { selectedTreatment = null },
            confirmButton = {
                TextButton(onClick = { selectedTreatment = null }) {
                    Text("OK", color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                }
            },
            title = {
                Text(
                    text = "Treatment Details",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Type:", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 13.sp)
                        Text(
                            text = when {
                                t.label.startsWith("SMB") -> "SMB (Super Micro Bolus)"
                                t.label.startsWith("Bolus") -> "Bolus"
                                else -> "Carbs"
                            },
                            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Date:", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 13.sp)
                        Text(dateStr, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Time:", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 13.sp)
                        Text(timeStr, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Amount:", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 13.sp)
                        Text(
                            text = t.label,
                            color = if (t.label.startsWith("SMB") || t.label.startsWith("Bolus")) Color(0xFF38BDF8) else Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            containerColor = if (isDark) Color(0xCC0F172A) else Color(0xF2FFFFFF),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ==========================================
// GRÁFICO DE IOB (INSULINA ATIVA)
// ==========================================
@Composable
fun IobChartCard(
    iobReadings: List<IobReadingPoint>,
    currentIob: Float,
    timeRangeHours: Int,
    isDark: Boolean
) {
    var touchedIob by remember { mutableStateOf<IobReadingPoint?>(null) }

    // Quando novos dados chegam, reseta o arrasto para a leitura atual
    LaunchedEffect(iobReadings) {
        touchedIob = null
    }

    GlassContainer(
        isDark = isDark,
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IOB",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                )
                if (touchedIob != null) {
                    val pt = touchedIob!!
                    val now = java.time.LocalTime.now()
                    val hoursAgo = timeRangeHours * (1f - pt.progress)
                    val pointTime = now.minusMinutes((hoursAgo * 60).toLong())
                    Text(
                        text = String.format(Locale.US, "%02d:%02d  %.2f U", pointTime.hour, pointTime.minute, pt.iob),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                } else {
                    Text(
                        text = String.format(Locale.US, "%.2f U", currentIob),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Canvas(modifier = Modifier
                .fillMaxSize()
                .pointerInput(iobReadings, timeRangeHours) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val chartW = size.width - 55.dp.toPx()
                        val p = (down.position.x / chartW).coerceIn(0f, 1f)
                        touchedIob = iobReadings.minByOrNull { abs(it.progress - p) }

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            if (event.changes.any { it.pressed }) {
                                val px = (event.changes.first().position.x / chartW).coerceIn(0f, 1f)
                                touchedIob = iobReadings.minByOrNull { abs(it.progress - px) }
                                event.changes.first().consume()
                            } else {
                                break
                            }
                        }
                    }
                }
            ) {
                val w = size.width
                val h = size.height
                val paddingRight = 65f
                val chartW = w - paddingRight
                val chartH = h - 28f

                val peakIob = maxOf(
                    iobReadings.maxByOrNull { it.iob }?.iob ?: 0f,
                    currentIob
                ).coerceAtLeast(0f)
                val maxIob = when {
                    peakIob == 0f -> 1f
                    peakIob % 1f == 0f -> peakIob + 1f
                    else -> ceil(peakIob)
                }

                fun mapY(iob: Float): Float {
                    val clamped = iob.coerceIn(0f, maxIob)
                    return chartH - (clamped / maxIob) * chartH
                }

                // Linha de base 0U
                drawLine(
                    color = if (isDark) Color(0x1FFFFFFF) else Color(0x1F000000),
                    start = Offset(0f, chartH),
                    end = Offset(chartW, chartH),
                    strokeWidth = 1f
                )

                // Linha de topo no valor da escala
                drawLine(
                    color = if (isDark) Color(0x1FFFFFFF) else Color(0x1F000000),
                    start = Offset(0f, 0f),
                    end = Offset(chartW, 0f),
                    strokeWidth = 1f
                )

                // Linhas verticais de grade
                for (i in 0 until 6) {
                    val x = (i.toFloat() / 5f) * chartW
                    drawLine(
                        color = if (isDark) Color(0x14FFFFFF) else Color(0x0D000000),
                        start = Offset(x, 0f),
                        end = Offset(x, chartH),
                        strokeWidth = 0.8f
                    )
                }

                val textPaint = Paint().apply {
                    color = if (isDark) android.graphics.Color.argb(160, 255, 255, 255) else android.graphics.Color.argb(180, 71, 85, 105)
                    textSize = 28f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val scaleCenterX = chartW + paddingRight / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%.1f", maxIob),
                    scaleCenterX,
                    12f,
                    textPaint
                )
                drawContext.canvas.nativeCanvas.drawText("0", scaleCenterX, chartH + 4f, textPaint)

                if (iobReadings.size >= 2) {
                    val path = Path()
                    val areaPath = Path()

                    val firstX = iobReadings.first().progress * chartW
                    val firstY = mapY(iobReadings.first().iob)

                    path.moveTo(firstX, firstY)
                    areaPath.moveTo(firstX, chartH)
                    areaPath.lineTo(firstX, firstY)

                    for (i in 1 until iobReadings.size) {
                        val prev = iobReadings[i - 1]
                        val curr = iobReadings[i]

                        val pX = prev.progress * chartW
                        val pY = mapY(prev.iob)
                        val cX = curr.progress * chartW
                        val cY = mapY(curr.iob)

                        val midX = (pX + cX) / 2f
                        path.cubicTo(midX, pY, midX, cY, cX, cY)
                        areaPath.cubicTo(midX, pY, midX, cY, cX, cY)
                    }

                    val lastX = iobReadings.last().progress * chartW
                    val lastY = mapY(iobReadings.last().iob)
                    areaPath.lineTo(lastX, chartH)
                    areaPath.close()

                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = if (isDark) 0.35f else 0.22f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = chartH
                        )
                    )

                    drawPath(
                        path = path,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    drawCircle(color = Color(0xFF38BDF8), radius = 8f, center = Offset(lastX, lastY))
                    drawCircle(color = Color.White, radius = 3.5f, center = Offset(lastX, lastY))
                }

                // Labels de Horas no Eixo X (alinhados com o BG)
                val hourPaint = Paint().apply {
                    color = if (isDark) android.graphics.Color.argb(130, 255, 255, 255) else android.graphics.Color.argb(150, 71, 85, 105)
                    textSize = 26f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val now = java.time.LocalTime.now()
                for (i in 0 until 6) {
                    val x = (i.toFloat() / 5f) * chartW
                    val progress = i.toFloat() / 5f
                    val hoursAgo = timeRangeHours * (1f - progress)
                    val totalMinutes = (hoursAgo * 60).toLong()
                    val labelTime = now.minusMinutes(totalMinutes)
                    val label = String.format(Locale.US, "%02d", labelTime.hour)
                    drawContext.canvas.nativeCanvas.drawText(label, x, h - 6f, hourPaint)
                }

                // Indicador de ponto tocado IOB (linha pontilhada + círculo)
                touchedIob?.let { pt ->
                    val ptX = pt.progress * chartW
                    val ptY = mapY(pt.iob)

                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(ptX, 0f),
                        end = Offset(ptX, chartH),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                    drawCircle(color = Color(0xFF38BDF8), radius = 10f, center = Offset(ptX, ptY))
                    drawCircle(color = Color.White, radius = 4f, center = Offset(ptX, ptY))
                }
            }
        }
    }
}

// ==========================================
// BARRA DE TEMPO E AÇÕES RÁPIDAS
// ==========================================
@Composable
fun TimeFilterBar(
    selectedHours: Int,
    onSelectHours: (Int) -> Unit,
    onOpenStats: () -> Unit,
    onOpenTreatment: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botão Stats (pill de ação, mesmo visual dos pills IOB/Target/Basal)
        GlassActionPill(
            label = "Stats",
            onClick = onOpenStats,
            isDark = isDark,
            height = 40.dp
        )

        // Seletor de Intervalo [ 6h | 12h | 18h | 24h ] dentro de um invólucro vítreo
        GlassHourDock(
            selectedHours = selectedHours,
            onSelectHours = onSelectHours,
            isDark = isDark
        )

        // Botão Treatment (pill de ação, mesmo visual dos pills IOB/Target/Basal)
        GlassActionPill(
            label = "Treatment",
            onClick = onOpenTreatment,
            isDark = isDark,
            height = 40.dp
        )
    }
}

@Composable
fun GlassHourDock(
    selectedHours: Int,
    onSelectHours: (Int) -> Unit,
    isDark: Boolean
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .shadow(
                elevation = if (isDark) 8.dp else 5.dp,
                shape = shape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.45f) else Color(0x20000000)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (isDark) {
                        listOf(Color(0x33FFFFFF), Color(0x0EFFFFFF))
                    } else {
                        listOf(Color(0x99FFFFFF), Color(0x55E8EEF8))
                    }
                )
            )
            .border(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x80CBD5E1), shape)
            .height(40.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(6, 12, 18, 24).forEach { hours ->
                val isSelected = selectedHours == hours
                GlassActionPill(
                    label = "${hours}h",
                    onClick = { onSelectHours(hours) },
                    isDark = isDark,
                    isSelected = isSelected,
                    compact = true
                )
            }
        }
    }
}

@Composable
fun GlassActionPill(
    label: String,
    onClick: () -> Unit,
    isDark: Boolean,
    isSelected: Boolean = false,
    compact: Boolean = false,
    height: Dp? = null
) {
    val shape = when {
        compact -> RoundedCornerShape(14.dp)
        else -> RoundedCornerShape(50)
    }
    val bgBrush = when {
        isSelected && isDark -> Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
        isSelected -> Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
        isDark -> Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0EFFFFFF)))
        else -> Brush.verticalGradient(listOf(Color(0xF5FFFFFF), Color(0xE0EEF2FA)))
    }
    val borderColor = when {
        isSelected -> Color(0x66028AC6)
        isDark -> Color(0x26FFFFFF)
        else -> Color(0xB8CBD5E1)
    }
    val textColor = when {
        isSelected -> Color.White
        isDark -> Color(0xFFE2E8F0)
        else -> Color(0xFF1E293B)
    }

    val boxModifier = Modifier
            .shadow(
                elevation = if (isDark) 7.dp else 5.dp,
                shape = shape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.42f) else Color(0x20000000)
            )
        .clip(shape)
        .background(bgBrush)
        .border(1.dp, borderColor, shape)
        .clickable { onClick() }
        .padding(
            vertical = when {
                height != null -> 0.dp
                compact -> 6.dp
                else -> 14.dp
            },
            horizontal = when {
                compact -> 8.dp
                else -> 20.dp
            }
        )
        .then(if (height != null) Modifier.height(height) else Modifier)

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = when {
                compact -> 11.sp
                else -> 14.sp
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

// ==========================================
// NOTIFICAÇÃO: STATUS DA PUMP (SMB, Temp Basal, etc.)
// ==========================================
@Composable
fun PumpStatusNotification(
    status: String,
    isDark: Boolean,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(14.dp)
    val bgBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x14FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xE4EEF2FA)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, if (isDark) Color(0x30FFFFFF) else Color(0xD0CBD5E1), shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
            letterSpacing = 0.3.sp
        )
    }
}

// ==========================================
// NOTIFICAÇÕES DO SISTEMA (Alarmes, Alertas)
// ==========================================
@Composable
fun NotificationsSection(
    notifications: List<GlassNotificationItem>,
    isDark: Boolean,
    onDismiss: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val snoozeBg = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        notifications.forEach { notification ->
            val (strokeColor, levelLabel) = when (notification.level) {
                0 -> Color(0xFFEF4444) to "ALERT"      // URGENT: Missing BG, Low Glucose
                1 -> Color(0xFFF59E0B) to "ALERT"      // NORMAL
                2 -> Color(0xFF38BDF8) to "INFO"       // LOW
                3 -> Color(0xFF10B981) to "WARNING"    // INFO: Stable glucose, Calibration
                4 -> Color(0xFF8B5CF6) to "NOTICE"     // ANNOUNCEMENT
                else -> Color(0xFF94A3B8) to "NOTICE"
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(2.dp, strokeColor),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(strokeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = strokeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = levelLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = strokeColor,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = notification.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Dismiss button
                    Surface(
                        onClick = { onDismiss(notification.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = snoozeBg,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = null,
                                tint = textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Dismiss",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}


