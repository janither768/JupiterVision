package com.jupiter.vision.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.util.AppLoader
import com.jupiter.vision.util.SystemControls

enum class PanelKind { NONE, CONTROLS, APPS }

@Composable
fun EdgePanelZones(
    openPanel: PanelKind,
    onPanelChange: (PanelKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
    ) {
        // Upper 50% — Controls Zone
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (openPanel == PanelKind.CONTROLS) onPanelChange(PanelKind.NONE)
                            else if (openPanel == PanelKind.NONE) onPanelChange(PanelKind.CONTROLS)
                        }
                    ) { change, dragAmount ->
                        if (dragAmount.x < -10f && openPanel == PanelKind.NONE) {
                            onPanelChange(PanelKind.CONTROLS)
                            change.consume()
                        }
                    }
                }
                .clickable {
                    onPanelChange(if (openPanel == PanelKind.CONTROLS) PanelKind.NONE else PanelKind.CONTROLS)
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.5f))
            )
        }

        // Lower 50% — App Lister Zone
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (openPanel == PanelKind.APPS) onPanelChange(PanelKind.NONE)
                            else if (openPanel == PanelKind.NONE) onPanelChange(PanelKind.APPS)
                        }
                    ) { change, dragAmount ->
                        if (dragAmount.x < -10f && openPanel == PanelKind.NONE) {
                            onPanelChange(PanelKind.APPS)
                            change.consume()
                        }
                    }
                }
                .clickable {
                    onPanelChange(if (openPanel == PanelKind.APPS) PanelKind.NONE else PanelKind.APPS)
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(Color(0xFFFF6A00).copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun PanelSurface(
    panel: PanelKind,
    onClose: () -> Unit,
    onPinApp: ((AppInfo) -> Unit)? = null,
    onOpenPreferences: (() -> Unit)? = null,
) {
    if (panel == PanelKind.NONE) return
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var batteryPercent by remember { mutableIntStateOf(85) }

    LaunchedEffect(panel) {
        if (panel == PanelKind.APPS) {
            installedApps = AppLoader.loadInstalledApps(context)
        } else if (panel == PanelKind.CONTROLS) {
            batteryPercent = SystemControls.getBatteryLevel(context)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.90f)
                .background(Color(0xFF0E0E12))
                .border(1.dp, Color(0xFF24242C))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            when (panel) {
                PanelKind.CONTROLS -> {
                    ControlsContent(
                        batteryPercent = batteryPercent,
                        onClose = onClose,
                        onOpenPreferences = onOpenPreferences
                    )
                }
                PanelKind.APPS -> {
                    AppsContent(
                        apps = installedApps,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onClose = onClose,
                        onPinApp = onPinApp
                    )
                }
                PanelKind.NONE -> {}
            }
        }
    }
}

@Composable
private fun ControlsContent(
    batteryPercent: Int,
    onClose: () -> Unit,
    onOpenPreferences: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val controls = listOf(
        ControlAction("TORCH", "Flashlight", Icons.Default.FlashlightOn, 0xFF4A4A4A) {
            SystemControls.toggleTorch(context)
        },
        ControlAction("WIFI", "Wi-Fi", Icons.Default.Wifi, 0xFF1F3A5F) {
            SystemControls.launchSystemAction(context, "wifi")
        },
        ControlAction("BT", "Bluetooth", Icons.Default.Bluetooth, 0xFF1F3A5F) {
            SystemControls.launchSystemAction(context, "bluetooth")
        },
        ControlAction("SOUND", "Audio Levels", Icons.AutoMirrored.Filled.VolumeUp, 0xFF2E5F3A) {
            SystemControls.launchSystemAction(context, "sound")
        },
        ControlAction("DISP", "Brightness", Icons.Default.BrightnessMedium, 0xFF5F4A2E) {
            SystemControls.launchSystemAction(context, "display")
        },
        ControlAction("NOTIF", "Notifications", Icons.Default.Notifications, 0xFF5F2E2E) {
            SystemControls.launchSystemAction(context, "notifications")
        },
        ControlAction("SYS", "System Settings", Icons.Default.Settings, 0xFF2B2B2B) {
            SystemControls.launchSystemAction(context, "set")
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JUPITER // CONTROLS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "SYSTEM TELEMETRY & HARDWARE",
                    color = Color(0xFF00E5FF),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("controls_close_btn")) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Power Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16161C))
                .border(1.dp, Color(0xFF262630))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "BATTERY: $batteryPercent%",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "ONLINE",
                color = Color(0xFF4CAF50),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "QUICK TOGGLES",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(controls) { ctrl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color(ctrl.accentColor))
                        .border(1.dp, Color.White.copy(alpha = 0.1f))
                        .clickable { ctrl.action() }
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            ctrl.icon,
                            contentDescription = ctrl.title,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = ctrl.tag,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = ctrl.title,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsContent(
    apps: List<AppInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
    onPinApp: ((AppInfo) -> Unit)? = null
) {
    val context = LocalContext.current
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JUPITER // APPS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${filtered.size} PACKAGES AVAILABLE",
                    color = Color(0xFFFF6A00),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("apps_close_btn")) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text("Search packages...", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFFF6A00))
            },
            singleLine = true,
            shape = RoundedCornerShape(0.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141418),
                unfocusedContainerColor = Color(0xFF141418),
                focusedBorderColor = Color(0xFFFF6A00),
                unfocusedBorderColor = Color(0xFF2E2E36),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO MATCHING APPLICATIONS",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF14141A))
                            .border(1.dp, Color(0xFF22222A))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFF252530)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.label.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = app.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onPinApp != null) {
                                IconButton(
                                    onClick = {
                                        onPinApp(app)
                                        onClose()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Pin",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }

                            Button(
                                onClick = {
                                    SystemControls.launchPackage(context, app.packageName)
                                    onClose()
                                },
                                shape = RoundedCornerShape(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF6A00),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("OPEN", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ControlAction(
    val tag: String,
    val title: String,
    val icon: ImageVector,
    val accentColor: Long,
    val action: () -> Unit
)
