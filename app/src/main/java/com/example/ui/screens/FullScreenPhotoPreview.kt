package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.data.SavedPhoto
import com.example.data.StampTemplate
import com.example.ui.GeoStampViewModel
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPhotoPreview(
    viewModel: GeoStampViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val photos by viewModel.filteredPhotos.collectAsState()
    val initialIndex by viewModel.selectedPhotoIndexForPreview.collectAsState()
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            onClick = {
                                val photo = photos[pagerState.currentPage]
                                shareImageFile(context, photo)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            onClick = {
                                val photo = photos[pagerState.currentPage]
                                viewModel.deletePhoto(photo)
                                showMenu = false
                                if (photos.size <= 1) {
                                    onNavigateBack()
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f))
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) { page ->
            val photo = photos[page]
            val template = templates.find { it.id == photo.templateId } ?: templates.firstOrNull() ?: StampTemplate(id = 1, name = "Classic Template", templateType = "CLASSIC")
            ZoomableImage(photo = photo, template = template)
        }
    }
}

@Composable
fun ZoomableImage(photo: SavedPhoto, template: StampTemplate) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(
    state = state,
    enabled = scale > 1f
),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = File(photo.filePath)),
            contentDescription = "Full-screen photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun shareImageFile(context: Context, photo: SavedPhoto) {
    val file = File(photo.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "Error: File doesn't exist!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(context, "com.aistudio.geostamp.yrmqwd.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Survey Photo"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
