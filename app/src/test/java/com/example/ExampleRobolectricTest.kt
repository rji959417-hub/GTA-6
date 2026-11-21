package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Vice Horizon", appName)
  }

  @Test
  fun `main activity enforces sensor landscape orientation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info =
      context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, info.screenOrientation)

    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
  }
}
