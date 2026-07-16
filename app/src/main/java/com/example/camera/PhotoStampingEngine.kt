package com.example.camera

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.SavedPhoto
import com.example.data.StampTemplate
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PhotoStampingEngine {

    // Helper for formatting timestamps
    fun formatTimestamp(timestamp: Long, datePattern: String = "yyyy-MM-dd", is24Hour: Boolean = false): String {
        val dateForm = SimpleDateFormat(datePattern, Locale.getDefault())
        val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"
        val timeForm = SimpleDateFormat(timePattern, Locale.getDefault())
        val dateStr = dateForm.format(Date(timestamp))
        val timeStr = timeForm.format(Date(timestamp))
        return "$dateStr | $timeStr"
    }

    // Smart Auto-Naming: Udupi_Beach_11Jun2026_0845PM.jpg
    fun generateSmartFileName(address: String, timestamp: Long): String {
        val datePart = SimpleDateFormat("ddMMMMyyyy_hhmm_a", Locale.US).format(Date(timestamp))
        
        // Extract a clean city or site keyword from address
        val cleanLocation = address.split(",")
            .firstOrNull { it.trim().isNotEmpty() }
            ?.trim()
            ?.replace(Regex("[^a-zA-Z0-9]"), "_")
            ?.replace(Regex("_+"), "_")
            ?: "GeoStamp"
            
        val safeLoc = if (cleanLocation.length > 25) cleanLocation.substring(0, 25) else cleanLocation
        return "${safeLoc}_${datePart}.jpg".replace("__", "_")
    }

    // Apply stamp on a given Bitmap representing exact GPS Map Camera screenshot layout
    fun applyStamp(
        context: Context,
        originalBitmap: Bitmap,
        photoMetadata: SavedPhoto,
        template: StampTemplate,
        is24Hour: Boolean = false,
        dateFormat: String = "dd/MM/yyyy"
    ): Bitmap {
        // Create an editable copy of the bitmap
        val config = originalBitmap.config ?: Bitmap.Config.ARGB_8888
        val workingBitmap = originalBitmap.copy(config, true)
        val canvas = Canvas(workingBitmap)
        val width = workingBitmap.width
        val height = workingBitmap.height

        // Define base scaling factor based on source image resolution to ensure consistent look
        // val scaleFactor = (height.coerceAtMost(width) / 1080f).coerceIn(0.6f, 5.0f)
        val scaleFactor =
    (width / 1080f).coerceIn(0.6f, 3.0f)
        
        drawStampToCanvas(
            context,
            canvas,
            width,
            height,
            scaleFactor,
            photoMetadata,
            template,
            is24Hour,
            dateFormat
        )

        return workingBitmap
    }

    // Unified drawing engine: used both for saving to Bitmap and rendering Composable Canvas preview
    fun drawStampToCanvas(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        scaleFactor: Float,
        photoMetadata: SavedPhoto,
        template: StampTemplate,
        is24Hour: Boolean = false,
        dateFormat: String = "dd/MM/yyyy",
        liveTimeString: String = "",
        isPreview: Boolean = false
    ) {
        
        val flag = if (photoMetadata.countryFlag.isNotEmpty()) {
            photoMetadata.countryFlag
        } else {
            "🇮🇳"
        }

        val locationName = if (photoMetadata.customLocationName.isNotEmpty()) {
            photoMetadata.customLocationName
        } else {
            val cleanAddr = photoMetadata.address.ifEmpty { "6/36, Chaitanya Nagar, Doddaballapura, Karnataka 561203, India" }
            val parts = cleanAddr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
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
                cleanAddr.split(",").firstOrNull { it.trim().isNotEmpty() }?.trim() ?: "MapSeal Location"
            }
        }

        val dayStr = if (photoMetadata.customDayOfWeek.isNotEmpty()) photoMetadata.customDayOfWeek else "Friday"
        val dateStr = if (photoMetadata.customDateStr.isNotEmpty()) photoMetadata.customDateStr else "12 Jun 2026"
        val timeStr = if (photoMetadata.customTimeStr.isNotEmpty()) photoMetadata.customTimeStr else "03:10 PM"

        // Setup colors matching overlay configurations safely
        val bgHex = template.backgroundColorHex.ifEmpty { "#1B222D" }
        val parsedBgColor = try {
            if (bgHex.equals("#7C7C80", ignoreCase = true) || bgHex.equals("#6C6C6F", ignoreCase = true) || bgHex.equals("#CC101B26", ignoreCase = true) || bgHex.equals("#E0101B26", ignoreCase = true) || bgHex.equals("#101010", ignoreCase = true) || bgHex.equals("#000000", ignoreCase = true) || bgHex.equals("#1B222D", ignoreCase = true) || bgHex.equals("#222935", ignoreCase = true)) {
                Color.parseColor("#000000") // Changed from slate-dark grey #222935 to black #000000 as requested
            } else {
                Color.parseColor(bgHex)
            }
        } catch (e: Exception) {
            Color.parseColor("#000000")
        }

        val fontHex = template.fontColorHex.ifEmpty { "#FFFFFF" }
        val parsedFontColor = try {
            Color.parseColor(fontHex)
        } catch (e: Exception) {
            Color.WHITE
        }

        val selectedTypeface = when (template.fontFamily) {
            "Serif" -> Typeface.SERIF
            "Monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }

        val fontSizeBase = template.fontSize.coerceIn(8, 11) * 3f

        // Date/Time Formats
        val dateObj = if (liveTimeString.isNotEmpty()) Date() else Date(photoMetadata.timestamp)
        val sdfDay = SimpleDateFormat("EEEE", Locale.US)
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.US)

        val tz = if (photoMetadata.latitude in 8.0..38.0 && photoMetadata.longitude in 68.0..98.0) {
            TimeZone.getTimeZone("Asia/Kolkata")
        } else {
            TimeZone.getDefault()
        }
        sdfDay.timeZone = tz
        sdfDate.timeZone = tz
        sdfTime.timeZone = tz

        val dayName = sdfDay.format(dateObj)
        val dateFormatted = sdfDate.format(dateObj)
        val timeFormatted = sdfTime.format(dateObj).uppercase(Locale.US)

        val finalDay = if (photoMetadata.customDayOfWeek.isNotEmpty()) photoMetadata.customDayOfWeek else dayName
        val finalDate = if (photoMetadata.customDateStr.isNotEmpty()) photoMetadata.customDateStr else dateFormatted
        val finalTime = if (photoMetadata.customTimeStr.isNotEmpty()) photoMetadata.customTimeStr else timeFormatted

        val offsetMillis = tz.getOffset(dateObj.time)
        val offsetHours = Math.abs(offsetMillis / 3600000)
        val offsetMinutes = Math.abs((offsetMillis % 3600000) / 60000)
        val sign = if (offsetMillis >= 0) "+" else "-"
        val offsetStr = String.format("%s%02d:%02d", sign, offsetHours, offsetMinutes)
        val clockTextPrecise = "$finalDay, $finalDate $finalTime GMT $offsetStr"
        val clockTextCompact = "$finalDay, $finalDate $finalTime"

        // Resolve Address split lines intelligently matching StampOverlayComponent.kt
        val cleanAddress = photoMetadata.address.ifEmpty { "6/36, Chaitanya Nagar, Doddaballapura, Karnataka 561203, India" }
        val parts = cleanAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val addressLine1: String
        val addressLine2: String
        val addressLine3: String

        if (parts.size < 3) {
            addressLine1 = cleanAddress
            addressLine2 = cleanAddress
            addressLine3 = ""
        } else {
            val country = parts.last()
            val stateAndZip = parts[parts.size - 2]
            val zipRegex = Regex("\\d{5,6}")
            val zipMatch = zipRegex.find(stateAndZip)
            val zipCode = zipMatch?.value ?: ""
            
            val filteredParts = parts.map { part ->
                var p = part
                if (zipCode.isNotEmpty()) p = p.replace(zipCode, "").trim()
                p
            }.filter { it.isNotEmpty() && it != country }
            
            addressLine2 = filteredParts.joinToString(", ")
            addressLine3 = if (zipCode.isNotEmpty()) "$zipCode, $country" else country
            
            val city = if (parts.size >= 3) parts[parts.size - 3] else ""
            val state = if (zipCode.isNotEmpty()) stateAndZip.replace(zipCode, "").trim() else ""
            addressLine1 = listOfNotNull(city.takeIf { it.isNotEmpty() }, state.takeIf { it.isNotEmpty() }, country.takeIf { it.isNotEmpty() }).joinToString(", ")
        }

        // Setup base paint utilities
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (fontSizeBase + 10).toFloat() * scaleFactor
            typeface = Typeface.create(selectedTypeface, Typeface.NORMAL)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSizeBase.toFloat() * scaleFactor
            typeface = Typeface.create(selectedTypeface, Typeface.NORMAL)
        }
        val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (fontSizeBase + 6).toFloat() * scaleFactor
        }
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedBgColor
            alpha = ((template.bgOpacityPercent / 100f) * 0.45f * 255).toInt().coerceIn(0, 255) // reduced opacity (less dark)
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26FFFFFF")
            strokeWidth = 1f * scaleFactor
            style = Paint.Style.STROKE
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSizeBase.toFloat() * scaleFactor
            typeface = Typeface.create(selectedTypeface, Typeface.NORMAL)
        }

        // Layout geometry configs based on screen placement - slightly reduced height/padding
        val padding = 10f * 3f * scaleFactor
        var totalBoxWidth = 420f * 3f * scaleFactor
        var totalBoxHeight = 120f * 3f * scaleFactor

        // Calculate size based on template style
        val type = template.templateType.uppercase()
        when (type) {
            "CLASSIC", "CUSTOM" -> {
                val mapW = if (template.showMapPreview) 270f * scaleFactor else 0f
                val mapS = if (template.showMapPreview) 18f * scaleFactor else 0f
                val textW = 950f * scaleFactor
                totalBoxWidth = mapW + mapS + textW + padding * 2
                
                totalBoxHeight = 275f * scaleFactor // Height: 275 px
            }
            
            
        }

        // Anchor box based on selected position
        val positionToUse = template.position.uppercase()
        val startX: Float
        val startY: Float
        if (type == "CLASSIC" || type == "CUSTOM") {
            val horizontalMargin = 12f * scaleFactor
            startX = horizontalMargin
            totalBoxWidth = width.toFloat() - horizontalMargin * 2
            when (positionToUse) {
                "TOP_LEFT", "TOP_RIGHT" -> {
                    startY = 12f * scaleFactor
                }
                "CENTER" -> {
                    startY = (height - totalBoxHeight) / 2f
                }
                else -> { // BOTTOM_LEFT, BOTTOM_RIGHT
                    startY = height - totalBoxHeight - 16f * scaleFactor // Leave a small margin below the timestamp when saved
                }
            }
        } else {
            when (positionToUse) {
                "TOP_LEFT" -> {
                    startX = padding
                    startY = padding + (if (type == "ADVANCE") 11f * scaleFactor else 0f)
                }
                "TOP_RIGHT" -> {
                    startX = width - totalBoxWidth - padding
                    startY = padding + (if (type == "ADVANCE") 11f * scaleFactor else 0f)
                }
                "CENTER" -> {
                    startX = (width - totalBoxWidth) / 2f
                    startY = (height - totalBoxHeight) / 2f
                }
                "BOTTOM_RIGHT" -> {
                    startX = width - totalBoxWidth - padding
                    startY = height - totalBoxHeight - padding
                }
                else -> { // BOTTOM_LEFT
                    startX = padding
                    startY = height - totalBoxHeight - padding
                }
            }
        }

        // Draw general layout background unless it has a stylized custom header like DATETIME
        if (type != "DATETIME" && type != "ADVANCE" && type != "CLASSIC" && type != "CUSTOM" && type != "REPORTING") {
            val cardRect = RectF(startX, startY, startX + totalBoxWidth, startY + totalBoxHeight)
            canvas.drawRoundRect(cardRect, 12f * scaleFactor, 12f * scaleFactor, cardBgPaint)
            canvas.drawRoundRect(cardRect, 12f * scaleFactor, 12f * scaleFactor, cardBorderPaint)
        }

        // Switch to corresponding rendering implementations
        // Draw badge globally at the top right of the whole box to match Jetpack Compose overlay exactly
        // Adjusted to be above the box to prevent overlapping
        // val globalBadgeY = if (isPreview) {
        //     maxOf(startY - 60f * scaleFactor, 5f * scaleFactor) // Slightly higher for camera preview
        // } else {
        //     maxOf(startY - 40f * scaleFactor, 5f * scaleFactor) // Slightly lower for saved photo gallery
        // }
        val badgeHeight = 48f * scaleFactor
        val badgeGap = 8f * scaleFactor

        val globalBadgeY = startY - badgeHeight - badgeGap

        drawGpsMapCameraBadge(
            context,
            canvas,
            startX + totalBoxWidth - 18f * scaleFactor,
            globalBadgeY,
            scaleFactor)

        when (type) {
            "CLASSIC", "CUSTOM" -> {
                cardBgPaint.color = Color.BLACK
                cardBgPaint.alpha = (0.58f * 255).toInt() // Black with 55-60% opacity (0.58)

                var currentX = startX
                if (template.showMapPreview) {
                    val mapHeight = 275f * scaleFactor // Height: 275 px
                    val mapW = 270f * scaleFactor // Width: 270 px (Square aspect ratio ~1:1)
                    val mapRect = RectF(startX, startY, startX + mapW, startY + mapHeight)
                    drawOfflineRadarMap(context, canvas, mapRect, scaleFactor, photoMetadata)
                    currentX = startX + mapW + 18f * scaleFactor // Gap reduced to 18f * scale (6.dp)
                    
                    val textBgWidth = 950f * scaleFactor // Timestamp Background: Width 950 px
                    val textBgRight = minOf(currentX + textBgWidth, width.toFloat() - 12f * scaleFactor)
                    val textBgRect = RectF(currentX, startY, textBgRight, startY + mapHeight)
                    canvas.drawRoundRect(textBgRect, 8f * scaleFactor, 8f * scaleFactor, cardBgPaint) // Border radius: 8-10 px
                    currentX += 24f * scaleFactor // Padding inside the background (8.dp equivalent)
                } else {
                    currentX = startX + padding
                    val textBgWidth = 950f * scaleFactor // Timestamp Background: Width 950 px
                    val textBgRight = minOf(startX + textBgWidth, width.toFloat() - 12f * scaleFactor)
                    val textBgRect = RectF(startX, startY, textBgRight, startY + (275f * scaleFactor))
                    canvas.drawRoundRect(textBgRect, 8f * scaleFactor, 8f * scaleFactor, cardBgPaint) // Border radius: 8-10 px
                    currentX += 24f * scaleFactor // Padding inside the background (8.dp equivalent)
                }

                val maxTextWidth = (startX + totalBoxWidth) - currentX - 16f * scaleFactor

                var textY = startY + padding + titlePaint.textSize
                // Title & Flag inline (ellipsized to prevent overflow cut-off on right edge)
                val fullTitle = "$locationName  $flag"
                val elapsedTitle = android.text.TextUtils.ellipsize(
                    fullTitle,
                    android.text.TextPaint(titlePaint),
                    maxTextWidth,
                    android.text.TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(elapsedTitle, currentX, textY, titlePaint)
                
                textY += titlePaint.textSize + 8f * scaleFactor

                // Unified Address Line with automatic wrapping to 2 lines
                val fullAddr = photoMetadata.address.ifEmpty { "6/36, Chaitanya Nagar, Doddaballapura, Karnataka 561203, India" }
                val textPaintWrap = android.text.TextPaint(textPaint)
                val staticLayout = android.text.StaticLayout(
                    fullAddr,
                    textPaintWrap,
                    maxTextWidth.toInt().coerceAtLeast(100),
                    android.text.Layout.Alignment.ALIGN_NORMAL,
                    1.0f,
                    0.0f,
                    false
                )
                
                val lineCount = staticLayout.lineCount
                var currentLineY = textY
                for (i in 0 until minOf(2, lineCount)) {
                    val lineStart = staticLayout.getLineStart(i)
                    val lineEnd = staticLayout.getLineEnd(i)
                    var lineText = fullAddr.substring(lineStart, lineEnd).trim()
                    if (i == 1 && lineCount > 2) {
                        if (lineText.length > 3) {
                            lineText = lineText.substring(0, lineText.length - 3) + "..."
                        } else {
                            lineText += "..."
                        }
                    }
                    canvas.drawText(lineText, currentX, currentLineY, textPaint)
                    currentLineY += textPaint.textSize + 8f * scaleFactor
                }
                textY = currentLineY

                // Coordinates with automatic wrapping/truncation to prevent right-side cut
                if (template.showCoordinates) {
                    val coordsText = "Lat %.6f° Long %.6f°".format(photoMetadata.latitude, photoMetadata.longitude)
                    val coordsLayout = android.text.StaticLayout(
                        coordsText,
                        android.text.TextPaint(textPaint),
                        maxTextWidth.toInt().coerceAtLeast(100),
                        android.text.Layout.Alignment.ALIGN_NORMAL,
                        1.0f,
                        0.0f,
                        false
                    )
                    var currentCoordsY = textY
                    for (i in 0 until coordsLayout.lineCount) {
                        val start = coordsLayout.getLineStart(i)
                        val end = coordsLayout.getLineEnd(i)
                        val lineText = coordsText.substring(start, end).trim()
                        canvas.drawText(lineText, currentX, currentCoordsY, textPaint)
                        currentCoordsY += textPaint.textSize + 8f * scaleFactor
                    }
                    textY = currentCoordsY
                }

                // Time (using light grey timePaint) with automatic wrapping/truncation to prevent right-side cut
                if (template.showTimestamp) {
                    val timeLayout = android.text.StaticLayout(
                        clockTextPrecise,
                        android.text.TextPaint(timePaint),
                        maxTextWidth.toInt().coerceAtLeast(100),
                        android.text.Layout.Alignment.ALIGN_NORMAL,
                        1.0f,
                        0.0f,
                        false
                    )
                    var currentTimeY = textY
                    for (i in 0 until timeLayout.lineCount) {
                        val start = timeLayout.getLineStart(i)
                        val end = timeLayout.getLineEnd(i)
                        val lineText = clockTextPrecise.substring(start, end).trim()
                        canvas.drawText(lineText, currentX, currentTimeY, timePaint)
                        currentTimeY += timePaint.textSize + 8f * scaleFactor
                    }
                    textY = currentTimeY
                }
            }

            
        }
    }

    // Wrap long lines for cleaner stamping look
    private fun wrapText(text: String, limit: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.length + word.length + 1 > limit) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
            }
            if (currentLine.isNotEmpty()) {
                currentLine.append(" ")
            }
            currentLine.append(word)
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines.take(3) // Limit to 3 wrapped lines to avoid stamps spilling out of card
    }

    // Get Compass heading
    private fun getCardinalDirection(deg: Float): String {
        return when (deg.coerceIn(0f, 360f)) {
            in 0.0..22.5 -> "N"
            in 22.5..67.5 -> "NE"
            in 67.5..112.5 -> "E"
            in 112.5..157.5 -> "SE"
            in 157.5..202.5 -> "S"
            in 202.5..247.5 -> "SW"
            in 247.5..292.5 -> "W"
            in 292.5..337.5 -> "NW"
            else -> "N"
        }
    }

    private fun getGoogleTileUrl(latitude: Double, longitude: Double, zoom: Int = 18): String {
        val clat = latitude.coerceIn(-85.05112878, 85.05112878)
        val clon = longitude.coerceIn(-180.0, 180.0)
        val x = kotlin.math.floor((clon + 180.0) / 360.0 * (1 shl zoom)).toInt()
        val latRad = Math.toRadians(clat)
        val mercN = kotlin.math.ln(kotlin.math.tan(Math.PI / 4.0 + latRad / 2.0))
        val y = kotlin.math.floor((1.0 - mercN / Math.PI) / 2.0 * (1 shl zoom)).toInt()
        return "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom"
    }

    // Technical Google-style satellite/terrain map drawable that presents perfectly offline, matching live feedback
    private fun fetchBitmapFromUrl(urlStr: String): Bitmap? {
        var connection: java.net.HttpURLConnection? = null
        var stream: java.io.InputStream? = null
        try {
            val url = java.net.URL(urlStr)
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.doInput = true
            connection.connect()
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                stream = connection.inputStream
                return BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { stream?.close() } catch (ex: Exception) {}
            try { connection?.disconnect() } catch (ex: Exception) {}
        }
        return null
    }

    // Technical Google-style satellite/terrain map drawable that presents perfectly offline, matching live feedback
    private fun drawOfflineRadarMap(context: Context, canvas: Canvas, rect: RectF, scale: Float, metadata: SavedPhoto) {
        val cx = rect.centerX()
        val cy = rect.centerY()

        val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#385E38") // Realistic dark forest/vegetation green
            style = Paint.Style.FILL
        }
        val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B9CD3") // Beautiful scenic river blue
            strokeWidth = 5f * scale
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EEEEEE") // Clean secondary roads
            strokeWidth = 1.2f * scale
            style = Paint.Style.STROKE
        }
        val mainHwyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FCD116") // Iconic yellow highway artery
            strokeWidth = 2.5f * scale
            style = Paint.Style.STROKE
        }
        val landFeaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#446E44") // Secondary terrain shading
            style = Paint.Style.FILL
        }
        val outlineBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26FFFFFF") // 15% thin white border line
            strokeWidth = 1f * scale
            style = Paint.Style.STROKE
        }

        // Use the exact Google Maps image provided as a constant resource
        var satelliteBitmap: Bitmap? = null
        try {
            satelliteBitmap = BitmapFactory.decodeResource(context.resources, com.example.R.drawable.google_map_tile)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (satelliteBitmap != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(rect, 8f * scale, 8f * scale, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val srcRect = Rect(0, 0, satelliteBitmap.width, satelliteBitmap.height)
            canvas.drawBitmap(satelliteBitmap, srcRect, rect, paint)
            
            // Remove watermark by drawing white rectangles over them
            val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            
            canvas.restore()
        } else {
            // Draw map frame background (satellite green fallback)
            canvas.drawRoundRect(rect, 8f * scale, 8f * scale, terrainPaint)

            // Draw topological land shading features
            val forestPath1 = Path().apply {
                moveTo(rect.left, rect.bottom)
                lineTo(rect.left + rect.width() * 0.4f, rect.bottom)
                lineTo(rect.left, rect.top + rect.height() * 0.3f)
                close()
            }
            canvas.drawPath(forestPath1, landFeaturePaint)

            // Draw winding scenic blue river
            val riverPath = Path().apply {
                moveTo(rect.left, rect.top + rect.height() * 0.1f)
                cubicTo(
                    rect.left + rect.width() * 0.3f, rect.top - 10f,
                    rect.left + rect.width() * 0.4f, rect.bottom - rect.height() * 0.2f,
                    rect.right, rect.bottom - rect.height() * 0.15f
                )
            }
            canvas.drawPath(riverPath, riverPaint)

            // Draw street network system (grids & arteries)
            val roadPath = Path().apply {
                // Horizontal secondary streets
                moveTo(rect.left, cy - 25f * scale)
                lineTo(rect.right, cy - 20f * scale)
                moveTo(rect.left, cy + 30f * scale)
                lineTo(rect.right, cy + 35f * scale)
                // Vertical secondary streets
                moveTo(cx - 20f * scale, rect.top)
                lineTo(cx - 25f * scale, rect.bottom)
                moveTo(cx + 30f * scale, rect.top)
                lineTo(cx + 25f * scale, rect.bottom)
            }
            canvas.drawPath(roadPath, roadPaint)

            // Draw prime arterial golden highway crossing the region
            val hwyPath = Path().apply {
                moveTo(rect.left, cy + 10f * scale)
                lineTo(rect.right, cy - 5f * scale)
            }
            canvas.drawPath(hwyPath, mainHwyPaint)
        }

        // Draw white frame border outline
        canvas.drawRoundRect(rect, 8f * scale, 8f * scale, outlineBorderPaint)

        // Draw light semi-transparent blue projection radar cone pointing down-right (matching the reference image's compass wedge)
        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D4285F4") // 30% alpha Google Blue
            style = Paint.Style.FILL
        }
        val conePath = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + 6f * scale, cy + 24f * scale)
            lineTo(cx + 24f * scale, cy + 6f * scale)
            close()
        }
        canvas.drawPath(conePath, conePaint)
    }

    private fun drawGpsMapCameraBadge(context: Context, canvas: Canvas, x: Float, y: Float, scaleFactor: Float) {
        // Container Size: 300 x 60 px
        val badgeHeight = 60f * scaleFactor
        val badgeWidth = 300f * scaleFactor
        val badgeRect = RectF(x - badgeWidth, y, x+10f* scaleFactor, y + badgeHeight)
        
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.58f * 255).toInt(), 0, 0, 0) // Black with 55-60% opacity (0.58f)
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            strokeWidth = 0.5f * scaleFactor
            style = Paint.Style.STROKE
        }
        
        val path = Path().apply {
            val r = 8f * scaleFactor
            moveTo(badgeRect.left, badgeRect.bottom)
            lineTo(badgeRect.left, badgeRect.top + r)
            quadTo(badgeRect.left, badgeRect.top, badgeRect.left + r, badgeRect.top)
            lineTo(badgeRect.right - r, badgeRect.top)
            quadTo(badgeRect.right, badgeRect.top, badgeRect.right, badgeRect.top + r)
            lineTo(badgeRect.right, badgeRect.bottom)
            close()
        }
        
        canvas.drawPath(path, badgeBgPaint)
        canvas.drawPath(path, badgeBorderPaint)
        
        // Draw the image
        val drawable = ContextCompat.getDrawable(context, R.drawable.gps_map_camera_badge_image)
        if (drawable != null) {
            val iconSize = 35f * scaleFactor
            val iconLeft = badgeRect.left + 12f * scaleFactor
            val iconTop = badgeRect.centerY() - iconSize / 2
            drawable.setBounds(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
            drawable.draw(canvas)
        }
        val logoCenterX = badgeRect.left + 12f * scaleFactor + 17f * scaleFactor
        val logoCenterY = badgeRect.centerY()
        val logoRadius = 17f * scaleFactor

        val orangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF9800")
            style = Paint.Style.STROKE
            strokeWidth = 0.6f * scaleFactor
        }
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius * 0.85f, orangePaint)

        val darkBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E3A4B")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius * 0.7f, darkBluePaint)

        val blackCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10171E")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius * 0.45f, blackCorePaint)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(logoCenterX + logoRadius * 0.22f, logoCenterY - logoRadius * 0.22f, logoRadius * 0.15f, whitePaint)
        canvas.drawCircle(logoCenterX + logoRadius * 0.35f, logoCenterY + logoRadius * 0.05f, logoRadius * 0.08f, whitePaint)
        
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f * scaleFactor // Font Size increased
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) // Font Weight: Medium (500)
        }
        
        val textX = logoCenterX + logoRadius + 12f * scaleFactor // Padding increased
        val textY = badgeRect.centerY() + (badgeTextPaint.textSize / 2f) - (badgeTextPaint.descent() / 2f)
        canvas.drawText("GPS Map Camera", textX, textY, badgeTextPaint)
    }

    // Interface triggers photo saving with optional EXIF metadata syncing
    fun saveBitmapToFile(
        bitmap: Bitmap, 
        file: File, 
        metadata: SavedPhoto? = null,
        is24Hour: Boolean = false,
        dateFormat: String = "dd/MM/yyyy"
    ): Uri {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        if (metadata != null) {
            writeExifMetadata(file, metadata, is24Hour, dateFormat)
        }
        return Uri.fromFile(file)
    }

    private fun writeExifMetadata(file: File, metadata: SavedPhoto, is24Hour: Boolean, dateFormat: String) {
        try {
            val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
            
            // Resolve timezone matching photo location or default
            val tz = if (metadata.latitude in 8.0..38.0 && metadata.longitude in 68.0..98.0) {
                TimeZone.getTimeZone("Asia/Kolkata")
            } else {
                TimeZone.getDefault()
            }
            
            // Parse custom date/time if custom values exist, otherwise fall back to metadata.timestamp
            val dateObj = if (metadata.customDateStr.isNotEmpty() && metadata.customTimeStr.isNotEmpty()) {
                try {
                    // Try to parse the custom defined values
                    val customSdf = SimpleDateFormat("$dateFormat hh:mm a", Locale.US)
                    customSdf.timeZone = tz
                    val parsed = customSdf.parse("${metadata.customDateStr} ${metadata.customTimeStr}")
                    parsed ?: Date(metadata.timestamp)
                } catch (e: Exception) {
                    try {
                        val custom24Sdf = SimpleDateFormat("$dateFormat HH:mm", Locale.US)
                        custom24Sdf.timeZone = tz
                        val parsed = custom24Sdf.parse("${metadata.customDateStr} ${metadata.customTimeStr}")
                        parsed ?: Date(metadata.timestamp)
                    } catch (e2: Exception) {
                        try {
                            val customAltSdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US)
                            customAltSdf.timeZone = tz
                            val parsed = customAltSdf.parse("${metadata.customDateStr} ${metadata.customTimeStr}")
                            parsed ?: Date(metadata.timestamp)
                        } catch (e3: Exception) {
                            Date(metadata.timestamp)
                        }
                    }
                }
            } else {
                Date(metadata.timestamp)
            }

            val sdfExif = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            sdfExif.timeZone = tz
            val formattedDateTime = sdfExif.format(dateObj)

            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, formattedDateTime)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, formattedDateTime)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, formattedDateTime)

            // Timezone offsets
            val offsetMillis = tz.getOffset(dateObj.time)
            val offsetHours = Math.abs(offsetMillis / 3600000)
            val offsetMinutes = Math.abs((offsetMillis % 3600000) / 60000)
            val sign = if (offsetMillis >= 0) "+" else "-"
            val offsetStr = String.format("%s%02d:%02d", sign, offsetHours, offsetMinutes)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME, offsetStr)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetStr)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offsetStr)

            // Write coordinates if we have valid ones
            if (metadata.latitude != 0.0 || metadata.longitude != 0.0) {
                exif.setLatLong(metadata.latitude, metadata.longitude)
            }

            // Write altitude/elevation
            val elev = metadata.elevation
            if (!elev.isNaN() && elev != 0.0) {
                val absElev = Math.abs(elev)
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE, "$absElev/1")
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF, if (elev >= 0) "0" else "1")
            }

            // Write user comment (address) and description (custom place name)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT, metadata.address)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION, metadata.customLocationName.ifEmpty { "GeoStamp Image" })

            exif.saveAttributes()
        } catch (e: Exception) {
            android.util.Log.e("GeoStamp", "Failed helper writeExifMetadata: ${e.message}", e)
        }
    }
}
