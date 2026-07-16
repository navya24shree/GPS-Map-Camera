package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.camera.PhotoStampingEngine
import com.example.data.SavedPhoto
import com.example.data.StampTemplate

@Composable
fun StampOverlayLayout(
    metadata: SavedPhoto,
    template: StampTemplate,
    modifier: Modifier = Modifier,
    liveTimeString: String = "",
    showAddress: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.toInt()
        
        val type = template.templateType.uppercase()
        val scaleFactor = (widthPx / 1080f).coerceIn(0.6f, 5.0f)
        val padding = 10f * 3f * scaleFactor
        val fontSizeBase = template.fontSize.coerceIn(8, 11) * 3f

        // Calculate size based on template style exactly like PhotoStampingEngine
        val totalHeightPx = when (type) {
            "CLASSIC", "CUSTOM" -> {
                275f * scaleFactor
            }
            "REPORTING" -> {
                var h = 30f * 3f * scaleFactor + 8f * 3f * scaleFactor
                var textH = (fontSizeBase + 6f).toFloat() * scaleFactor + 12f * scaleFactor
                textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showCoordinates) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showTimestamp) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                h += maxOf(if (template.showMapPreview) 75f * 3f * scaleFactor else 0f, textH)
                h + padding * 2
            }
            "NAVIGATION_COMPASS" -> {
                var textH = (fontSizeBase + 4f).toFloat() * scaleFactor + 12f * scaleFactor
                textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showCoordinates) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showTimestamp) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                textH += fontSizeBase * scaleFactor + 12f * scaleFactor
                textH += 48f * scaleFactor
                maxOf(90f * 3f * scaleFactor, textH) + padding * 2
            }
            "ADVANCE" -> {
                var textH = (fontSizeBase + 4f).toFloat() * scaleFactor + 24f * scaleFactor
                textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showCoordinates) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showTimestamp) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                maxOf(if (template.showMapPreview) 90f * 3f * scaleFactor else 0f, textH) + padding * 2
            }
            "DATETIME" -> {
                var textH = (fontSizeBase + 4f).toFloat() * scaleFactor + 12f * scaleFactor
                textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showCoordinates) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                44f * 3f * scaleFactor + 6f * 3f * scaleFactor + textH + padding * 2
            }
            "SCAN_LOCATION" -> {
                var textH = (fontSizeBase + 4f).toFloat() * scaleFactor + 12f * scaleFactor
                textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showCoordinates) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                if (template.showTimestamp) textH += fontSizeBase * scaleFactor + 6f * scaleFactor
                maxOf(75f * 3f * scaleFactor, textH) + padding * 2
            }
            else -> {
                120f * 3f * scaleFactor
            }
        }

        // Add 50f * scaleFactor margin for the top-floating App badge
        val finalCanvasHeightPx = totalHeightPx
        val totalHeightDp = with(density) { finalCanvasHeightPx.toDp() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
        ) {
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val displayMetadata = if (showAddress) metadata else metadata.copy(address = "")
                PhotoStampingEngine.drawStampToCanvas(
                    context = context,
                    canvas = nativeCanvas,
                    width = widthPx,
                    height = finalCanvasHeightPx.toInt(),
                    scaleFactor = scaleFactor,
                    photoMetadata = displayMetadata,
                    template = template,
                    is24Hour = false,
                    dateFormat = "dd/MM/yyyy",
                    liveTimeString = liveTimeString,
                    isPreview = true
                )
            }
        }
    }
}
