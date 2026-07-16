package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.example.ui.GeoStampViewModel
import com.example.data.SavedPhoto
import com.example.data.StampTemplate
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import android.app.Activity

@Composable
fun CameraScreen(
    viewModel: GeoStampViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = (context as? Activity)

    BackHandler {
        activity?.moveTaskToBack(true)
    }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        cameraGranted = permissions[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED

        locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted || !locationGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    DisposableEffect(locationGranted) {
        if (locationGranted) {
            viewModel.startLocationUpdates()
        }
        onDispose {
            viewModel.stopLocationUpdates()
        }
    }

    if (!cameraGranted || !locationGranted) {
        // Render simple permissions prompt card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                     imageVector = Icons.Default.Security,
                     contentDescription = "Lock",
                     tint = Color(0xFF2DF380),
                     modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Permissions Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "MapSeal GPS Camera needs GPS Location and Camera access to snapshot, capture metadata, record videos, and save reports.",
                    color = Color.LightGray,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DF380), contentColor = Color.Black)
                ) {
                    Text("Grant Required Access", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onNavigateBack) {
                    Text("Decline & Go Back", color = Color.Gray)
                }
            }
        }
        return
    }

    // Camera use cases holding states
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var zoomRatio by remember { mutableStateOf(0f) }

    // Video Recording usecase bindings
    var isVideoMode by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording: Recording? by remember { mutableStateOf(null) }

    val cameraProviderFuture = remember {
        try {
            ProcessCameraProvider.getInstance(context)
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Failed getting ProcessCameraProvider instance", e)
            null
        }
    }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        try {
            ImageCapture.Builder().build()
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Failed creating ImageCapture Builder", e)
            null
        }
    }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var cameraInfo: CameraInfo? by remember { mutableStateOf(null) }

    // Video recording recorder safely initialized
    val recorder = remember {
        try {
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Failed creating Recorder Builder", e)
            null
        }
    }
    val videoCapture = remember {
        try {
            recorder?.let { VideoCapture.withOutput(it) }
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Failed creating VideoCapture with default Recorder", e)
            null
        }
    }

    // Synchronize telemetry details
    val latitude by viewModel.liveLatitude.collectAsState()
    val longitude by viewModel.liveLongitude.collectAsState()
    val address by viewModel.liveAddress.collectAsState()
    val altitude by viewModel.liveAltitude.collectAsState()
    val speed by viewModel.liveSpeed.collectAsState()
    val compassDirection by viewModel.liveCompassDirection.collectAsState()
    val weatherTemp by viewModel.liveWeatherTemp.collectAsState()
    val weatherCond by viewModel.liveWeatherCondition.collectAsState()
    val isGpsActive by viewModel.isGpsEnabled.collectAsState()
    val activeTemplate by viewModel.activeTemplate.collectAsState()
    val photos by viewModel.allPhotos.collectAsState()
    val templatesList by viewModel.allTemplates.collectAsState(initial = emptyList())
    val lastPhoto = photos.firstOrNull()

    // Real-time ticking clock overlay
    var liveTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("EEEE, MMM dd, yyyy | hh:mm:ss a z", Locale.getDefault())
            liveTimeString = sdf.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // Local file content/system library chooser (supports photos and videos!)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val contentResolver = context.contentResolver
                val mime = contentResolver.getType(it) ?: ""
                val prefix = if (mime.startsWith("video/")) "mp4" else "jpg"
                val file = File(context.cacheDir, "GeoStamp_Imported_${System.currentTimeMillis()}.$prefix")
                contentResolver.openInputStream(it)?.use { input ->
                    file.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
                viewModel.prepareDraftPhoto(file)
            } catch (e: Exception) {
                Log.e("GeoStamp", "Failed custom media import: ${e.message}", e)
                Toast.makeText(context, "Failed to import media file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Start CameraX preview binder (Binds capture based on mode to prevent concurrent conflicts)
    LaunchedEffect(lensFacing, isVideoMode, cameraGranted) {
        if (!cameraGranted) {
            Log.d("GeoStamp", "Camera permission not granted yet, skipping bindToLifecycle")
            return@LaunchedEffect
        }
        try {
            if (cameraProviderFuture == null) {
                Log.e("GeoStamp", "CameraProviderFuture is null, skipping CameraX binding.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Camera hardware/initialization is unavailable on this device", Toast.LENGTH_LONG).show()
                }
                return@LaunchedEffect
            }
            val cameraProvider = withContext(Dispatchers.IO) {
                cameraProviderFuture.get()
            }
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            var cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            // Verify if requested lens facing camera is available
            if (!cameraProvider.hasCamera(cameraSelector)) {
                Log.w("GeoStamp", "Selected camera lens facing not available: $lensFacing")
                val alternativeLens = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                val alternativeSelector = CameraSelector.Builder()
                    .requireLensFacing(alternativeLens)
                    .build()
                
                if (cameraProvider.hasCamera(alternativeSelector)) {
                    lensFacing = alternativeLens
                    cameraSelector = alternativeSelector
                } else {
                    Log.e("GeoStamp", "No standard camera lens facing (back or front) exists on this device.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No camera hardware detected on this device", Toast.LENGTH_LONG).show()
                    }
                    return@LaunchedEffect
                }
            }

            cameraProvider.unbindAll()
            val camera = if (isVideoMode && videoCapture != null) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            } else if (!isVideoMode && imageCapture != null) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
            }
            cameraControl = camera.cameraControl
            cameraInfo = camera.cameraInfo
            
            // Observe zoom
            camera.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                zoomRatio = state.linearZoom
            }
        } catch (exc: Throwable) {
            Log.e("GeoStamp", "Camera binding failed", exc)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Camera preview initialization error: ${exc.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Main camera layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_screen_root")
    ) {
        // Camera viewfinder Android View - clickable focuses view naturally without interceptor box!
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(
                            previewView.meteringPointFactory.createPoint(
                                previewView.width / 2f,
                                previewView.height / 2f
                            )
                        ).build()
                    )
                }
        )

        // Overlay Header with flash switch, lens switcher, location tracker configuration, and template layers settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode Selector Button
            val (flashIcon, flashLabel) = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> Pair(Icons.Default.FlashOn, "ON")
                ImageCapture.FLASH_MODE_OFF -> Pair(Icons.Default.FlashOff, "OFF")
                else -> Pair(Icons.Default.FlashAuto, "AUTO")
            }
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    imageCapture?.flashMode = flashMode
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .size(44.dp)
                    .testTag("flash_icon")
            ) {
                Icon(flashIcon, contentDescription = "Flash state $flashLabel", tint = Color.White)
            }



            // Lens toggle back <-> front selector
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .size(44.dp)
                    .testTag("flip_camera_icon")
            ) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Lens direction", tint = Color.White)
            }

            // Application settings gear shortcut
            IconButton(
                onClick = { viewModel.navigateTo("SETTINGS") },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .size(44.dp)
                    .testTag("settings_icon")
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Global Settings", tint = Color.White)
            }
        }

        // Pulse recording red indicator if recording
        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("RECORDING VIDEO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Live Location Timestamp Overlay Card matching chosen Template
        val dummyPhoto = SavedPhoto(
            filePath = "",
            latitude = latitude,
            longitude = longitude,
            address = address,
            timestamp = System.currentTimeMillis(),
            compassDirection = compassDirection,
            elevation = altitude,
            weatherTemp = weatherTemp,
            weatherCondition = weatherCond
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 210.dp) // Lifted above standard camera controls
        ) {
            StampOverlayLayout(
                metadata = dummyPhoto,
                template = activeTemplate ?: StampTemplate(id=1, name="Classic Template", templateType="CLASSIC"),
                liveTimeString = liveTimeString
            )
        }

        // Bottom Controls Screen Rail (Shutter button + Option triggers HUD)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.85f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 20.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Linear Zoom Controller Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                Slider(
                    value = zoomRatio,
                    onValueChange = {
                        zoomRatio = it
                        cameraControl?.setLinearZoom(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Premium Visual Switch for Photo / Video mode selector
            Row(
                modifier = Modifier
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PHOTO",
                    color = if (!isVideoMode) Color(0xFF2DF380) else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { 
                        if (!isRecording) {
                            isVideoMode = false 
                        }
                    }
                )
                Text(
                    text = "VIDEO",
                    color = if (isVideoMode) Color(0xFF2DF380) else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { 
                        if (!isRecording) {
                            isVideoMode = true 
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizon Bottom Action options rail (Preview, Capture, Storage, Template)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. "Preview" (navigates to GALLERY or opens full timestamped preview if clicked)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(60.dp)
                        .clickable {
                            if (lastPhoto != null && File(lastPhoto.filePath).exists()) {
                                val index = photos.indexOf(lastPhoto)
                                viewModel.setSelectedPhotoIndex(index)
                                viewModel.navigateTo("PHOTO_PREVIEW")
                            } else {
                                viewModel.navigateTo("GALLERY")
                            }
                        }
                        .testTag("preview_btn")
                ) {
                    if (lastPhoto != null && File(lastPhoto.filePath).exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(model = File(lastPhoto.filePath)),
                            contentDescription = "Last captured photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.5.dp, Color(0xFF2DF380), RoundedCornerShape(6.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Stamping records gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Preview", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }

                // 2. Central Shutter Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(if (isVideoMode) Color(0xFFE53935) else Color(0xFF2DF380))
                        .clickable {
                            if (isVideoMode) {
                                // Video record trigger
                                if (isRecording) {
                                    activeRecording?.stop()
                                    activeRecording = null
                                    isRecording = false
                                } else {
                                    val videoFile = File(context.cacheDir, "GeoStamp_Rec_${System.currentTimeMillis()}.mp4")
                                    val outputOptions = FileOutputOptions.Builder(videoFile).build()
                                    
                                    if (videoCapture == null) {
                                        Toast.makeText(context, "Video recording is not supported on this device", Toast.LENGTH_SHORT).show()
                                    } else {
                                        try {
                                            isRecording = true
                                            val recordingJob = videoCapture.output
                                                .prepareRecording(context, outputOptions)
                                                .apply {
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                        withAudioEnabled()
                                                    }
                                                }
                                                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                                    if (recordEvent is VideoRecordEvent.Finalize) {
                                                        isRecording = false
                                                        activeRecording = null
                                                        if (!recordEvent.hasError()) {
                                                            viewModel.prepareDraftPhoto(videoFile)
                                                        } else {
                                                            Log.e("GeoStamp", "Video captured finalize failure code: ${recordEvent.error}")
                                                            Toast.makeText(context, "Error saving video recording", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            activeRecording = recordingJob
                                        } catch (e: Throwable) {
                                            isRecording = false
                                            Log.e("GeoStamp", "Failed start of video recorder: ${e.message}", e)
                                            Toast.makeText(context, "Mic/video uninitialized or unavailable", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                // Photo snapshot trigger
                                val shutterTime = System.currentTimeMillis()
                                val name = "GeoStamp_Temp_" + shutterTime + ".jpg"
                                val tempFile = File(context.cacheDir, name)

                                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                                if (imageCapture == null) {
                                    Toast.makeText(context, "Photo capture is not supported on this device", Toast.LENGTH_SHORT).show()
                                } else {
                                    try {
                                        imageCapture.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    viewModel.prepareDraftPhoto(tempFile, shutterTime)
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e("GeoStamp", "Photo capture failed: ${exception.message}", exception)
                                                    Toast.makeText(context, "Capture error!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    } catch (e: Throwable) {
                                        Log.e("GeoStamp", "Failed calling takePicture: ${e.message}", e)
                                        Toast.makeText(context, "Camera is uninitialized or busy", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .testTag("shutter_btn")
                ) {
                    if (isVideoMode && isRecording) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        ) {}
                    }
                }

                // 4. "Storage" (imports file from device gallery/storage)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(60.dp)
                        .clickable { mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                        .testTag("storage_btn")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Import external JPEG file and apply stamp", tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Storage", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

}
