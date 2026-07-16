package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.example.data.StampTemplate
import com.example.ui.GeoStampViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    viewModel: GeoStampViewModel,
    onNavigateBack: () -> Unit
) {
    val draftFile by viewModel.currentCapturedDraftFile.collectAsState()
    val editorModel by viewModel.editorModel.collectAsState()
    val activeTemplate by viewModel.activeTemplate.collectAsState()
    val templatesList by viewModel.allTemplates.collectAsState(initial = emptyList())

    if (draftFile == null || editorModel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active draft media to stamp.")
        }
        return
    }

    // Exact required customizable fields
    var locationNameText by remember { mutableStateOf(editorModel!!.customLocationName) }
    var customAddressText by remember { mutableStateOf(editorModel!!.address) }
    var customLatText by remember { mutableStateOf(editorModel!!.latitude.toString()) }
    var customLonText by remember { mutableStateOf(editorModel!!.longitude.toString()) }
    var dayOfWeekText by remember { mutableStateOf(editorModel!!.customDayOfWeek) }
    var countryFlagText by remember { mutableStateOf(editorModel!!.countryFlag) }
    var dateStrText by remember { mutableStateOf(editorModel!!.customDateStr) }
    var timeStrText by remember { mutableStateOf(editorModel!!.customTimeStr) }
    var flagDropdownExpanded by remember { mutableStateOf(false) }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    // Synchronize custom inputs dynamically inside the ViewModel
    LaunchedEffect(locationNameText, dayOfWeekText, countryFlagText, dateStrText, timeStrText, customAddressText, customLatText, customLonText) {
        viewModel.updateEditorCustomFields(
            locationName = locationNameText,
            dayOfWeek = dayOfWeekText,
            flag = countryFlagText,
            dateStr = dateStrText,
            timeStr = timeStrText
        )
        viewModel.updateEditorAddress(customAddressText)
        val latVal = customLatText.toDoubleOrNull() ?: editorModel!!.latitude
        val lonVal = customLonText.toDoubleOrNull() ?: editorModel!!.longitude
        viewModel.updateEditorCoords(latVal, lonVal)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Media Stamp", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Discard Draft")
                    }
                    Button(
                        onClick = {
                            viewModel.finalizeAndSaveStamping("dd/MM/yyyy", false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A)),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("apply_stamp_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply", modifier = Modifier.padding(end = 6.dp))
                        Text("Save & Apply Stamp", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Live Real-Time Visual Feedback Canvas containing original draft file (Image/Video)
            Text(
                "Media View & Stamped Overlay Preview",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (draftFile!!.absolutePath.endsWith(".mp4")) {
                        // Looping VideoView for live video review inside Editor screen!
                        AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    setVideoURI(Uri.parse(draftFile!!.absolutePath))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(model = draftFile),
                            contentDescription = "Draft Image Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Combined Stamp Overlay Layout rendering over draft visual
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        StampOverlayLayout(
                            metadata = editorModel!!,
                            template = activeTemplate ?: StampTemplate(id = 1, name = "Classic Template", templateType = "CLASSIC")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Template selector bar
            Text(
                "Select Active Stamp Template Preset",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templatesList.size) { index ->
                    val temp = templatesList[index]
                    val isSelected = activeTemplate?.id == temp.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setActiveTemplate(temp) },
                        label = { Text(temp.name) }
                    )
                }
            }

            // Customization Options Panel Content
            Text(
                "Stamping Customization Values",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Section 1: Location Settings
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("1. Geographic Location", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = locationNameText,
                        onValueChange = { locationNameText = it },
                        label = { Text("Location Name / Landmark title") },
                        leadingIcon = { Icon(Icons.Default.PinDrop, "Location Title") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = customAddressText,
                        onValueChange = { customAddressText = it },
                        label = { Text("Full Survey Address details") },
                        leadingIcon = { Icon(Icons.Default.Room, "Address details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Section 2: Coordinates Details
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("2. Coordinates Values", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customLatText,
                            onValueChange = { customLatText = it },
                            label = { Text("Latitude") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customLonText,
                            onValueChange = { customLonText = it },
                            label = { Text("Longitude") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 3: Country Flag Selector Dropdown
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("3. Country Flag associated with location", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    val flags = listOf(
                        Pair("🇮🇳", "India (IN)"), Pair("🇺🇸", "United States (US)"), Pair("🇬🇧", "United Kingdom (UK)"), 
                        Pair("🇦🇪", "United Arab Emirates (UAE)"), Pair("🇨🇦", "Canada (CA)"), Pair("🇦🇺", "Australia (AU)"), 
                        Pair("🇸🇬", "Singapore (SG)"), Pair("🇯🇵", "Japan (JP)"), Pair("🇩🇪", "Germany (DE)"), 
                        Pair("🇫🇷", "France (FR)")
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = flags.firstOrNull { it.first == countryFlagText }?.let { "${it.first}  ${it.second}" } ?: countryFlagText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Country Flag Option") },
                            leadingIcon = { Icon(Icons.Default.Security, "Flag Icon") },
                            trailingIcon = {
                                IconButton(onClick = { flagDropdownExpanded = true }) {
                                    Icon(if (flagDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, "Toggle Dropdown")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Transparent overlay to capture click events and show dropdown
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { flagDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = flagDropdownExpanded,
                            onDismissRequest = { flagDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            flags.forEach { pair ->
                                DropdownMenuItem(
                                    text = { Text("${pair.first}   ${pair.second}", fontSize = 14.sp) },
                                    onClick = {
                                        countryFlagText = pair.first
                                        flagDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Day of the Week Selector Dropdown
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("4. Day of the week", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = dayOfWeekText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day of the week") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, "Day Icon") },
                            trailingIcon = {
                                IconButton(onClick = { dayDropdownExpanded = true }) {
                                    Icon(if (dayDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, "Toggle Dropdown")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Transparent overlay to capture click events and show dropdown
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { dayDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = dayDropdownExpanded,
                            onDismissRequest = { dayDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            daysOfWeek.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day, fontSize = 14.sp) },
                                    onClick = {
                                        dayOfWeekText = day
                                        dayDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: Date & Time details
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("5. Custom Date & Time details", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dateStrText,
                            onValueChange = { dateStrText = it },
                            label = { Text("Date (dd/MM/yyyy)") },
                            modifier = Modifier.weight(1.1f)
                        )
                        OutlinedTextField(
                            value = timeStrText,
                            onValueChange = { timeStrText = it },
                            label = { Text("Time (e.g. 05:24 pm)") },
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
