package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GeoStampViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GeoStampViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Settings Flow bindings
    val isDarkTheme by viewModel.darkThemeEnabled.collectAsState()
    val gpsAccuracy by viewModel.gpsAccuracyType.collectAsState()
    val defaultTemplateId by viewModel.defaultTemplateIdState.collectAsState()
    val defaultCamera by viewModel.defaultCameraType.collectAsState()
    val autoSaveEnabled by viewModel.autoSaveState.collectAsState()
    val weatherApiKey by viewModel.weatherApiKeyCached.collectAsState()
    val allTemplates by viewModel.allTemplates.collectAsState(initial = emptyList())

    var weatherKeyInput by remember { mutableStateOf(weatherApiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Preferences", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            // General Theme Options
            Text(
                "Theme Aesthetics",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, "Dark Icon", tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Dark Display Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Optimizes outdoor contrast and visibility", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.updateTheme(it) },
                    modifier = Modifier.testTag("dark_theme_switch")
                )
            }

            Divider(modifier = Modifier.padding(vertical = 14.dp))

            // GPS Location Tuning
            Text(
                "GPS Tuning Control",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text("Satellite Update Accuracy:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("HIGH", "BALANCED", "LOW").forEach { mode ->
                    val isModeSelected = gpsAccuracy == mode
                    FilterChip(
                        selected = isModeSelected,
                        onClick = { viewModel.updateGpsAccuracy(mode) },
                        label = { Text(mode) }
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 14.dp))

            // Camera Settings Choices
            Text(
                "Camera Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Default Facing Lens:", fontSize = 13.sp)
                Row {
                    FilterChip(
                        selected = defaultCamera == "BACK",
                        onClick = { viewModel.updateDefaultCamera("BACK") },
                        label = { Text("Rear Lens") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilterChip(
                        selected = defaultCamera == "FRONT",
                        onClick = { viewModel.updateDefaultCamera("FRONT") },
                        label = { Text("Front Lens") }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Save Photos Immediately", fontSize = 13.sp)
                    Text("Saves to disk directly without prompting of custom metadata fields", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = autoSaveEnabled,
                    onCheckedChange = { viewModel.updateAutoSave(it) }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 14.dp))

            // Default Stamp Template Preference
            Text(
                "Default Stamp",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text("Launcher Default Stamp Layer:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
            allTemplates.forEach { temp ->
                val isDefault = defaultTemplateId == temp.id
                Surface(
                    color = if (isDefault) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateDefaultTemplate(temp.id) }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isDefault, onClick = { viewModel.updateDefaultTemplate(temp.id) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(temp.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 14.dp))

            // OpenWeather API Credentials
            Text(
                "OpenWeather API Credentials",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = weatherKeyInput,
                onValueChange = { weatherKeyInput = it },
                label = { Text("API Key") },
                placeholder = { Text("Paste openweather api token...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.updateWeatherApiKey(weatherKeyInput.trim())
                    Toast.makeText(context, "Weather Credentials Cached Successfully!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A)),
                modifier = Modifier.fillMaxWidth().testTag("save_api_key_btn")
            ) {
                Text("Save Weather Connection", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
