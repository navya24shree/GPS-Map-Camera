package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SavedPhoto::class, StampTemplate::class, FavoriteLocation::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun templateDao(): TemplateDao
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geostamp_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                try {
                    // Seed defaults for stamp_templates - KEEP ONLY CLASSIC TEMPLATE
                    db.execSQL("INSERT OR IGNORE INTO stamp_templates (id, name, templateType, fontFamily, fontSize, fontColorHex, backgroundColorHex, bgOpacityPercent, position, showMapPreview, showWeather, showCoordinates, showTimestamp, showNotes, showDetails, logoPath) VALUES (1, 'Classic Template', 'CLASSIC', 'SansSerif', 14, '#2DF380', '#CC101B26', 45, 'BOTTOM_LEFT', 1, 0, 1, 1, 0, 1, NULL);")

                    // Seed defaults for favorite_locations
                    db.execSQL("INSERT OR IGNORE INTO favorite_locations (id, name, latitude, longitude, address) VALUES (1, 'Mysore Palace', 12.3052, 76.6552, 'Mysore Palace, Sayyaji Rao Rd, Devaraja Mohalla, Mysuru, Karnataka 570001, India');")
                    db.execSQL("INSERT OR IGNORE INTO favorite_locations (id, name, latitude, longitude, address) VALUES (2, 'Udupi Beach', 13.3492, 74.6811, 'Malpe Beach, Udupi, Karnataka 576108, India');")
                } catch (e: Throwable) {
                    android.util.Log.e("GeoStamp", "Synchronous database seeding failed: ${e.message}", e)
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    // Strictly keep only classic template (id = 1), delete any others
                    db.execSQL("DELETE FROM stamp_templates WHERE id != 1")
                } catch (e: Throwable) {
                    android.util.Log.e("GeoStamp", "Failed cleaning up non-classic templates: ${e.message}", e)
                }
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                onCreate(db)
            }
        }
    }
}
