package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.ui.GeoStampViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GPS Map Camera", appName)
  }

  @Test
  fun `initialize viewModel and database`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = GeoStampViewModel(application)
    assertNotNull(viewModel)
  }

  @Test
  fun `launch MainActivity`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      assertNotNull(scenario)
    }
  }

  @Test
  fun `launch MainActivity and wait for CameraScreen`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      assertNotNull(scenario)
      // Idle the main looper to advance the virtual clock by 3 seconds, letting the SplashScreen transition complete
      org.robolectric.shadows.ShadowLooper.idleMainLooper(3000)
    }
  }
}
