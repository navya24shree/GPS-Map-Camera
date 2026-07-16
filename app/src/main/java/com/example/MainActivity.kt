package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GeoStampViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Intercept all uncaught exceptions to show crash diagnostics screen
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("GeoStampCrash", "Uncaught exception intercepted in GeoStamp", throwable)
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("crash_error", stackTrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                previousHandler?.uncaughtException(thread, throwable)
            }
            Process.killProcess(Process.myPid())
            System.exit(10)
        }

        super.onCreate(savedInstanceState)

        val crashError = intent.getStringExtra("crash_error")
        if (crashError != null) {
            setContent {
                MyApplicationTheme(darkTheme = true) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        CrashScreen(
                            stackTrace = crashError,
                            modifier = Modifier.padding(innerPadding),
                            onRestart = {
                                val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
                                if (restartIntent != null) {
                                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    startActivity(restartIntent)
                                }
                                finish()
                            }
                        )
                    }
                }
            }
            return
        }

        enableEdgeToEdge()
        setContent {
            val viewModel: GeoStampViewModel = viewModel()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val isDarkTheme by viewModel.darkThemeEnabled.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)
                    
                    when (currentScreen.uppercase()) {
                        "SPLASH" -> {
                            SplashScreen(
                                onNavigateToHome = { viewModel.navigateTo("CAMERA") }
                            )
                        }
                        "CAMERA" -> {
                            CameraScreen(
                                viewModel = viewModel,
                                onNavigateBack = { finish() }
                            )
                        }
                        "PHOTO_EDITOR" -> {
                            PhotoEditorScreen(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateTo("CAMERA") }
                            )
                        }
                        "TEMPLATE_MANAGER" -> {
                            TemplateManagerScreen(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateTo("CAMERA") }
                            )
                        }
                        "MAP_PICKER" -> {
                            MapLocationPickerScreen(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateTo("CAMERA") }
                            )
                        }
                        "GALLERY" -> {
                            GalleryScreen(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateTo("CAMERA") }
                            )
                        }
                        "PHOTO_PREVIEW" -> {
                            FullScreenPhotoPreview(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateTo("GALLERY") }
                            )
                        }
                        "SETTINGS" -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateTo("CAMERA") }
                            )
                        }
                        else -> {
                            CameraScreen(
                                viewModel = viewModel,
                                onNavigateBack = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CrashScreen(
    stackTrace: String,
    modifier: Modifier = Modifier,
    onRestart: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Crash icon",
                tint = Color(0xFFFF1744),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Application Crash Detected",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The application encountered an unexpected error on startup. Please copy the details below to share them for troubleshooting.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = stackTrace,
                            color = Color(0xFFFF8A80),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Crash Stack Trace", stackTrace)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied details to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Details", color = Color.White)
                }
                
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DF380)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restart App", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

