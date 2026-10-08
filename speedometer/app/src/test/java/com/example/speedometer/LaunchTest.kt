package com.example.speedometer

import android.location.GnssStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {
    @Test
    fun activityStartsAndRenders() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        Tracker.onLocation(android.location.Location("gps").apply { speed = 20f; time = 1000 })
        Tracker.setMph(controller.get(), true)
        Tracker.setMph(controller.get(), false)
        controller.pause().stop().destroy()
    }

    @Test
    fun serviceStartsForeground() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app as android.app.Application).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        val intent = android.content.Intent(app, TrackerService::class.java)
        val c = Robolectric.buildService(TrackerService::class.java, intent).create().startCommand(0, 1)
        Tracker.onLocation(android.location.Location("gps").apply { speed = 15f; time = 2000 })
        c.destroy()
    }
}
