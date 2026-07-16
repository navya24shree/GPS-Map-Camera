package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.PdfExporter
import com.example.camera.PhotoStampingEngine
import com.example.data.*
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GeoStampViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    val repository = GeoStampRepository(db)
    val settingsManager = SettingsManager(context)
    private val weatherService = WeatherService()

    // Screen navigation flow
    private val _currentScreen = MutableStateFlow("SPLASH") // SPLASH, HOME, CAMERA, TEMPLATE_MANAGER, MAP_PICKER, GALLERY, PHOTO_EDITOR, SETTINGS
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    // Settings States
    val darkThemeEnabled = MutableStateFlow(settingsManager.themeMode == "DARK")
    val gpsAccuracyType = MutableStateFlow(settingsManager.gpsAccuracy)
    val defaultTemplateIdState = MutableStateFlow(settingsManager.defaultTemplateId)
    val defaultCameraType = MutableStateFlow(settingsManager.defaultCamera)
    val autoSaveState = MutableStateFlow(settingsManager.autoSave)
    val weatherApiKeyCached = MutableStateFlow(settingsManager.weatherApiKey)

    fun updateTheme(isDark: Boolean) {
        darkThemeEnabled.value = isDark
        settingsManager.themeMode = if (isDark) "DARK" else "LIGHT"
    }

    fun updateGpsAccuracy(accuracy: String) {
        gpsAccuracyType.value = accuracy
        settingsManager.gpsAccuracy = accuracy
        restartLocationUpdates()
    }

    fun updateDefaultTemplate(id: Int) {
        defaultTemplateIdState.value = id
        settingsManager.defaultTemplateId = id
    }

    fun updateDefaultCamera(type: String) {
        defaultCameraType.value = type
        settingsManager.defaultCamera = type
    }

    fun updateAutoSave(enabled: Boolean) {
        autoSaveState.value = enabled
        settingsManager.autoSave = enabled
    }

    fun updateWeatherApiKey(key: String) {
        weatherApiKeyCached.value = key
        settingsManager.weatherApiKey = key
    }

    // Database Flows
    val allPhotos = repository.allPhotos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTemplates = repository.allTemplates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allFavoriteLocations = repository.allFavoriteLocations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter flows for Gallery
    val gallerySearchQuery = MutableStateFlow("")
    val galleryFilterStartDate = MutableStateFlow<Long?>(null)
    val galleryFilterEndDate = MutableStateFlow<Long?>(null)

    val filteredPhotos = combine(allPhotos, gallerySearchQuery, galleryFilterStartDate, galleryFilterEndDate) { photos, query, start, end ->
        photos.filter { photo ->
            val matchesQuery = query.isBlank() || 
                photo.address.contains(query, ignoreCase = true) || 
                photo.projectName.contains(query, ignoreCase = true) ||
                photo.siteName.contains(query, ignoreCase = true) ||
                photo.notes.contains(query, ignoreCase = true)
            
            val matchesStart = start == null || photo.timestamp >= start
            val matchesEnd = end == null || photo.timestamp <= end
            
            matchesQuery && matchesStart && matchesEnd
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // Sensors Integration for Live Metadata
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    val liveCompassDirection = MutableStateFlow(0f)

    // Location client securely wrapped to prevent Play Services crash on startup
    private val fusedLocationClient: FusedLocationProviderClient? by lazy {
        try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Failed initializing FusedLocationProviderClient", e)
            null
        }
    }
    private var locationCallback: LocationCallback? = null

    // Live telemetry properties
    val liveLatitude = MutableStateFlow(12.9716) // Default Bangalore Coords
    val liveLongitude = MutableStateFlow(77.5946)
    val liveAltitude = MutableStateFlow(920.0)
    val liveAddress = MutableStateFlow("Bangalore, Karnataka, India")
    val liveSpeed = MutableStateFlow(0f)
    val liveWeatherTemp = MutableStateFlow("")
    val liveWeatherCondition = MutableStateFlow("")

    // GPS Status (Enabled/Disabled)
    val isGpsEnabled = MutableStateFlow(true)
    
    // Loaded template for active camera/stamping
    val activeTemplate = MutableStateFlow<StampTemplate?>(null)

    // Current active draft photo under editor before stamp rendering
    val currentCapturedDraftFile = MutableStateFlow<File?>(null)
    val editorModel = MutableStateFlow<SavedPhoto?>(null)
    
    // Fullscreen photo preview state
    val selectedPhotoIndexForPreview = MutableStateFlow(0)
    fun setSelectedPhotoIndex(index: Int) {
        selectedPhotoIndexForPreview.value = index
    }
    
    // Compass Direction Sensor Setup
    init {
        setupSensors()
        loadDefaultTemplate()
    }

    private fun setupSensors() {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationSensor == null) {
                // Fallback to accelerometer + magnetometer if rotation vector is unavailable
                rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
            }
            rotationSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Sensor initialization failed: ${e.message}")
        }
    }

    fun loadDefaultTemplate() {
        viewModelScope.launch {
            try {
                allTemplates.collectLatest { templates ->
                    if (templates.isNotEmpty()) {
                        val defaultId = defaultTemplateIdState.value
                        activeTemplate.value = templates.find { it.id == defaultId } ?: templates.first()
                    }
                }
            } catch (e: Throwable) {
                Log.e("GeoStamp", "Failed collecting templates on startup", e)
            }
        }
    }

    fun setActiveTemplate(template: StampTemplate) {
        activeTemplate.value = template
        // Save as default if required
        updateDefaultTemplate(template.id)
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!isGpsEnabled.value) return

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("GeoStamp", "Skipping location updates: No location permissions granted yet.")
            return
        }

        try {
            // Unregister previous callback to prevent leaks and overlapping updates
            locationCallback?.let {
                try {
                    fusedLocationClient?.removeLocationUpdates(it)
                } catch (ex: Throwable) {
                    // Ignore safe removal issues
                }
            }

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000L
            ).apply {
                setMinUpdateIntervalMillis(2000L)
                setMinUpdateDistanceMeters(1f)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val loc = locationResult.lastLocation ?: return
                    
                    // Update GPS numbers
                    liveLatitude.value = loc.latitude
                    liveLongitude.value = loc.longitude
                    liveAltitude.value = loc.altitude
                    liveSpeed.value = loc.speed * 3.6f // Convert m/s to km/h
                    
                    // Geocode address in background
                    resolveAddress(loc.latitude, loc.longitude)
                    
                    // Fetch live weather
                    fetchWeatherForLocation(loc.latitude, loc.longitude)
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Failed starting GPS updates: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient?.removeLocationUpdates(it)
                Log.d("GeoStamp", "Stopped location updates successfully.")
            } catch (ex: Throwable) {
                Log.e("GeoStamp", "Failed removing location updates", ex)
            }
        }
    }

    fun setManualLocation(fav: FavoriteLocation) {
        isGpsEnabled.value = false
        liveLatitude.value = fav.latitude
        liveLongitude.value = fav.longitude
        liveAddress.value = fav.address
        liveAltitude.value = 0.0
        liveSpeed.value = 0f
        fetchWeatherForLocation(fav.latitude, fav.longitude)
    }

    fun enableGpsMode() {
        isGpsEnabled.value = true
        startLocationUpdates()
    }

    private fun resolveAddress(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val addressText = address.getAddressLine(0) ?: "${address.locality}, ${address.adminArea}"
                    liveAddress.value = addressText
                } else {
                    liveAddress.value = "Lat: %.5f, Lon: %.5f".format(lat, lon)
                }
            } catch (e: Exception) {
                // Offline fallback
                liveAddress.value = "Lat: %.5f, Lon: %.5f (Resolved Offline)".format(lat, lon)
            }
        }
    }

    private fun fetchWeatherForLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val weather = weatherService.fetchWeather(lat, lon, weatherApiKeyCached.value)
                liveWeatherTemp.value = weather.tempStr
                liveWeatherCondition.value = weather.conditionStr
            } catch (e: Exception) {
                // Silent catch fallback
            }
        }
    }

    private fun restartLocationUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient?.removeLocationUpdates(it)
            } catch (ex: Throwable) {
                Log.e("GeoStamp", "Failed removing location updates during restart", ex)
            }
        }
        startLocationUpdates()
    }

    // Photo Capture Target Setup
    fun prepareDraftPhoto(file: File, shutterTime: Long = System.currentTimeMillis()) {
        currentCapturedDraftFile.value = file
        
        val captureDate = Date(shutterTime)
        
        val lat = liveLatitude.value
        val lon = liveLongitude.value
        val tz = if (lat in 8.0..38.0 && lon in 68.0..98.0) {
            TimeZone.getTimeZone("Asia/Kolkata")
        } else {
            TimeZone.getDefault()
        }

        // Find default day of week
        val sdfDay = SimpleDateFormat("EEEE", Locale.US).apply { timeZone = tz }
        val defaultDay = sdfDay.format(captureDate)
        
        // Find default country flag from resolved address
        val flag = getCountryFlagFromAddress(liveAddress.value)
        
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = tz }
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = tz }
        val defaultDateStr = sdfDate.format(captureDate)
        val defaultTimeStr = sdfTime.format(captureDate).uppercase(Locale.US)

        // Prepare inspection model details
        val initialMetadata = SavedPhoto(
            id = 0,
            filePath = file.absolutePath,
            latitude = liveLatitude.value,
            longitude = liveLongitude.value,
            address = liveAddress.value,
            timestamp = shutterTime,
            notes = "",
            projectName = "",
            siteName = "",
            inspectionNumber = "",
            templateId = activeTemplate.value?.id ?: 1,
            weatherTemp = liveWeatherTemp.value,
            weatherCondition = liveWeatherCondition.value,
            compassDirection = liveCompassDirection.value,
            speed = liveSpeed.value,
            elevation = liveAltitude.value,
            customLocationName = if (liveAddress.value.isNotEmpty()) {
                val parts = liveAddress.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 3) {
                    val country = parts.last()
                    val stateAndZip = parts[parts.size - 2]
                    val zipRegex = Regex("\\d{5,6}")
                    val zipMatch = zipRegex.find(stateAndZip)
                    val zipCode = zipMatch?.value ?: ""
                    val state = stateAndZip.replace(zipCode, "").trim()
                    val city = parts[parts.size - 3]
                    listOfNotNull(city.takeIf { it.isNotEmpty() }, state.takeIf { it.isNotEmpty() }, country.takeIf { it.isNotEmpty() }).joinToString(", ")
                } else {
                    liveAddress.value.split(",").firstOrNull { it.trim().isNotEmpty() }?.trim() ?: "MapSeal Location"
                }
            } else {
                "Doddaballapura, Karnataka, India"
            },
            customDayOfWeek = defaultDay,
            countryFlag = flag,
            customDateStr = defaultDateStr,
            customTimeStr = defaultTimeStr
        )
        editorModel.value = initialMetadata
        navigateTo("PHOTO_EDITOR")
    }

    // Helper to extract flag associated with a particular country
    fun getCountryFlagFromAddress(address: String): String {
        val addrUpper = address.uppercase()
        return when {
            addrUpper.contains("INDIA") || addrUpper.contains(", IN") -> "🇮🇳"
            addrUpper.contains("UNITED STATES") || addrUpper.contains("USA") || addrUpper.contains(", US") -> "🇺🇸"
            addrUpper.contains("UNITED KINGDOM") || addrUpper.contains("GREAT BRITAIN") || addrUpper.contains(", UK") || addrUpper.contains(", GB") -> "🇬🇧"
            addrUpper.contains("CANADA") || addrUpper.contains(", CA") -> "🇨🇦"
            addrUpper.contains("AUSTRALIA") || addrUpper.contains(", AU") -> "🇦🇺"
            addrUpper.contains("GERMANY") || addrUpper.contains(", DE") -> "🇩🇪"
            addrUpper.contains("FRANCE") || addrUpper.contains(", FR") -> "🇫🇷"
            addrUpper.contains("JAPAN") || addrUpper.contains(", JP") -> "🇯🇵"
            addrUpper.contains("SINGAPORE") || addrUpper.contains(", SG") -> "🇸🇬"
            addrUpper.contains("UNITED ARAB EMIRATES") || addrUpper.contains("UAE") || addrUpper.contains(", AE") -> "🇦🇪"
            else -> "🇮🇳" // Default standard flag
        }
    }

    // Photo Editor Customizations Updates
    fun updateEditorAddress(address: String) {
        editorModel.value = editorModel.value?.copy(address = address)
    }

    fun updateEditorCoords(lat: Double, lon: Double) {
        editorModel.value = editorModel.value?.copy(latitude = lat, longitude = lon)
    }

    fun updateEditorCustomFields(
        locationName: String,
        dayOfWeek: String,
        flag: String,
        dateStr: String,
        timeStr: String
    ) {
        editorModel.value = editorModel.value?.copy(
            customLocationName = locationName,
            customDayOfWeek = dayOfWeek,
            countryFlag = flag,
            customDateStr = dateStr,
            customTimeStr = timeStr
        )
    }

    // Final Save Operation: Stamps the photo (or copies video), stores in device gallery, and writes metadata in SQLite!
    fun finalizeAndSaveStamping(dateFormat: String = "dd/MM/yyyy", is24Hour: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val draftFile = currentCapturedDraftFile.value ?: return@launch
            val metadata = editorModel.value ?: return@launch
            val temp = activeTemplate.value ?: StampTemplate(1, "Minimalist", "MINIMAL")

            try {
                val isVideo = draftFile.absolutePath.endsWith(".mp4")
                val smartName: String
                val finalFile: File
                val appGalleryDir = File(context.getExternalFilesDir(null), "GeoStampPhotos")
                if (!appGalleryDir.exists()) appGalleryDir.mkdirs()

                if (isVideo) {
                    smartName = "GeoStamp_Vid_" + metadata.timestamp + ".mp4"
                    finalFile = File(appGalleryDir, smartName)
                    
                    // Copy video draft file to final file
                    draftFile.inputStream().use { input ->
                        finalFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Save video to device gallery (MediaStore)
                    try {
                        val resolver = context.contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, smartName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Movies/GeoStamp")
                                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }
                        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        }
                        val videoUri = resolver.insert(collection, contentValues)
                        if (videoUri != null) {
                            resolver.openOutputStream(videoUri)?.use { out ->
                                finalFile.inputStream().use { fIn ->
                                    fIn.copyTo(out)
                                }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                                resolver.update(videoUri, contentValues, null, null)
                            }
                        }
                        // Scan file in public gallery directory to sync with system gallery app immediately!
                        val publicVideoFile = File(
                            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                            "GeoStamp/$smartName"
                        )
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(publicVideoFile.absolutePath),
                            arrayOf("video/mp4")
                        ) { _, _ -> }
                    } catch (e: Exception) {
                        Log.e("GeoStamp", "Error saving video to device gallery: ${e.message}", e)
                    }

                } else {
                    // It is a photo snapshot
                    // Decode original draft photo from device
                    val rawBitmap = BitmapFactory.decodeFile(draftFile.absolutePath) ?: return@launch
                    
                    // Process the Bitmap stamp
                    val stampedBitmap = PhotoStampingEngine.applyStamp(
                        context, rawBitmap, metadata, temp, is24Hour, dateFormat
                    )

                    // Generate smart clean filename
                    smartName = PhotoStampingEngine.generateSmartFileName(metadata.address, metadata.timestamp)
                    finalFile = File(appGalleryDir, smartName)
                    
                    // Save the finalized image
                    PhotoStampingEngine.saveBitmapToFile(
                        stampedBitmap, 
                        finalFile, 
                        metadata = metadata, 
                        is24Hour = is24Hour, 
                        dateFormat = dateFormat
                    )

                    // Automatically store in device gallery (MediaStore)
                    try {
                        val resolver = context.contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, smartName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GeoStamp")
                                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }
                        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }
                        val galleryUri = resolver.insert(collection, contentValues)
                        if (galleryUri != null) {
                            resolver.openOutputStream(galleryUri)?.use { out ->
                                finalFile.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                                resolver.update(galleryUri, contentValues, null, null)
                            }
                        }
                        // Scan file in public gallery directory to sync with system gallery app immediately!
                        val publicPhotoFile = File(
                            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                            "GeoStamp/$smartName"
                        )
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(publicPhotoFile.absolutePath),
                            arrayOf("image/jpeg")
                        ) { _, _ -> }
                    } catch (e: Exception) {
                        Log.e("GeoStamp", "Error saving photo to device gallery: ${e.message}", e)
                    }
                }

                // Save in SQLite database
                val finalizedRecord = metadata.copy(
                    filePath = finalFile.absolutePath,
                    templateId = temp.id
                )
                repository.insertPhoto(finalizedRecord)

                // Delete original loose un-stamped draft file to save storage space
                if (draftFile.exists() && draftFile.absolutePath != finalFile.absolutePath) {
                    draftFile.delete()
                }

                // Reset draft reference and navigate home
                currentCapturedDraftFile.value = null
                editorModel.value = null

                withContext(Dispatchers.Main) {
                    navigateTo("GALLERY")
                }
            } catch (e: Exception) {
                Log.e("GeoStamp", "Error during final stamp save: ${e.message}")
            }
        }
    }

    // Delete single photo
    fun deletePhoto(photo: SavedPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePhoto(photo)
            val file = File(photo.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    // Export PDF Report function
    fun generatePdfReport(photo: SavedPhoto, onCompleted: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val appReportsDir = File(context.getExternalFilesDir(null), "GeoStampReports")
            if (!appReportsDir.exists()) appReportsDir.mkdirs()

            val cleanName = File(photo.filePath).nameWithoutExtension
            val pdfFile = File(appReportsDir, "Report_${cleanName}.pdf")
            
            PdfExporter.exportPhotoToPdf(context, photo, pdfFile)
            
            withContext(Dispatchers.Main) {
                onCompleted(pdfFile)
            }
        }
    }

    // Favorite locations CRUD
    fun addFavoriteLocation(name: String, lat: Double, lon: Double, address: String) {
        viewModelScope.launch {
            repository.insertFavoriteLocation(FavoriteLocation(0, name, lat, lon, address))
        }
    }

    // Templates customization & Custom Stamp Template Designer
    fun saveCustomLayout(
        name: String,
        fontFamily: String,
        fontSize: Int,
        fontColor: String,
        bgColor: String,
        opacity: Int,
        position: String,
        showWeather: Boolean,
        showCoords: Boolean,
        showTimestamp: Boolean,
        showNotes: Boolean
    ) {
        viewModelScope.launch {
            val customTemplate = StampTemplate(
                id = 7, // Preset custom slot
                name = name,
                templateType = "CUSTOM",
                fontFamily = fontFamily,
                fontSize = fontSize,
                fontColorHex = fontColor,
                backgroundColorHex = bgColor,
                bgOpacityPercent = opacity,
                position = position,
                showMapPreview = false,
                showWeather = showWeather,
                showCoordinates = showCoords,
                showTimestamp = showTimestamp,
                showNotes = showNotes
            )
            repository.insertTemplate(customTemplate)
            activeTemplate.value = customTemplate
        }
    }

    // Method to update any stamp template in the database reactively
    fun updateTemplate(template: StampTemplate) {
        viewModelScope.launch {
            repository.insertTemplate(template)
            if (activeTemplate.value?.id == template.id) {
                activeTemplate.value = template
            }
        }
    }

    // Batch Watermarking: Apply a template directly onto existing images from local gallery!
    fun batchWatermarkPhotos(files: List<File>, template: StampTemplate) {
        viewModelScope.launch(Dispatchers.IO) {
            for (file in files) {
                try {
                    val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                    val metadata = SavedPhoto(
                        filePath = file.absolutePath,
                        latitude = liveLatitude.value,
                        longitude = liveLongitude.value,
                        address = liveAddress.value,
                        timestamp = file.lastModified(),
                        notes = "Batch Watermarked"
                    )

                    val stamped = PhotoStampingEngine.applyStamp(context, rawBitmap, metadata, template)
                    val outDir = File(context.getExternalFilesDir(null), "GeoStampPhotos")
                    if (!outDir.exists()) outDir.mkdirs()
                    
                    val outName = "Stamped_" + file.name
                    val finalFile = File(outDir, outName)
                    PhotoStampingEngine.saveBitmapToFile(stamped, finalFile, metadata = metadata)
                    
                    repository.insertPhoto(metadata.copy(filePath = finalFile.absolutePath, templateId = template.id))
                } catch (e: Exception) {
                    Log.e("GeoStamp", "Batch failed for ${file.name}", e)
                }
            }
        }
    }

    // SensorEventListener overrides for Live Compass Heading Integration
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        try {
            val values = event.values
            if (values == null || values.isEmpty()) return

            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                if (values.size >= 3) {
                    val rotationMatrix = FloatArray(9)
                    // Some platforms require checking length specifically and padding to prevent native crashes
                    val rotationVector = if (values.size >= 4) {
                        values
                    } else {
                        val padded = FloatArray(4)
                        System.arraycopy(values, 0, padded, 0, 3)
                        padded
                    }
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    
                    // Output yaw/azimuth in degrees [0, 360]
                    val azimuthInRadians = orientation[0]
                    var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                    if (azimuthInDegrees < 0) {
                        azimuthInDegrees += 360f
                    }
                    liveCompassDirection.value = azimuthInDegrees
                }
            } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                if (values.isNotEmpty()) {
                    var deg = values[0]
                    if (deg < 0) deg += 360f
                    liveCompassDirection.value = deg
                }
            }
        } catch (e: Throwable) {
            Log.e("GeoStamp", "Error processing sensor change: ${e.message}", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        // Unregister listeners
        try {
            sensorManager?.unregisterListener(this)
            locationCallback?.let {
                fusedLocationClient?.removeLocationUpdates(it)
            }
        } catch (e: Exception) {
            // Safe teardown
        }
    }
}
