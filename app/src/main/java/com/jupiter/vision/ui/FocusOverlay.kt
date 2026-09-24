package com.jupiter.vision.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.util.SystemControls

@Composable
fun FocusOverlay(
    tile: TileModel?,
    onDismiss: () -> Unit,
    onUpdateTile: ((TileModel) -> Unit)? = null,
) {
    if (tile == null) return
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.80f))
            .pointerInput(tile.id) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.88f)
                .background(tile.accentColor)
                .border(1.5.dp, Color.White.copy(alpha = 0.2f))
                .pointerInput(tile.id) { detectTapGestures { } }
        ) {
            Column(
                Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = tile.label.uppercase(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "UNITS: ${tile.colSpan}x${tile.rowSpan} ${if (tile.isMicro) "(MICRO)" else ""}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("focus_close_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TELEMETRY & PREVIEW",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (tile.contentLines.isNotEmpty()) {
                            tile.contentLines.forEach { line ->
                                Text(
                                    text = "· $line",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Text(
                                text = "· System Ready\n· Quick Launch Enabled\n· Cobalt Palette",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Launch Button
                Button(
                    onClick = {
                        if (tile.packageName != null) {
                            SystemControls.launchPackage(context, tile.packageName)
                        } else {
                            SystemControls.launchSystemAction(context, tile.id)
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("focus_launch_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("LAUNCH APPLICATION", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }

                if (onUpdateTile != null) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val (newCol, newRow, isMicro) = when {
                                tile.isMicro -> Triple(2, 2, false)
                                tile.colSpan == 2 && tile.rowSpan == 2 -> Triple(4, 2, false)
                                tile.colSpan == 4 && tile.rowSpan == 2 -> Triple(4, 4, false)
                                tile.colSpan == 4 && tile.rowSpan == 4 -> Triple(2, 4, false)
                                else -> Triple(1, 1, true)
                            }
                            onUpdateTile(tile.copy(colSpan = newCol, rowSpan = newRow, isMicro = isMicro))
                        },
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CYCLE SIZE (${tile.colSpan}x${tile.rowSpan})", color = Color.White, fontSize = 10.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val newActive = !tile.hasActivity
                            val newLines = if (newActive && tile.contentLines.isEmpty()) listOf("Active Telemetry", "Status: Nominal") else tile.contentLines
                            onUpdateTile(tile.copy(hasActivity = newActive, contentLines = newLines))
                        },
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (tile.hasActivity) "ACTIVITY: ACTIVE" else "ACTIVITY: IDLE", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
