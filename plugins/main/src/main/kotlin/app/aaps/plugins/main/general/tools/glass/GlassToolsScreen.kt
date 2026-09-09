package app.aaps.plugins.main.general.tools.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ToolItemData(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit
)

@Composable
fun GlassToolsScreen(
    isDark: Boolean,
    onOpenActions: () -> Unit,
    onOpenOref: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenNsClient: () -> Unit,
    onOpenTidepool: () -> Unit,
    onOpenXdrip: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenXdripBg: () -> Unit
) {
    var viewMode by remember { mutableStateOf("grid") }

    val toolsList = remember(
        onOpenActions, onOpenOref, onOpenProfile, onOpenAutomation,
        onOpenNsClient, onOpenTidepool, onOpenXdrip, onOpenMaintenance, onOpenXdripBg
    ) {
        listOf(
            ToolItemData("actions", "ACTIONS", Icons.Default.Bolt, Color(0xFFF59E0B), onOpenActions),
            ToolItemData("oref", "RAPID-ACTING OREF", Icons.Default.Science, Color(0xFF0284C7), onOpenOref),
            ToolItemData("profile", "PROFILE", Icons.Default.Tune, Color(0xFF6366F1), onOpenProfile),
            ToolItemData("automation", "AUTOMATION", Icons.Default.Schedule, Color(0xFFA855F7), onOpenAutomation),
            ToolItemData("nsclient", "NSCLIENT", Icons.Default.CloudUpload, Color(0xFF06B6D4), onOpenNsClient),
            ToolItemData("tidepool", "TIDEPOOL", Icons.Default.Storage, Color(0xFF10B981), onOpenTidepool),
            ToolItemData("xdrip", "XDRIP+", Icons.Default.Sensors, Color(0xFF3B82F6), onOpenXdrip),
            ToolItemData("maintenance", "MAINTENANCE", Icons.Default.Build, Color(0xFFF97316), onOpenMaintenance),
            ToolItemData("xdrip_bg", "XDRIP+ BG", Icons.Default.ShowChart, Color(0xFF0EA5E9), onOpenXdripBg)
        )
    }

    val cardBg = if (isDark) Color(0xD91E293B) else Color(0xF2FFFFFF)
    val cardBorder = if (isDark) Color(0x33FFFFFF) else Color(0x66CBD5E1)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (isDark) 4.dp else 2.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tools",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0x33000000) else Color(0x1A000000))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (viewMode == "grid") Color(0xFF0284C7) else Color.Transparent)
                            .clickable { viewMode = "grid" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Grade",
                            tint = if (viewMode == "grid") Color.White else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (viewMode == "pills") Color(0xFF0284C7) else Color.Transparent)
                            .clickable { viewMode = "pills" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewList,
                            contentDescription = "Lista",
                            tint = if (viewMode == "pills") Color.White else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (viewMode == "grid") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(toolsList) { tool ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.95f)
                            .shadow(if (isDark) 3.dp else 1.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                            .clickable { tool.onClick() }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(tool.accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, tool.accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = null,
                                    tint = tool.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = tool.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                color = textColor,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(toolsList) { tool ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(if (isDark) 3.dp else 1.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                            .clickable { tool.onClick() }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tool.accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, tool.accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = null,
                                    tint = tool.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Text(
                                text = tool.name,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                color = textColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
