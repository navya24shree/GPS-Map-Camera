package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.SavedPhoto
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportPhotoToPdf(context: Context, photo: SavedPhoto, targetFile: File) {
        val pdfDocument = PdfDocument()
        
        // Setup A4 dimensions (595 x 842 points)
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Prepare paints
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 36, 51) // Deep blue branding
            textSize = 20f
            isFakeBoldText = true
        }

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 150, 243)
            textSize = 14f
            isFakeBoldText = true
        }

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 247, 250)
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // Draw title header
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 60f, Paint().apply { color = Color.rgb(21, 36, 51) })
        headerPaint.color = Color.WHITE
        canvas.drawText("MapSeal GPS Camera Report", 24f, 38f, headerPaint)

        // Date generated metadata
        val dateGen = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date())
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
        }
        canvas.drawText("Generated: $dateGen", pageWidth - 160f, 35f, datePaint)

        // Draw Photo image
        val photoFile = File(photo.filePath)
        if (photoFile.exists()) {
            val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (rawBitmap != null) {
                // Resize photo to fit comfortably on the page
                val maxWidth = pageWidth - 48
                val maxHeight = 340
                
                var widthRatio = maxWidth.toFloat() / rawBitmap.width
                var heightRatio = maxHeight.toFloat() / rawBitmap.height
                var ratio = widthRatio.coerceAtMost(heightRatio)
                
                val drawWidth = (rawBitmap.width * ratio).toInt()
                val drawHeight = (rawBitmap.height * ratio).toInt()
                
                val scaledPhoto = Bitmap.createScaledBitmap(rawBitmap, drawWidth, drawHeight, true)
                val photoX = (pageWidth - drawWidth) / 2f
                val photoY = 80f
                
                // Draw picture item on PDF
                canvas.drawBitmap(scaledPhoto, photoX, photoY, null)
                
                // Frame the photo
                canvas.drawRect(photoX, photoY, photoX + drawWidth, photoY + drawHeight, borderPaint)
            }
        }

        // Inspection Details Section
        val detailStartY = 450f
        canvas.drawText("FIELD SURVEY METADATA", 24f, detailStartY, sectionTitlePaint)
        canvas.drawLine(24f, detailStartY + 6, pageWidth - 24f, detailStartY + 6, borderPaint)

        // Details card background bounding box
        val cardRectLeft = 24f
        val cardRectTop = detailStartY + 15
        val cardRectRight = pageWidth - 24f
        val cardRectBottom = pageHeight - 40f
        canvas.drawRoundRect(cardRectLeft, cardRectTop, cardRectRight, cardRectBottom, 8f, 8f, cardPaint)
        canvas.drawRoundRect(cardRectLeft, cardRectTop, cardRectRight, cardRectBottom, 8f, 8f, borderPaint)

        // Write labels and details inside card
        var infoY = cardRectTop + 24f
        val lineGap = 18f
        
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            isFakeBoldText = true
        }

        // Write out pairs
        val details = listOf(
            "Address:" to photo.address,
            "Coordinates:" to "Latitude: ${photo.latitude}, Longitude: ${photo.longitude}",
            "Altitude:" to "${photo.elevation} meters",
            "Survey Date:" to SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm:ss", Locale.getDefault()).format(Date(photo.timestamp)),
            "Project Name:" to (photo.projectName.ifBlank { "N/A (Unspecified)" }),
            "Site Name:" to (photo.siteName.ifBlank { "N/A (Unspecified)" }),
            "Inspection ID:" to (photo.inspectionNumber.ifBlank { "N/A" }),
            "Custom Notes:" to (photo.notes.ifBlank { "No notes attached to this survey." })
        )

        for ((label, value) in details) {
            canvas.drawText(label, 40f, infoY, labelPaint)
            
            // Note could wrap, handle note or address layout simply
            if (value.length > 55) {
                val line1 = value.substring(0, 52) + "..."
                canvas.drawText(line1, 150f, infoY, valuePaint)
            } else {
                canvas.drawText(value, 150f, infoY, valuePaint)
            }
            infoY += lineGap
        }

        // PDF Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 8f
        }
        canvas.drawText("Report created with MapSeal GPS Camera - Secure, Offline & Ad-Free Pro", 24f, pageHeight - 16f, footerPaint)

        pdfDocument.finishPage(page)
        
        FileOutputStream(targetFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }
}
