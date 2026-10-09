package com.example.speedometer

import android.content.Context
import android.location.GnssStatus
import android.location.Location
import java.util.concurrent.CopyOnWriteArrayList

/** A satellite navigation system: short name, spelled-out name, operator and flag (null when not a single country). */
data class Gnss(val name: String, val fullName: String, val country: String, val flag: Int?)

data class ConstellationStat(val system: Gnss, val used: Int, val visible: Int)

data class TrackerState(
    val running: Boolean = false,
    val gpsOn: Boolean = true,
    val speedMs: Float = 0f,
    val maxMs: Float = 0f,
    val avgMs: Float = 0f,
    val distanceM: Double = 0.0,
    val used: Int = 0,
    val visible: Int = 0,
    val avgCn0: Float = 0f,
    val constellations: List<ConstellationStat> = emptyList(),
)

/** Process-wide trip + satellite state. Written by [TrackerService], read by the UI and the notification. */
object Tracker {
    private const val PREFS = "speedometer"
    private const val KEY_MPH = "mph"

    var state = TrackerState()
        private set
    var useMph = false
        private set

    private val listeners = CopyOnWriteArrayList<(TrackerState) -> Unit>()
    private var movingSeconds = 0.0
    private var last: Location? = null
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        useMph = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MPH, false)
    }

    fun setMph(context: Context, mph: Boolean) {
        useMph = mph
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MPH, mph).apply()
        publish(state)
    }

    fun toUnit(ms: Float) = ms * if (useMph) 2.236936f else 3.6f
    val unitLabel get() = if (useMph) "mph" else "km/h"

    fun addListener(l: (TrackerState) -> Unit) { listeners += l; l(state) }
    fun removeListener(l: (TrackerState) -> Unit) { listeners -= l }

    private fun publish(s: TrackerState) {
        state = s
        listeners.forEach { it(s) }
    }

    fun setRunning(on: Boolean) = publish(
        if (on) state.copy(running = true)
        else state.copy(running = false, speedMs = 0f, used = 0, visible = 0, avgCn0 = 0f, constellations = emptyList())
    )

    fun setGpsOn(on: Boolean) = publish(state.copy(gpsOn = on, speedMs = if (on) state.speedMs else 0f))

    fun onLocation(loc: Location) {
        var speed = if (loc.hasSpeed()) loc.speed else 0f
        if (speed < 0.5f) speed = 0f   // GPS jitter while standing still

        var distance = state.distanceM
        last?.let { prev ->
            val dt = (loc.time - prev.time) / 1000.0
            if (speed > 0f && dt in 0.0..10.0) {
                distance += prev.distanceTo(loc)
                movingSeconds += dt
            }
        }
        last = loc
        val avg = if (movingSeconds > 0) (distance / movingSeconds).toFloat() else 0f
        publish(state.copy(speedMs = speed, maxMs = maxOf(state.maxMs, speed), avgMs = avg, distanceM = distance))
    }

    fun onSatellites(s: GnssStatus) {
        val visible = linkedMapOf<Gnss, Int>()
        val used = linkedMapOf<Gnss, Int>()
        var usedCount = 0
        var cn0 = 0f
        for (i in 0 until s.satelliteCount) {
            val name = systemFor(s.getConstellationType(i))
            visible[name] = (visible[name] ?: 0) + 1
            if (s.usedInFix(i)) {
                usedCount++
                cn0 += s.getCn0DbHz(i)
                used[name] = (used[name] ?: 0) + 1
            }
        }
        val list = visible.map { (n, v) -> ConstellationStat(n, used[n] ?: 0, v) }
            .sortedWith(compareByDescending<ConstellationStat> { it.used }.thenByDescending { it.visible })
        publish(
            state.copy(
                used = usedCount,
                visible = s.satelliteCount,
                avgCn0 = if (usedCount > 0) cn0 / usedCount else 0f,
                constellations = list,
            )
        )
    }

    fun clearSatellites() = publish(state.copy(used = 0, visible = 0, avgCn0 = 0f, constellations = emptyList()))

    fun resetTrip() {
        movingSeconds = 0.0
        last = null
        publish(state.copy(maxMs = 0f, avgMs = 0f, distanceM = 0.0))
    }

    /** Maps typical 15–50 dB-Hz carrier-to-noise range to 0..1. */
    fun signalFraction(cn0: Float) = ((cn0 - 15f) / 35f).coerceIn(0f, 1f)

    fun signalQuality(s: TrackerState) = when {
        s.used == 0 -> "No signal"
        signalFraction(s.avgCn0) >= 0.7f -> "Excellent"
        signalFraction(s.avgCn0) >= 0.45f -> "Good"
        signalFraction(s.avgCn0) >= 0.25f -> "Fair"
        else -> "Weak"
    }

    private val gps = Gnss("GPS", "Global Positioning System", "United States", R.drawable.flag_us)
    private val glonass = Gnss("GLONASS", "Global Navigation Satellite System", "Russia", R.drawable.flag_ru)
    private val galileo = Gnss("Galileo", "Named after astronomer Galileo Galilei", "European Union", R.drawable.flag_eu)
    private val beidou = Gnss("BeiDou", "BeiDou Navigation Satellite System", "China", R.drawable.flag_cn)
    private val qzss = Gnss("QZSS", "Quasi-Zenith Satellite System", "Japan", R.drawable.flag_jp)
    private val navic = Gnss("NavIC", "Navigation with Indian Constellation", "India", R.drawable.flag_in)
    private val sbas = Gnss("SBAS", "Satellite-Based Augmentation System", "Regional correction service", null)
    private val other = Gnss("Other", "Unknown system", "Unknown", null)

    private fun systemFor(type: Int) = when (type) {
        GnssStatus.CONSTELLATION_GPS -> gps
        GnssStatus.CONSTELLATION_GLONASS -> glonass
        GnssStatus.CONSTELLATION_GALILEO -> galileo
        GnssStatus.CONSTELLATION_BEIDOU -> beidou
        GnssStatus.CONSTELLATION_QZSS -> qzss
        GnssStatus.CONSTELLATION_IRNSS -> navic
        GnssStatus.CONSTELLATION_SBAS -> sbas
        else -> other
    }
}
