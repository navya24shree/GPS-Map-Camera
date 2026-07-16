package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StampTemplate
import com.example.ui.GeoStampViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateManagerScreen(
    viewModel: GeoStampViewModel,
    onNavigateBack: () -> Unit
) {
    val allTemplates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val activeTemplate by viewModel.activeTemplate.collectAsState()

    // Preset color palettes
    val textColors = listOf("#FFFFFF", "#2DF380", "#FFFF00", "#00E676", "#FF5722", "#03A9F4")
    val bgColors = listOf("#101B26", "#0B0E11", "#152433", "#000000", "#12131C")

    // Mock SavedPhoto for live premium preview rendering
    val mockPhotoMetadata = remember {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(java.util.Calendar.YEAR, 2026)
            set(java.util.Calendar.MONTH, java.util.Calendar.JUNE)
            set(java.util.Calendar.DAY_OF_MONTH, 12)
            set(java.util.Calendar.HOUR_OF_DAY, 15)
            set(java.util.Calendar.MINUTE, 25)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        com.example.data.SavedPhoto(
            id = 999,
            filePath = "",
            latitude = 13.288112,
            longitude = 77.530453,
            address = "6/36, Chaitanya Nagar, Doddaballapura, Karnataka 561203, India",
            timestamp = cal.timeInMillis,
            countryFlag = "🇮🇳",
            customLocationName = "Doddaballapura, Karnataka, India"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stamp Editor & Presets", fontWeight = FontWeight.Bold) },
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
        ) {
            // Static Realtime Preview Area at the top
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text(
                    "Real-Time Visual Stamp Preview",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1216))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    activeTemplate?.let { template ->
                        StampOverlayLayout(
                            metadata = mockPhotoMetadata,
                            template = template,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } ?: Text("Select a template below to view real-time preview", color = Color.Gray, fontSize = 12.sp)
                }
            }

            // Scrollable list of presets with integrated inline customization
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Selected Theme Customizations & Presets",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                allTemplates.forEach { temp ->
                    val isActive = activeTemplate?.id == temp.id
                    
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { viewModel.setActiveTemplate(temp) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                 Column(modifier = Modifier.weight(1f)) {
                                    Text(temp.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "Style: ${temp.templateType} • Font: ${temp.fontFamily} • Align: ${temp.position}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Active", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }

                            // Active Stamp Preset Preview in-place
                            AnimatedVisibility(
                                visible = isActive,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                ) {
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Preview of Preset Stamp Layout:", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF0F1216))
                                            .padding(10.dp)
                                    ) {
                                        StampOverlayLayout(
                                            metadata = mockPhotoMetadata,
                                            template = temp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
