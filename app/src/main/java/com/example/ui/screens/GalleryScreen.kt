package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.data.SavedPhoto
import com.example.data.StampTemplate
import com.example.ui.GeoStampViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GeoStampViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.gallerySearchQuery.collectAsState()
    val photos by viewModel.filteredPhotos.collectAsState()
    val templatesList by viewModel.allTemplates.collectAsState(initial = emptyList())


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stamping Records Gallery", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
        ) {

            if (photos.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = "Empty",
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No inspection records stored.",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Capture a photo or record a video using the camera to stamp.",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).testTag("gallery_grid")
                ) {
                    items(photos) { photo ->
                        val isVideo = photo.filePath.endsWith(".mp4")
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable {
                                    val index = photos.indexOf(photo)
                                    viewModel.setSelectedPhotoIndex(index)
                                    viewModel.navigateTo("PHOTO_PREVIEW")
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = File(photo.filePath)),
                                    contentDescription = "Stored Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Video Play icon badge overlay
                                if (isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(40.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .border(1.5.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Address tag overlay bottom
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            photo.customLocationName.ifEmpty {
                                                photo.address.split(",").firstOrNull() ?: "Offline Place"
                                            },
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(photo.timestamp)),
                                                color = Color.LightGray,
                                                fontSize = 9.sp
                                            )
                                            if (isVideo) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFE53935), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text("VIDEO", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
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
        }
    }

}

// Sharing helper handles both photographs and video files!
private fun shareImageFile(context: Context, photo: SavedPhoto) {
    val file = File(photo.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "Error: File doesn't exist!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val isVideo = photo.filePath.endsWith(".mp4")
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val shareTitle = if (isVideo) "Share Survey Video" else "Share Survey Photo"

        val dayStr = if (photo.customDayOfWeek.isNotEmpty()) photo.customDayOfWeek else "Friday"
        val dateStr = if (photo.customDateStr.isNotEmpty()) photo.customDateStr else SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(photo.timestamp))
        val timeStr = if (photo.customTimeStr.isNotEmpty()) photo.customTimeStr else SimpleDateFormat("hh:mm a", Locale.US).format(Date(photo.timestamp)).uppercase(Locale.US)
        
        val stampCaptionText = buildString {
            append("📍 GPS Map Camera\n")
            append("Latitude: ${photo.latitude}\n")
            append("Longitude: ${photo.longitude}\n")
            append("Address: ${photo.address}\n")
            append("Time: $dayStr, $dateStr $timeStr\n")
            if (photo.projectName.isNotEmpty()) {
                append("Project: ${photo.projectName}\n")
            }
            if (photo.siteName.isNotEmpty()) {
                append("Site: ${photo.siteName}\n")
            }
            if (photo.notes.isNotEmpty()) {
                append("Notes: ${photo.notes}\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "com.aistudio.geostamp.yrmqwd.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, stampCaptionText)
            putExtra(Intent.EXTRA_SUBJECT, "GPS Map Camera Survey Stamped Record")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, shareTitle))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Sharing PDF Document Helper
private fun sharePdfFile(context: Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(context, "Error: PDF not found!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(context, "com.aistudio.geostamp.yrmqwd.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Survey Report PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
