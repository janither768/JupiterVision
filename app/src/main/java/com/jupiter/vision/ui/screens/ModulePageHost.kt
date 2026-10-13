package com.jupiter.vision.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.jupiter.vision.tracemachine.ui.TraceMachineScreen
import com.jupiter.vision.ui.components.ModuleChangerPlate

/**
 * Host surface for Page 0 of JupiterVision:
 * Houses interchangeable modules ("DoTPro", "TraceMachine") switched via the
 * Deliverable 1 Module Changer Plate radial dial overlay.
 */
@Composable
fun ModulePageHost(
    modifier: Modifier = Modifier
) {
    var activeModule by rememberSaveable { mutableStateOf("DoTPro") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .testTag("module_page_host")
    ) {
        // Active Module Surface
        when (activeModule) {
            "DoTPro" -> {
                DoTProModuleScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
            "TraceMachine" -> {
                TraceMachineScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                DoTProModuleScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // DELIVERABLE 1 — MODULE CHANGER PLATE
        // Sits on left edge as a sliver overlay over module content
        ModuleChangerPlate(
            activeModule = activeModule,
            onModuleSelected = { selected ->
                activeModule = selected
            },
            modules = listOf("DoTPro", "TraceMachine")
        )
    }
}
