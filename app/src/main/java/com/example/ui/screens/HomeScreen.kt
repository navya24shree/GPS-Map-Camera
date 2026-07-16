package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FavoriteLocation
import com.example.ui.GeoStampViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GeoStampViewModel,
    onNavigate: (String) -> Unit
) {
    val latitude by viewModel.liveLatitude.collectAsState()
    val longitude by viewModel.liveLongitude.collectAsState()
    val address by viewModel.liveAddress.collectAsState()
    val altitude by viewModel.liveAltitude.collectAsState()
    val compassDirection by viewModel.liveCompassDirection.collectAsState()
    val weatherTemp by viewModel.liveWeatherTemp.collectAsState()
    val weatherCond by viewModel.liveWeatherCondition.collectAsState()
    val isGpsActive by viewModel.isGpsEnabled.collectAsState()
    val favorites by viewModel.allFavoriteLocations.collectAsState(initial = emptyList())
    val activeTemplate by viewModel.activeTemplate.collectAsState()

    var showAddFavDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(32.dp)
                                .background(Color(0xFF0F131E), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF2DF380), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = "Logo Indicator",
                                tint = Color(0xFF2DF380),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            "MapSeal GPS Camera",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onNavigate("SETTINGS") },
                        modifier = Modifier.testTag("settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
            // Camera Hub Card - Start Capture!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2C5364),
                                Color(0xFF203A43),
                                Color(0xFF0F2027)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF2DF380).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { onNavigate("CAMERA") }
                    .testTag("home_camera_trigger_card"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Launch Camera",
                        tint = Color(0xFF2DF380),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "OPEN FIELD CAMERA",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Click to capture styled stamped photo",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Telemetry Readout Box
            Text(
                "Live Field Telemetry",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isGpsActive) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                contentDescription = "GPS Status Indicator",
                                tint = if (isGpsActive) Color(0xFF2DF380) else Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GPS Source: ${if (isGpsActive) "Active Satellite" else "Manual/Locked Offset"}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        
                        if (!isGpsActive) {
                            Button(
                                onClick = { viewModel.enableGpsMode() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Re-Enable GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        TelemetryItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.MyLocation,
                            label = "Latitude",
                            value = "%.5f".format(latitude)
                        )
                        TelemetryItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.MyLocation,
                            label = "Longitude",
                            value = "%.5f".format(longitude)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        TelemetryItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Landscape,
                            label = "Altitude",
                            value = "${altitude.toInt()} m"
                        )
                        TelemetryItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Explore,
                            label = "Compass Azimuth",
                            value = "${compassDirection.toInt()}°"
                        )
                    }

                    if (weatherTemp.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TelemetryItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Cloud,
                                label = "Weather Context",
                                value = "$weatherCond $weatherTemp"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Full text address
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Room,
                                contentDescription = "Marker",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = address,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Favorite/Custom Location Switcher Flow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Switch / Favorite Places",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { showAddFavDialog = true }) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add Custom Location", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (favorites.isEmpty()) {
                Text(
                    "No favorite locations created yet. Click '+' to add customized survey nodes.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(favorites) { fav ->
                        Surface(
                            color = if (!isGpsActive && Math.abs(latitude - fav.latitude) < 0.001) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .width(150.dp)
                                .clickable {
                                    viewModel.setManualLocation(fav)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    fav.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${fav.latitude}, ${fav.longitude}",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stamping Template Shortcut
            Text(
                "Active Stamping Template",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            activeTemplate?.name ?: "Loading Default...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Type: ${activeTemplate?.templateType ?: "MINIMAL"}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Button(
                        onClick = { onNavigate("TEMPLATE_MANAGER") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Manage Layers", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Grid Options
            Text(
                "Explorer Modules",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                NavigationGridCard(
                    modifier = Modifier.weight(1f),
                    title = "File Gallery",
                    subtitle = "View PDF Reports & Photos",
                    icon = Icons.Default.PhotoLibrary,
                    color = Color(0xFF3F51B5),
                    onClick = { onNavigate("GALLERY") }
                )
                Spacer(modifier = Modifier.width(12.dp))
                NavigationGridCard(
                    modifier = Modifier.weight(1f),
                    title = "Custom Map",
                    subtitle = "Search & Lock Offsets",
                    icon = Icons.Default.Map,
                    color = Color(0xFF009688),
                    onClick = { onNavigate("MAP_PICKER") }
                )
            }
        }
    }

    // Add Favorite Location Dialog Box
    if (showAddFavDialog) {
        var nameInput by remember { mutableStateOf("") }
        var latInput by remember { mutableStateOf(latitude.toString()) }
        var lonInput by remember { mutableStateOf(longitude.toString()) }
        var addrInput by remember { mutableStateOf(address) }

        AlertDialog(
            onDismissRequest = { showAddFavDialog = false },
            title = { Text("Save Favorite Site Coordinates") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Site / Node Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lonInput,
                        onValueChange = { lonInput = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addrInput,
                        onValueChange = { addrInput = it },
                        label = { Text("Resolved Address Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val latVal = latInput.toDoubleOrNull() ?: latitude
                        val lonVal = lonInput.toDoubleOrNull() ?: longitude
                        if (nameInput.isNotBlank()) {
                            viewModel.addFavoriteLocation(nameInput, latVal, lonVal, addrInput)
                        }
                        showAddFavDialog = false
                    }
                ) {
                    Text("Save Pin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFavDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TelemetryItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NavigationGridCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
