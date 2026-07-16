package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "saved_photos")
data class SavedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val timestamp: Long,
    val notes: String = "",
    val projectName: String = "",
    val siteName: String = "",
    val inspectionNumber: String = "",
    val templateId: Int = 1,
    val weatherTemp: String = "",
    val weatherCondition: String = "",
    val compassDirection: Float = 0f,
    val speed: Float = 0f,
    val elevation: Double = 0.0,
    // Customization fields matching user preferences
    val customLocationName: String = "",
    val customDayOfWeek: String = "",
    val countryFlag: String = "",
    val customDateStr: String = "",
    val customTimeStr: String = ""
) : Serializable

@Entity(tableName = "stamp_templates")
data class StampTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val templateType: String, // MINIMAL, DETAILS, PROFESSIONAL, MAP_PREVIEW, CUSTOM
    val fontFamily: String = "SansSerif",
    val fontSize: Int = 14,
    val fontColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#42000000", // Dark semi-transparent
    val bgOpacityPercent: Int = 50,
    val position: String = "BOTTOM_LEFT", // TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    val showMapPreview: Boolean = false,
    val showWeather: Boolean = true,
    val showCoordinates: Boolean = true,
    val showTimestamp: Boolean = true,
    val showNotes: Boolean = true,
    val showDetails: Boolean = true,
    val logoPath: String? = null
) : Serializable

@Entity(tableName = "favorite_locations")
data class FavoriteLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String
) : Serializable
