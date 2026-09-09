package app.aaps.plugins.main.general.overview.glass

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun GlassSensorInsertScreen(
    uiState: GlassSensorInsertState,
    onBack: () -> Unit,
    onDateSet: (Int, Int, Int) -> Unit,
    onTimeSet: (Int, Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    isDark: Boolean
) {
    val isDarkMode = isDark

    val bgColorTop = if (isDarkMode) Color(0xFF070E1B) else Color(0xFFF1F5F9)
    val bgColorBot = if (isDarkMode) Color(0xFF0B1424) else Color(0xFFE8EEF8)
    val cardBgStart = if (isDarkMode) Color(0x24FFFFFF) else Color(0xF5FFFFFF)
    val cardBgEnd = if (isDarkMode) Color(0x0EFFFFFF) else Color(0xE0EEF2FA)
    val borderCard = if (isDarkMode) Color(0x26FFFFFF) else Color(0xB8CBD5E1)
    val textPrimary = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val textBright = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val textFieldBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val textFieldBorder = if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
    val tealColor = Color(0xFF0D9488)
    val skyBlue = Color(0xFF38BDF8)
    val skyBlueDark = Color(0xFF0284C7)

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(bgColorTop, bgColorBot, bgColorTop))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TOP BAR
            Text(
                text = "CGM Sensor Insert",
                color = textBright,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                modifier = Modifier.fillMaxWidth()
            )

            // ICON + DESCRIPTION CARD
            GlassCardInner(isDarkMode = isDarkMode, cardBgStart = cardBgStart, cardBgEnd = cardBgEnd, borderCard = borderCard) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(tealColor.copy(alpha = 0.15f))
                            .border(1.dp, tealColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            tint = tealColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Register Sensor Insertion",
                        color = textBright,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Date and time when the sensor was inserted",
                        color = textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // DATE + TIME ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // DATE CARD
                GlassCardInner(
                    isDarkMode = isDarkMode,
                    cardBgStart = cardBgStart,
                    cardBgEnd = cardBgEnd,
                    borderCard = borderCard,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = uiState.eventTimestamp }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth -> onDateSet(year, month, dayOfMonth) },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = tealColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(text = "DATE", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        }
                        Text(
                            text = uiState.formattedDate,
                            color = textBright,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // TIME CARD
                GlassCardInner(
                    isDarkMode = isDarkMode,
                    cardBgStart = cardBgStart,
                    cardBgEnd = cardBgEnd,
                    borderCard = borderCard,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = uiState.eventTimestamp }
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute -> onTimeSet(hourOfDay, minute) },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = skyBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(text = "TIME", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        }
                        Text(
                            text = uiState.formattedTime,
                            color = textBright,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // NOTES CARD
            GlassCardInner(isDarkMode = isDarkMode, cardBgStart = cardBgStart, cardBgEnd = cardBgEnd, borderCard = borderCard) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "NOTES", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = onNotesChange,
                        placeholder = { Text("Optional", color = textMuted, fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = tealColor.copy(alpha = 0.6f),
                            unfocusedBorderColor = textFieldBorder,
                            cursorColor = skyBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // SAVE BUTTON
            val saveButtonGradient = Brush.horizontalGradient(
                listOf(skyBlue, skyBlueDark)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (uiState.isSaved) Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669))) else saveButtonGradient
                    )
                    .clickable(enabled = !uiState.isSaving && !uiState.isSaved) { onSave() },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else if (uiState.isSaved) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Registered!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("CONFIRM", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            // ERROR MESSAGE
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun GlassCardInner(
    isDarkMode: Boolean,
    cardBgStart: Color,
    cardBgEnd: Color,
    borderCard: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(cardBgStart, cardBgEnd)))
            .border(1.dp, borderCard, RoundedCornerShape(16.dp)),
        content = content
    )
}
