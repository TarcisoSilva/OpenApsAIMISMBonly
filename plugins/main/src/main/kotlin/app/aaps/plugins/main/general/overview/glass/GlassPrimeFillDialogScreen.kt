package app.aaps.plugins.main.general.overview.glass

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar

// Pencil with underline badge icon - matches Prime/Fill design header
private val PrimeFillBadgeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PrimeFill",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF2563EB)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.5f, 16.5f)
            curveTo(6f, 17.7f, 8f, 18f, 9.5f, 16.5f)
            curveTo(11f, 15f, 13f, 15f, 14.5f, 16.5f)
            curveTo(16f, 17.7f, 18f, 17.7f, 19.5f, 16.5f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF2563EB)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 6f)
            lineTo(17f, 9f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF2563EB)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12.5f, 7.5f)
            lineTo(4.5f, 15.5f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF2563EB)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16f, 4.5f)
            arcTo(2.12f, 2.12f, 0f, true, true, 19f, 7.5f)
            lineTo(7.5f, 19f)
            lineTo(3f, 20f)
            lineTo(4f, 15.5f)
            close()
        }
    }.build()
}

// Crosshair/target icon for Site Change toggle
private val SiteTargetIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SiteTarget",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF334155)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, false, true, 3f, 12f)
            arcTo(9f, 9f, 0f, false, true, 21f, 12f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF334155)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15f, 12f)
            arcTo(3f, 3f, 0f, false, true, 9f, 12f)
            arcTo(3f, 3f, 0f, false, true, 15f, 12f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF334155)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3f)
            verticalLineTo(6f)
            moveTo(12f, 18f)
            verticalLineTo(21f)
            moveTo(3f, 12f)
            horizontalLineTo(6f)
            moveTo(18f, 12f)
            horizontalLineTo(21f)
        }
    }.build()
}

// Cartridge icon for Cartridge toggle
private val CartridgeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Cartridge",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF2563EB)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 2f)
            horizontalLineTo(17f)
            arcTo(3f, 3f, 0f, false, true, 17f, 8f)
            horizontalLineTo(7f)
            arcTo(3f, 3f, 0f, false, true, 7f, 2f)
            close()
            moveTo(7f, 8f)
            verticalLineTo(22f)
            moveTo(17f, 8f)
            verticalLineTo(22f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color(0xFF2563EB)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 8f)
            horizontalLineTo(17f)
            moveTo(7f, 14f)
            horizontalLineTo(17f)
        }
    }.build()
}

@Composable
fun GlassPrimeFillDialogScreen(
    isDark: Boolean,
    siteChecked: Boolean,
    cartridgeChecked: Boolean,
    primeAmountText: String,
    stepMaxLabel: String,
    formattedDate: String,
    formattedTime: String,
    notes: String,
    isSaving: Boolean,
    isSaved: Boolean,
    errorMessage: String?,
    eventTimestamp: Long,
    onSiteToggle: () -> Unit,
    onCartridgeToggle: () -> Unit,
    onPrimeMinus: () -> Unit,
    onPrimePlus: () -> Unit,
    onPrimePreset: (Double) -> Unit,
    onNotesChange: (String) -> Unit,
    onDateSet: (year: Int, month: Int, day: Int) -> Unit,
    onTimeSet: (hour: Int, minute: Int) -> Unit,
    onSetToNow: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    // GlycoCalm Theme Colors (blue/slate accent, dark/light)
    val surfaceWhite = if (isDark) Color(0xFF1E293B) else Color.White
    val surfaceField = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val textTitle = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val textMuted = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    val borderLight = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val blue = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val blueSoftBg = if (isDark) blue.copy(alpha = 0.15f) else Color(0xFFEFF6FF)
    val blueSoftBorder = if (isDark) blue.copy(alpha = 0.4f) else Color(0xFFBFDBFE)
    val sky = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val skyCardBg = if (isDark) sky.copy(alpha = 0.12f) else Color(0xFFF0F9FF)
    val skyBadgeBg = if (isDark) sky.copy(alpha = 0.15f) else Color(0xFFE0F4F7)
    val skyBadgeBorder = if (isDark) sky.copy(alpha = 0.4f) else Color(0xFFB2E4EC)
    val saveTop = Color(0xFF0284C7)
    val saveMid = Color(0xFF0EA5E9)
    val saveBottom = Color(0xFF38BDF8)
    val slateChipBg = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
    val slateChipBorder = if (isDark) Color(0xFF475569) else Color(0xFFE2E8F0)
    val slateIcon = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val closeBg = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
    val footerBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val footerBorder = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.8f)
    val uncheckedBoxBorder = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)

    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0xFF0D1B2A).copy(alpha = 0.18f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(surfaceWhite)
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
        ) {
            Column {
                // SCROLLABLE CONTENT
                Column(
                    modifier = Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(skyBadgeBg)
                                    .border(1.dp, skyBadgeBorder, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PrimeFillBadgeIcon,
                                    contentDescription = null,
                                    tint = sky,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Prime / Fill",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textTitle,
                                    letterSpacing = (-0.2).sp
                                )
                                Text(
                                    text = "Cannula fill, tubing prime & cartridge change",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textSecondary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(slateChipBg)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // TOGGLES - 2 column grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Site Change
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (siteChecked) skyCardBg.copy(alpha = 0.5f) else surfaceField.copy(alpha = 0.5f))
                                .border(
                                    if (siteChecked) 2.dp else 1.dp,
                                    if (siteChecked) sky else borderLight,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSiteToggle() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (siteChecked) skyBadgeBg else slateChipBg)
                                        .border(
                                            1.dp,
                                            if (siteChecked) skyBadgeBorder else slateChipBorder,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = SiteTargetIcon,
                                        contentDescription = null,
                                        tint = if (siteChecked) sky else slateIcon,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Site Change",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Reset timer",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (siteChecked) sky else textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            GlycoCheckBox(checked = siteChecked, accent = sky, uncheckedBorder = uncheckedBoxBorder)
                        }
                        // Cartridge
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (cartridgeChecked) skyCardBg.copy(alpha = 0.5f) else surfaceField.copy(alpha = 0.5f))
                                .border(
                                    if (cartridgeChecked) 2.dp else 1.dp,
                                    if (cartridgeChecked) sky else borderLight,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onCartridgeToggle() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(blueSoftBg)
                                        .border(1.dp, blueSoftBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CartridgeIcon,
                                        contentDescription = null,
                                        tint = blue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cartridge",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "New reservoir",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            GlycoCheckBox(checked = cartridgeChecked, accent = sky, uncheckedBorder = uncheckedBoxBorder)
                        }
                    }

                    // PRIME DOSE STEPPER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceField)
                            .border(1.dp, borderLight, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PRIME DOSE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = stepMaxLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(surfaceWhite)
                                    .border(1.dp, borderLight, RoundedCornerShape(12.dp))
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TactileCard(
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    face = Brush.verticalGradient(listOf(slateChipBg, slateChipBg)),
                                    border = borderLight,
                                    edge = if (isDark) Color(0xFF020617) else Color(0xFFCBD5E1),
                                    edgeHeight = 2.dp,
                                    onClick = { onPrimeMinus() }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = textSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(slateChipBg)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = primeAmountText,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = textTitle,
                                            letterSpacing = (-0.5).sp
                                        )
                                        Text(
                                            text = "U",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textMuted,
                                            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                                        )
                                    }
                                }
                                TactileCard(
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    face = Brush.verticalGradient(listOf(slateChipBg, slateChipBg)),
                                    border = borderLight,
                                    edge = if (isDark) Color(0xFF020617) else Color(0xFFCBD5E1),
                                    edgeHeight = 2.dp,
                                    onClick = { onPrimePlus() }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = textSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // PRESET PILLS (tactile)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TactilePresetPill(isDark = isDark, main = "1.0", unit = "U") { onPrimePreset(1.0) }
                        TactilePresetPill(isDark = isDark, main = "2.0", unit = "U") { onPrimePreset(2.0) }
                    }


                    // NOTES & SITE INFO (single line)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOTES & SITE INFO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Optional",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMuted
                            )
                        }
                        Box {
                            OutlinedTextField(
                                value = notes,
                                onValueChange = onNotesChange,
                                placeholder = {
                                    Text(
                                        text = "e.g. Right abdomen, 6mm Teflon...",
                                        fontSize = 12.sp,
                                        color = textMuted,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = surfaceWhite,
                                    unfocusedContainerColor = surfaceWhite,
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedBorderColor = sky.copy(alpha = 0.6f),
                                    unfocusedBorderColor = borderLight,
                                    cursorColor = blue
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.Create,
                                contentDescription = null,
                                tint = textMuted.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                                    .size(14.dp)
                            )
                        }
                    }

                    // TIMESTAMP
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "TIMESTAMP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(skyBadgeBg)
                                    .border(1.dp, skyBadgeBorder, RoundedCornerShape(12.dp))
                                    .clickable { onSetToNow() }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(sky)
                                )
                                Text(
                                    text = "Set to Now",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = sky
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // DATE CARD
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surfaceWhite)
                                    .border(1.dp, borderLight, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val cal = Calendar.getInstance().apply { timeInMillis = eventTimestamp }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day -> onDateSet(year, month, day) },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "DATE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMuted,
                                        letterSpacing = 0.6.sp
                                    )
                                    Text(
                                        text = formattedDate,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            // TIME CARD
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surfaceWhite)
                                    .border(1.dp, borderLight, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val cal = Calendar.getInstance().apply { timeInMillis = eventTimestamp }
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute -> onTimeSet(hour, minute) },
                                            cal.get(Calendar.HOUR_OF_DAY),
                                            cal.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "TIME",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMuted,
                                        letterSpacing = 0.6.sp
                                    )
                                    Text(
                                        text = formattedTime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // FOOTER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(footerBg)
                        .border(1.dp, footerBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(closeBg)
                            .border(1.dp, borderLight, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = if (isSaved) Brush.verticalGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                                else Brush.horizontalGradient(listOf(saveTop, saveMid, saveBottom))
                            )
                            .clickable(enabled = !isSaving && !isSaved) { onSave() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isSaved) "Saved" else "Save Prime",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun GlycoCheckBox(
    checked: Boolean,
    accent: Color,
    uncheckedBorder: Color
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) accent else Color.Transparent)
            .border(
                2.dp,
                if (checked) accent else uncheckedBorder,
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}


@Composable
private fun RowScope.TactilePresetPill(
    isDark: Boolean,
    main: String,
    unit: String,
    onClick: () -> Unit
) {
    val bg: Brush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
    }
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val mainColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val unitColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    TactileCard(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        face = bg,
        border = borderColor,
        edge = if (isDark) Color(0xFF020617) else Color(0xFFCBD5E1),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = main,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = mainColor
            )
            Text(
                text = " $unit",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = unitColor
            )
        }
    }
    }
}
