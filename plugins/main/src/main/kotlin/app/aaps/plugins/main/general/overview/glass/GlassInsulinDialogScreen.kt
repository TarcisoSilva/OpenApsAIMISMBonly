package app.aaps.plugins.main.general.overview.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.aaps.plugins.main.R
import kotlin.math.max
import kotlin.math.min

// Droplet icon - same as ic_nav_bolus.xml
private val DropletIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Droplet",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = null,
            strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(12f, 2.69f)
            lineTo(17.66f, 8.35f)
            arcTo(8f, 8f, 0f, true, true, 6.35f, 8.35f)
            close()
        }
    }.build()
}

@Composable
fun GlassInsulinDialogScreen(
    isDark: Boolean,
    maxInsulin: Double,
    bolusStep: Double,
    plus1: Double,
    plus2: Double,
    plus3: Double,
    currentIOB: Double,
    currentBG: Double,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, recordOnly: Boolean, eatingSoon: Boolean, notes: String) -> Unit
) {
    // GlycoCalm Light Theme Colors
    val surfaceWhite = Color.White
    val surfaceLight = Color(0xFFEFF4FF)
    val textPrimary = Color(0xFF0B1C30)
    val textSecondary = Color(0xFF64748B)
    val textMuted = Color(0xFF94A3B8)
    val accentBlue = Color(0xFF2563EB)
    val accentBlueDark = Color(0xFF1D4ED8)
    val borderLight = Color(0xFFE2E8F0)
    val borderBlue = Color(0xFFBFDBFE)
    val emerald = Color(0xFF00B894)

    var amount by remember { mutableStateOf(0.0) }
    var recordOnly by remember { mutableStateOf(false) }
    var eatingSoon by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val canConfirm = amount > 0 || eatingSoon
    val dosePercentage = if (maxInsulin > 0) (amount / maxInsulin).coerceIn(0.0, 1.0) else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = Color(0xFF0B1C30).copy(alpha = 0.28f)
                )
                .clip(RoundedCornerShape(36.dp))
                .background(surfaceWhite)
                .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(36.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grabber Handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFCBD5E1))
                        .align(Alignment.CenterHorizontally)
                )

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
                        // Droplet Icon Badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.verticalGradient(listOf(surfaceLight, Color(0xFFDBEAFE)))
                                )
                                .border(1.dp, borderBlue.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_bolus),
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Bolus Delivery",
                                    color = textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(surfaceLight)
                                        .border(1.dp, borderBlue, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Quick Bolus",
                                        color = accentBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // CLINICAL CONTEXT STRIP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // IOB Card with Clock Icon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceWhite)
                            .border(1.dp, borderLight, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEFF4FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = accentBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Column {
                                Text("ACTIVE IOB", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        String.format("%.2f", currentIOB),
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text("U", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    // Glucose Card with Trending Up Icon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceWhite)
                            .border(1.dp, borderLight, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFECFDF5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.TrendingUp,
                                    contentDescription = null,
                                    tint = emerald,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Column {
                                Text("GLUCOSE", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        String.format("%.0f", currentBG),
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text("mg/dL", color = textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                // DOSE AMOUNT SECTION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFF8FAFF), surfaceLight.copy(alpha = 0.5f)))
                        )
                        .border(1.dp, borderBlue, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "DOSE AMOUNT",
                            color = textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Amount Controls Row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(surfaceWhite)
                                .border(1.dp, borderLight.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                                .padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Decrement Button
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable {
                                            val newVal = max(0.0, amount - bolusStep)
                                            amount = newVal
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        tint = textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Amount Display
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        String.format("%.1f", amount),
                                        color = textPrimary,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.02).sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(bottom = 4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(surfaceLight)
                                            .border(1.dp, borderBlue, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "U",
                                            color = accentBlue,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                // Increment Button
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(accentBlue)
                                        .clickable {
                                            val newVal = min(maxInsulin, amount + bolusStep)
                                            amount = newVal
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Increase",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Safety Limit Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Safety Limit", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${String.format("%.1f", amount)}/${String.format("%.1f", maxInsulin)} U max",
                                color = textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(borderLight)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = dosePercentage.toFloat())
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accentBlue)
                            )
                        }
                    }
                }

                // QUICK ADD PILLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickAddPill("+${String.format("%.1f", plus1)}") {
                        amount = min(maxInsulin, amount + plus1)
                    }
                    QuickAddPill("+${String.format("%.1f", plus2)}") {
                        amount = min(maxInsulin, amount + plus2)
                    }
                    QuickAddPill("+${String.format("%.1f", plus3)}") {
                        amount = min(maxInsulin, amount + plus3)
                    }
                    QuickAddPill("+${String.format("%.1f", plus3 * 2)}") {
                        amount = min(maxInsulin, amount + plus3 * 2)
                    }
                }

                // OPTIONS
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfaceWhite)
                        .border(1.dp, borderLight, RoundedCornerShape(14.dp))
                ) {
                    Column {
                        // Eating Soon Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { eatingSoon = !eatingSoon }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(surfaceLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("TT", color = accentBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text(
                                        "Eating Soon (TT)",
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Pre-meal temporary target",
                                        color = textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = eatingSoon,
                                onCheckedChange = { eatingSoon = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentBlue,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }

                        Divider(color = borderLight, thickness = 0.5.dp)

                        // Record Only Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { recordOnly = !recordOnly }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("R", color = textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text(
                                        "Record Only",
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Log without pump delivery",
                                        color = textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = recordOnly,
                                onCheckedChange = { recordOnly = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentBlue,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }

                // NOTES FIELD
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, borderLight, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            notes.ifEmpty { "Add notes (e.g. snack, exercise)..." },
                            color = if (notes.isEmpty()) textMuted else textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.Create,
                            contentDescription = "Edit notes",
                            tint = textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // ACTION BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(1.dp, borderLight, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Cancel",
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Deliver Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (canConfirm) Brush.horizontalGradient(listOf(accentBlue, accentBlueDark))
                                else Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFE2E8F0)))
                            )
                            .clickable(enabled = canConfirm) {
                                onConfirm(amount, recordOnly, eatingSoon, notes)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (canConfirm) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_nav_bolus),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                if (amount > 0) "Deliver ${String.format("%.1f", amount)} U" else "Set Dose",
                                color = if (canConfirm) Color.White else textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickAddPill(
    label: String,
    onClick: () -> Unit
) {
    val accentBlue = Color(0xFF2563EB)
    val surfaceLight = Color(0xFFEFF4FF)
    val borderBlue = Color(0xFFBFDBFE)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(surfaceLight)
            .border(1.dp, borderBlue.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = accentBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
