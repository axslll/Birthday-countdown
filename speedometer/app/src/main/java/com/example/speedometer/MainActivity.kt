package com.example.speedometer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private lateinit var speedometer: SpeedometerView
    private lateinit var unitKmh: TextView
    private lateinit var unitMph: TextView
    private lateinit var signalBar: ProgressBar
    private lateinit var signalLabel: TextView
    private lateinit var constellations: TextView
    private lateinit var status: TextView

    private lateinit var cardMax: StatCard
    private lateinit var cardAvg: StatCard
    private lateinit var cardDist: StatCard
    private lateinit var cardUsed: StatCard
    private lateinit var cardVisible: StatCard
    private lateinit var cardSignal: StatCard

    private var useMph = false
    private var speedMs = 0f
    private var maxMs = 0f
    private var distanceM = 0.0
    private var movingSeconds = 0.0
    private var lastLocation: Location? = null

    private class StatCard(root: View, labelText: String) {
        private val value: TextView = root.findViewById(R.id.value)
        init { root.findViewById<TextView>(R.id.label).text = labelText }
        fun set(text: String) { value.text = text }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) startTracking()
            else status.text = "Location permission is required for speed and satellite data."
        }

    private val locationListener = LocationListener { loc -> onLocation(loc) }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(s: GnssStatus) = onSatellites(s)
        override fun onFirstFix(ttffMillis: Int) { status.text = "GPS fix acquired" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        speedometer = findViewById(R.id.speedometer)
        unitKmh = findViewById(R.id.unitKmh)
        unitMph = findViewById(R.id.unitMph)
        signalBar = findViewById(R.id.signalBar)
        signalLabel = findViewById(R.id.signalLabel)
        constellations = findViewById(R.id.constellations)
        status = findViewById(R.id.status)

        cardMax = StatCard(findViewById(R.id.cardMax), "MAX")
        cardAvg = StatCard(findViewById(R.id.cardAvg), "AVG")
        cardDist = StatCard(findViewById(R.id.cardDist), "DISTANCE")
        cardUsed = StatCard(findViewById(R.id.cardUsed), "IN USE")
        cardVisible = StatCard(findViewById(R.id.cardVisible), "AVAILABLE")
        cardSignal = StatCard(findViewById(R.id.cardSignal), "AVG dB-Hz")

        unitKmh.setOnClickListener { setUnit(false) }
        unitMph.setOnClickListener { setUnit(true) }
        findViewById<View>(R.id.cardMax).setOnLongClickListener { resetTrip(); true }
        status.text = "Long-press MAX to reset trip stats"

        setUnit(false)
        refreshStats()
    }

    override fun onStart() {
        super.onStart()
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startTracking() else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    override fun onStop() {
        super.onStop()
        locationManager.removeUpdates(locationListener)
        locationManager.unregisterGnssStatusCallback(gnssCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            status.text = "GPS is off — enable Location in system settings."
            return
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locationListener)
        locationManager.registerGnssStatusCallback(gnssCallback, null)
        status.text = "Searching for satellites…"
    }

    private fun onLocation(loc: Location) {
        speedMs = if (loc.hasSpeed()) loc.speed else 0f
        // ignore GPS jitter when standing still
        if (speedMs < 0.5f) speedMs = 0f
        if (speedMs > maxMs) maxMs = speedMs

        lastLocation?.let { prev ->
            val dt = (loc.time - prev.time) / 1000.0
            if (speedMs > 0f && dt in 0.0..10.0) {
                distanceM += prev.distanceTo(loc)
                movingSeconds += dt
            }
        }
        lastLocation = loc
        refreshStats()
    }

    private fun onSatellites(s: GnssStatus) {
        val total = s.satelliteCount
        var used = 0
        var cn0Sum = 0f
        val visibleBy = sortedMapOf<String, Int>()
        val usedBy = sortedMapOf<String, Int>()

        for (i in 0 until total) {
            val name = constellationName(s.getConstellationType(i))
            visibleBy[name] = (visibleBy[name] ?: 0) + 1
            if (s.usedInFix(i)) {
                used++
                cn0Sum += s.getCn0DbHz(i)
                usedBy[name] = (usedBy[name] ?: 0) + 1
            }
        }

        val avg = if (used > 0) cn0Sum / used else 0f
        cardUsed.set(used.toString())
        cardVisible.set(total.toString())
        cardSignal.set(if (used > 0) String.format(Locale.US, "%.0f", avg) else "--")

        // map typical 15..50 dB-Hz range to 0..100 %
        val pct = ((avg - 15f) / 35f * 100f).coerceIn(0f, 100f).toInt()
        val (quality, color) = when {
            used == 0 -> "No signal" to "#5C6684"
            pct >= 70 -> "Excellent" to "#7CFF6B"
            pct >= 45 -> "Good" to "#00E5FF"
            pct >= 25 -> "Fair" to "#FFC107"
            else -> "Weak" to "#FF3D71"
        }
        signalBar.progress = pct
        signalBar.progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
        signalLabel.text = "Signal strength: $quality ($pct%)"

        constellations.text = if (total == 0) "Waiting for GPS…" else
            visibleBy.entries.joinToString("\n") { (name, n) ->
                "$name — ${usedBy[name] ?: 0} in use / $n visible"
            }
    }

    private fun constellationName(type: Int) = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS (USA)"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS (Russia)"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo (EU)"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou (China)"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS (Japan)"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC (India)"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        else -> "Other"
    }

    private fun setUnit(mph: Boolean) {
        useMph = mph
        if (mph) speedometer.setScale(160, 20, "mph") else speedometer.setScale(240, 20, "km/h")
        for ((chip, selected) in listOf(unitKmh to !mph, unitMph to mph)) {
            chip.isSelected = selected
            chip.setTextColor(if (selected) Color.BLACK else Color.WHITE)
        }
        refreshStats()
    }

    private fun resetTrip() {
        maxMs = 0f; distanceM = 0.0; movingSeconds = 0.0; lastLocation = null
        refreshStats()
        status.text = "Trip stats reset"
    }

    private fun conv(ms: Float) = ms * if (useMph) 2.236936f else 3.6f

    private fun refreshStats() {
        speedometer.setSpeed(conv(speedMs))
        cardMax.set(String.format(Locale.US, "%.0f", conv(maxMs)))
        val avgMs = if (movingSeconds > 0) (distanceM / movingSeconds).toFloat() else 0f
        cardAvg.set(String.format(Locale.US, "%.0f", conv(avgMs)))
        val dist = if (useMph) distanceM / 1609.344 else distanceM / 1000.0
        cardDist.set(String.format(Locale.US, "%.2f %s", dist, if (useMph) "mi" else "km"))
    }
}
