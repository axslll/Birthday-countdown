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
    @org.junit.Before
    fun resetTracker() {
        // Tracker is a process-wide singleton; start every test from a clean state
        Tracker::class.java.getDeclaredMethod("publish", TrackerState::class.java)
            .apply { isAccessible = true }.invoke(Tracker, TrackerState())
    }

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

    @Test
    fun powerButtonStartsAndStopsTracking() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val button = controller.get().findViewById<android.widget.TextView>(R.id.powerButton)
        org.junit.Assert.assertEquals("Start tracking", button.text.toString())
        org.junit.Assert.assertFalse(Tracker.state.running)

        button.performClick()
        org.junit.Assert.assertNotNull(org.robolectric.Shadows.shadowOf(app).nextStartedService)

        // service comes up -> UI flips to "Stop tracking"
        val svc = Robolectric.buildService(TrackerService::class.java, android.content.Intent(app, TrackerService::class.java))
            .create().startCommand(0, 1)
        org.junit.Assert.assertTrue(Tracker.state.running)
        org.junit.Assert.assertEquals("Stop tracking", button.text.toString())

        button.performClick()
        org.junit.Assert.assertNotNull(org.robolectric.Shadows.shadowOf(app).nextStoppedService)
        svc.destroy()
        org.junit.Assert.assertFalse(Tracker.state.running)
        org.junit.Assert.assertEquals("Start tracking", button.text.toString())
    }

    @Test
    fun constellationRowsRenderWithFlags() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val gps = Gnss("GPS", "Global Positioning System", "United States", R.drawable.flag_us)
        val sbas = Gnss("SBAS", "Satellite-Based Augmentation System", "Regional correction service", null)
        val m = Tracker::class.java.getDeclaredMethod("publish", TrackerState::class.java).apply { isAccessible = true }
        m.invoke(Tracker, Tracker.state.copy(running = true, used = 7, visible = 17,
            constellations = listOf(ConstellationStat(gps, 7, 16), ConstellationStat(sbas, 0, 1))))
        val list = controller.get().findViewById<android.widget.LinearLayout>(R.id.constellations)
        org.junit.Assert.assertEquals(2, list.childCount)
        org.junit.Assert.assertEquals("United States", list.getChildAt(0).findViewById<android.widget.TextView>(R.id.country).text.toString())
        org.junit.Assert.assertEquals(android.view.View.VISIBLE, list.getChildAt(0).findViewById<android.view.View>(R.id.flag).visibility)
        org.junit.Assert.assertEquals(android.view.View.GONE, list.getChildAt(1).findViewById<android.view.View>(R.id.flag).visibility)
    }
}
