package app.aaps.plugins.main.general.overview.glass

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max

@Composable
fun GlassInsulinDialogScreen(
    isDark: Boolean,
    maxInsulin: Double,
    bolusStep: Double,
    plus1: Double,
    plus2: Double,
    plus3: Double,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, recordOnly: Boolean, eatingSoon: Boolean, notes: String) -> Unit
) {
    // Mesmas cores do GlassLoopDashboardScreen
    val bgColorTop = if (isDark) Color(0xFF070E1B) else Color(0xFFF1F5F9)
    val bgColorBot = if (isDark) Color(0xFF0B1424) else Color(0xFFE8EEF8)
    val cardBgStart = if (isDark) Color(0x24FFFFFF) else Color(0xF5FFFFFF)
    val cardBgEnd = if (isDark) Color(0x0EFFFFFF) else Color(0xE0EEF2FA)
    val borderCard = if (isDark) Color(0x26FFFFFF) else Color(0xB8CBD5E1)
    val textBright = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val cellBg = if (isDark) Color(0x12FFFFFF) else Color(0xFFF1F5F9)
    val cellBorder = if (isDark) Color(0x1AFFFFFF) else Color(0xFFE2E8F0)
    val dividerColor = if (isDark) Color(0x1AFFFFFF) else Color(0xFFE2E8F0)
    val skyBlue = Color(0xFF38BDF8)
    val emerald = Color(0xFF10B981)
    val skyBlueBg = if (isDark) Color(0xFF1E3A5F) else Color(0xFFDBEAFE)
    val iconBgGreen = if (isDark) Color(0xFF0D3320) else Color(0xFFD1FAE5)

    var amount by remember { mutableStateOf("0.0") }
    var recordOnly by remember { mutableStateOf(false) }
    var eatingSoon by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val canConfirm = amountValue > 0 || eatingSoon

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .shadow(24.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(bgColorTop, bgColorBot, bgColorTop))
                )
                .border(1.dp, borderCard, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(skyBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("U", color = skyBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Insulin", color = textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Bolus delivery", color = textMuted, fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = textMuted, fontSize = 12.sp)
                    }
                }

                Divider(color = dividerColor, thickness = 0.5.dp)

                // AMOUNT DISPLAY
                GlassCardInner(isDark, cardBgStart, cardBgEnd, borderCard) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AMOUNT", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(cellBg)
                                    .border(1.dp, cellBorder, CircleShape)
                                    .clickable {
                                        val current = amount.toDoubleOrNull() ?: 0.0
                                        val newVal = max(0.0, current - bolusStep)
                                        amount = String.format("%.1f", newVal)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Remove, null, tint = textMuted, modifier = Modifier.size(18.dp))
                            }

                            OutlinedTextField(
                                value = amount,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        amount = newValue
                                    }
                                },
                                modifier = Modifier.width(120.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textBright,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = skyBlue,
                                    unfocusedBorderColor = cellBorder,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(cellBg)
                                    .border(1.dp, cellBorder, CircleShape)
                                    .clickable {
                                        val current = amount.toDoubleOrNull() ?: 0.0
                                        val newVal = max(0.0, current + bolusStep)
                                        amount = String.format("%.1f", newVal)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("U (max: ${String.format("%.1f", maxInsulin)})", color = textMuted, fontSize = 10.sp)
                    }
                }

                // QUICK ADD BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAddButton("+${String.format("%.1f", plus1)}", cellBg, cellBorder, skyBlue) {
                        val current = amount.toDoubleOrNull() ?: 0.0
                        amount = String.format("%.1f", max(0.0, current + plus1))
                    }
                    QuickAddButton("+${String.format("%.1f", plus2)}", cellBg, cellBorder, skyBlue) {
                        val current = amount.toDoubleOrNull() ?: 0.0
                        amount = String.format("%.1f", max(0.0, current + plus2))
                    }
                    QuickAddButton("+${String.format("%.1f", plus3)}", cellBg, cellBorder, skyBlue) {
                        val current = amount.toDoubleOrNull() ?: 0.0
                        amount = String.format("%.1f", max(0.0, current + plus3))
                    }
                }

                // OPTIONS
                GlassCardInner(isDark, cardBgStart, cardBgEnd, borderCard) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Eating Soon TT", color = textBright, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = eatingSoon,
                                onCheckedChange = { eatingSoon = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = emerald,
                                    checkedTrackColor = emerald.copy(alpha = 0.3f),
                                    uncheckedThumbColor = textMuted,
                                    uncheckedTrackColor = cellBg
                                )
                            )
                        }
                        Divider(color = dividerColor, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Record Only", color = textBright, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = recordOnly,
                                onCheckedChange = { recordOnly = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = skyBlue,
                                    checkedTrackColor = skyBlue.copy(alpha = 0.3f),
                                    uncheckedThumbColor = textMuted,
                                    uncheckedTrackColor = cellBg
                                )
                            )
                        }
                    }
                }

                // NOTES
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Notes (optional)", color = textMuted, fontSize = 12.sp) },
                    textStyle = LocalTextStyle.current.copy(color = textBright, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = skyBlue.copy(alpha = 0.5f),
                        unfocusedBorderColor = cellBorder,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    maxLines = 2
                )

                // CONFIRM BUTTON
                Button(
                    onClick = { onConfirm(amountValue, recordOnly, eatingSoon, notes) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canConfirm) skyBlue else cellBg,
                        contentColor = if (canConfirm) Color(0xFF082F49) else textMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (amountValue > 0) "Deliver ${String.format("%.1f", amountValue)}U" else "Set Target",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickAddButton(
    label: String,
    cellBg: Color,
    cellBorder: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = cellBg,
        border = BorderStroke(1.dp, cellBorder),
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 10.dp),
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
