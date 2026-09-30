package com.jupiter.vision.ui.vision

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jupiter.vision.engine.VisionEngine
import com.jupiter.vision.model.VenueItem
import com.jupiter.vision.model.VisionProfileRepository
import com.jupiter.vision.model.WeekScheduleItem
import com.jupiter.vision.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import java.util.Calendar

val IndustrialBezier = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

enum class VisionScreen {
    FIRST_RUN_WELCOME,
    VISION_PROFILE,
    VENUES_LIST,
    VENUE_EDITOR,
    WEEK_CALENDAR,
    ENGINE_WORKING_SAVING,
    ENGINE_STATUS_DIAGNOSTIC,
    NONE
}

@Composable
fun VisionFirstRunWelcomeScreen(
    onBeginInterview: () -> Unit,
    onSilentLearning: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "WELCOME TO VISION HOME",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "The engine learns faster when given user input. You may begin the Vision Interview or let the engine learn by itself, accordingly.",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(42.dp))

            // White background, black text, 0.dp corners, sharp industrial
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .background(Color.White, shape = RectangleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBeginInterview
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BEGIN",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Let the engine learn by itself. (7-day silent learning)",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                color = Color(0xFF777777),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSilentLearning
                    )
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun VisionProfileMainScreen(
    userName: String,
    onNameChange: (String) -> Unit,
    userBirthday: String,
    onOpenBirthday: () -> Unit,
    onOpenVenues: () -> Unit,
    onOpenWeekCalendar: () -> Unit,
    onSaveAndExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Top Right Save & Exit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(1.dp))
                Text(
                    text = "SAVE & EXIT",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSaveAndExit
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title & Subtitle
            Text(
                text = "THIS IS YOUR VISION PROFILE.",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Add as many details as you can. You may exit anytime. The engine will learn the details you didn't include by itself, but it will take some time.",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFF888888)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Name Input: Screen-width input bar, transparent bg, 1px white bottom border, 0.dp corners, uppercase white text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                BasicTextField(
                    value = userName,
                    onValueChange = { onNameChange(it.uppercase()) },
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.5.sp,
                        color = Color.White
                    ),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            if (userName.isEmpty()) {
                                Text(
                                    text = "NAME",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.5.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // 1px white bottom border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2-Column Button Grid: BIRTHDAY, VENUES, WEEK CALENDAR
            // Rectangular buttons, white background, black text, 0.dp corners
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Birthday Button
                VisionGridButton(
                    modifier = Modifier.weight(1f),
                    title = "BIRTHDAY",
                    middleText = if (userBirthday.isNotEmpty()) userBirthday else null,
                    description = "Used for age-based routines.",
                    onClick = onOpenBirthday
                )

                // Venues Button
                VisionGridButton(
                    modifier = Modifier.weight(1f),
                    title = "VENUES",
                    middleText = null,
                    description = "Locations and schedules.",
                    onClick = onOpenVenues
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Week Calendar Button
                VisionGridButton(
                    modifier = Modifier.weight(1f),
                    title = "WEEK CALENDAR",
                    middleText = null,
                    description = "Wake, sleep, and key times.",
                    onClick = onOpenWeekCalendar
                )

                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer Note
            Text(
                text = "New features will arrive with the 2nd generation of Vision engine.",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = Color(0xFF555555),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
fun VisionGridButton(
    modifier: Modifier = Modifier,
    title: String,
    middleText: String? = null,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color.White, shape = RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = Color.Black
        )

        if (!middleText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = middleText,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color(0xFF222222)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = Color(0xFF444444)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 1px black full-width separator line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.Black)
        )
    }
}

@Composable
fun BirthdayModalPopup(
    currentBirthday: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val months = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val years = remember { (1940..2024).toList().reversed() }
    val days = remember { (1..31).toList() }

    var selectedYear by remember { mutableIntStateOf(1995) }
    var selectedMonthIndex by remember { mutableIntStateOf(7) } // August
    var selectedDay by remember { mutableIntStateOf(15) }

    LaunchedEffect(currentBirthday) {
        if (currentBirthday.isNotEmpty()) {
            try {
                val parts = currentBirthday.split(" ")
                if (parts.size >= 3) {
                    val d = parts[0].toIntOrNull() ?: 15
                    val mIdx = months.indexOfFirst { it.equals(parts[1], ignoreCase = true) }.takeIf { it >= 0 } ?: 7
                    val y = parts[2].toIntOrNull() ?: 1995
                    selectedDay = d
                    selectedMonthIndex = mIdx
                    selectedYear = y
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .background(Color(0xFF1A1A1A), shape = RectangleShape)
                .border(1.dp, Color.White, shape = RectangleShape)
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BIRTHDAY",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3 Vertical Scrollable Columns: Day, Month, Year
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Day column
                    ScrollWheelColumn(
                        items = days.map { it.toString() },
                        selectedIndex = (selectedDay - 1).coerceIn(0, days.size - 1),
                        onSelect = { selectedDay = days[it] },
                        modifier = Modifier.weight(1f)
                    )

                    // Month column
                    ScrollWheelColumn(
                        items = months,
                        selectedIndex = selectedMonthIndex.coerceIn(0, months.size - 1),
                        onSelect = { selectedMonthIndex = it },
                        modifier = Modifier.weight(1.4f)
                    )

                    // Year column
                    ScrollWheelColumn(
                        items = years.map { it.toString() },
                        selectedIndex = years.indexOf(selectedYear).takeIf { it >= 0 } ?: 0,
                        onSelect = { selectedYear = years[it] },
                        modifier = Modifier.weight(1.1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Buttons: Left [ CANCEL ] (transparent, white text), Right [ SAVE ] (white bg, black text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(Color.White, shape = RectangleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val formatted = "$selectedDay ${months[selectedMonthIndex]} $selectedYear"
                                    onSave(formatted)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SAVE",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollWheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(items.size) { index ->
            val isSelected = index == selectedIndex
            val alpha = if (isSelected) 1.0f else 0.35f
            Text(
                text = items[index],
                fontFamily = InterFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isSelected) 14.sp else 12.sp,
                color = Color.White.copy(alpha = alpha),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSelect(index)
                    }
                    .padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
fun VenuesScreen(
    venues: List<VenueItem>,
    onAddVenue: () -> Unit,
    onSelectVenue: (VenueItem) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: VENUES on left, [+] on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "←",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack
                            )
                            .padding(end = 12.dp)
                    )
                    Text(
                        text = "VENUES",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp,
                        color = Color.White
                    )
                }

                // Sharp 0.dp [+] button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Color.White, RectangleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAddVenue
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(venues) { venue ->
                    VenueTile(venue = venue, onClick = { onSelectVenue(venue) })
                }
            }
        }
    }
}

@Composable
fun VenueTile(
    venue: VenueItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Color(0xFF222222), RectangleShape)
            .border(1.dp, Color(0xFF333333), RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Satellite map snippet on left (desaturated, high-contrast, tactical grid, no labels)
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(Color(0xFF141414))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Dark tactical satellite contour grid
                for (i in 0..4) {
                    val gy = h * (i / 4f)
                    drawLine(Color(0xFF2B2B2B), Offset(0f, gy), Offset(w, gy), 1f)
                    val gx = w * (i / 4f)
                    drawLine(Color(0xFF2B2B2B), Offset(gx, 0f), Offset(gx, h), 1f)
                }
                // Center location pin / target crosshair
                val cx = w * 0.5f
                val cy = h * 0.5f
                drawCircle(Color.White, 3f, Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = 0.4f), 12f, Offset(cx, cy), style = Stroke(1.2f))
            }
        }

        // Venue Name and Type on right
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = venue.name.uppercase(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = venue.type.uppercase(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = Color(0xFF888888)
            )
        }
    }
}

@Composable
fun VenueEditorScreen(
    venue: VenueItem,
    isNew: Boolean,
    onSave: (VenueItem) -> Unit,
    onDelete: (VenueItem) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(if (isNew) "" else venue.name) }
    var selectedType by remember { mutableStateOf(venue.type) }
    val schedules = remember { venue.schedules.toMutableMap() }
    val context = LocalContext.current

    val types = listOf("JOB", "HOUSE", "GYM", "OTHER")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: EDIT [NAME] or NEW VENUE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNew) "NEW VENUE" else "EDIT ${venue.name.uppercase()}",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = Color.White
                )

                Text(
                    text = "CANCEL",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCancel
                        )
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large desaturated satellite map snippet with draggable/tappable pin
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFF141414))
                            .border(1.dp, Color(0xFF333333))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            // Tactical Grid
                            for (i in 0..8) {
                                val gx = w * (i / 8f)
                                drawLine(Color(0xFF242424), Offset(gx, 0f), Offset(gx, h), 1f)
                            }
                            for (j in 0..6) {
                                val gy = h * (j / 6f)
                                drawLine(Color(0xFF242424), Offset(0f, gy), Offset(w, gy), 1f)
                            }
                            // Radar rings
                            val cx = w * 0.5f
                            val cy = h * 0.5f
                            drawCircle(Color.White.copy(alpha = 0.15f), 30f, Offset(cx, cy), style = Stroke(1f))
                            drawCircle(Color.White.copy(alpha = 0.25f), 15f, Offset(cx, cy), style = Stroke(1f))
                            drawCircle(Color.White, 4f, Offset(cx, cy))
                        }
                    }
                }

                // Name Field
                item {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        textStyle = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = Color.White
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                if (name.isEmpty()) {
                                    Text(
                                        text = "VENUE NAME",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White)
                    )
                }

                // Type selector: [ JOB ] [ HOUSE ] [ GYM ] [ OTHER ]
                // 0.dp corners, white border, transparent fill; when selected fills white with black text
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        types.forEach { t ->
                            val isSel = selectedType.equals(t, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(if (isSel) Color.White else Color.Transparent)
                                    .border(1.dp, Color.White, RectangleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedType = t
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = t,
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = if (isSel) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                // Schedule section: LEAVE TIME / ARRIVE TIME
                item {
                    Column {
                        Text(
                            text = "LEAVE TIME / ARRIVE TIME",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If you don't set this, the engine will learn it via geofencing.",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = Color(0xFF777777)
                        )
                    }
                }

                // 7 Rows: Monday to Sunday
                items(VisionProfileRepository.DAYS_OF_WEEK) { day ->
                    val curPair = schedules[day] ?: Pair("07:45", "08:15")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = day,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = Color(0xFFDDDDDD),
                            modifier = Modifier.width(100.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Leave Time Button
                            TimePill(
                                time = curPair.first,
                                onSelect = { newT ->
                                    schedules[day] = Pair(newT, curPair.second)
                                }
                            )

                            // Arrive Time Button
                            TimePill(
                                time = curPair.second,
                                onSelect = { newT ->
                                    schedules[day] = Pair(curPair.first, newT)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Buttons: Left [ DELETE ] (only if not Home or Work), Right [ SAVE ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isDeletable = !isNew && venue.name.lowercase() != "home" && venue.name.lowercase() != "work"
                if (isDeletable) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .border(1.dp, Color(0xFF883333), RectangleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onDelete(venue) }
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DELETE",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = Color(0xFFFF5555)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .background(Color.White, RectangleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                venue.name = name.ifEmpty { "VENUE" }
                                venue.type = selectedType
                                venue.schedules = schedules
                                onSave(venue)
                            }
                        )
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SAVE",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun TimePill(
    time: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .border(1.dp, Color(0xFF444444), RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val parts = time.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(context, { _, selectedH, selectedM ->
                    val str = String.format("%02d:%02d", selectedH, selectedM)
                    onSelect(str)
                }, h, m, true).show()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = time,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = Color.White
        )
    }
}

@Composable
fun WeekCalendarScreen(
    calendarMap: Map<String, WeekScheduleItem>,
    onSaveSchedule: (String, WeekScheduleItem) -> Unit,
    onBack: () -> Unit
) {
    val days = VisionProfileRepository.DAYS_OF_WEEK

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "←",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack
                        )
                        .padding(end = 12.dp)
                )
                Text(
                    text = "WEEK CALENDAR",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.5.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Column Header: WAKE, SLEEP, OTHER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DAY",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = "WAKE",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "SLEEP",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "OTHER",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(days) { day ->
                    val item = calendarMap[day] ?: WeekScheduleItem()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = day.take(3),
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.width(90.dp)
                        )

                        TimePill(
                            time = item.wakeTime,
                            onSelect = { newT ->
                                onSaveSchedule(day, item.copy(wakeTime = newT))
                            }
                        )

                        TimePill(
                            time = item.sleepTime,
                            onSelect = { newT ->
                                onSaveSchedule(day, item.copy(sleepTime = newT))
                            }
                        )

                        TimePill(
                            time = if (item.otherEvent.isNotEmpty()) item.otherEvent else "--:--",
                            onSelect = { newT ->
                                onSaveSchedule(day, item.copy(otherEvent = newT))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VisionEngineWorkingTransitionScreen(
    onFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2500L)
        onFinished()
    }

    val transition = rememberInfiniteTransition(label = "RadarGhostEcho")
    val pulseRadius by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Radius"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Radar pulse Ghost Echo expanding from center
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val maxR = size.width * 0.45f
            val r = maxR * pulseRadius
            val alpha = (1f - pulseRadius) * 0.35f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(2f)
            )
            drawCircle(
                color = Color.White.copy(alpha = (1f - pulseRadius) * 0.15f),
                radius = r * 0.5f,
                center = Offset(cx, cy),
                style = Stroke(1.5f)
            )
        }

        Text(
            text = "VISION ENGINE IS WORKING...",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 2.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun VisionHomeLongPressMenu(
    onOpenProfile: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenCorrection: () -> Unit,
    onDisableForToday: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .padding(start = 24.dp)
                .width(240.dp)
                .background(Color(0xFF1A1A1A), RectangleShape)
                .border(1.dp, Color.White, RectangleShape)
                .padding(vertical = 12.dp)
        ) {
            VisionMenuItem(label = "VISION PROFILE", onClick = onOpenProfile)
            VisionMenuItem(label = "VISION ENGINE STATUS", onClick = onOpenStatus)
            VisionMenuItem(label = "CORRECT ENGINE", onClick = onOpenCorrection)
            VisionMenuItem(label = "DISABLE FOR TODAY", onClick = onDisableForToday)
        }
    }
}

@Composable
fun VisionMenuItem(
    label: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun VisionEngineStatusScreen(
    snapshot: VisionEngine.EngineSnapshot,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val learningDay = VisionProfileRepository.DAYS_OF_WEEK
    val dayCount = VisionEngine.getLearningDay(context)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VISION ENGINE STATUS",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.5.sp,
                    color = Color.White
                )

                Text(
                    text = "CLOSE",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose
                        )
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            DiagnosticItem(title = "ACTIVE STATE", value = snapshot.state.displayName)
            DiagnosticItem(title = "STATUS LINE", value = snapshot.statusText)
            DiagnosticItem(title = "LEARNING TIMELINE", value = "DAY $dayCount OF 7 ${if (dayCount <= 7) "(OBSERVATION ACTIVE)" else "(REFINEMENT)"}")
            DiagnosticItem(title = "ACTIVE ROUTINE", value = if (snapshot.state == VisionEngine.VisionState.WORK) "WORKDAY ROUTINE 1" else if (snapshot.state == VisionEngine.VisionState.HOME) "HOME LEISURE ROUTINE" else "SYSTEM DETERMINISTIC")
            DiagnosticItem(title = "OBSERVED GEOFENCES", value = "HOME (ACTIVE), WORK (INFERRED)")
            DiagnosticItem(title = "SCREEN-ON INFERENCE", value = "LOW DURING WORK HOURS (08:35 - 15:30)")
            DiagnosticItem(title = "LIFESTYLE CYCLE", value = "DETERMINISTIC PRIORITY CHAIN (NIGHT > GYM > WORK > HOME > SETTLE > TRANSIT > FOCUS > LEARNING > IDLE)")

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "The engine does not ask. It observes, infers, and acts.",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                color = Color(0xFF666666)
            )
        }
    }
}

@Composable
fun DiagnosticItem(title: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = Color(0xFF777777)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}

@Composable
fun VisionEngineCorrectionDialog(
    currentState: VisionEngine.VisionState,
    onCorrect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .background(Color(0xFF1A1A1A), RectangleShape)
                .border(1.dp, Color.White, RectangleShape)
                .padding(20.dp)
        ) {
            if (step == 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CORRECT ENGINE",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "The engine currently believes you are in:\n${currentState.displayName}",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFCCCCCC)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Is this correct?",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .border(1.dp, Color.White, RectangleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "YES",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(Color.White, RectangleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { step = 2 }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NO",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WHAT IS THE CORRECTION?",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val options = listOf("This is not Work", "This is not Home", "This is not Transit", "Other")
                    options.forEach { opt ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF242424), RectangleShape)
                                .border(1.dp, Color(0xFF444444), RectangleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onCorrect(opt)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt.uppercase(),
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
