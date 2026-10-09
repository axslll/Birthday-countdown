package com.example.speedometer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private class Cell(root: View, label: String) {
        private val value: TextView = root.findViewById(R.id.value)
        private val caption: TextView = root.findViewById(R.id.label)
        init { caption.text = label }
        fun set(text: String) { value.text = text }
        fun setLabel(text: String) { caption.text = text }
    }

    private lateinit var speedometer: SpeedometerView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var powerButton: TextView
    private lateinit var unitKmh: TextView
    private lateinit var unitMph: TextView
    private lateinit var signalMeter: MeterView
    private lateinit var signalQuality: TextView
    private lateinit var constellations: LinearLayout
    private lateinit var cellMax: Cell
    private lateinit var cellAvg: Cell
    private lateinit var cellDist: Cell
    private lateinit var cellUsed: Cell
    private lateinit var cellView: Cell
    private lateinit var cellCn0: Cell

    private val listener: (TrackerState) -> Unit = { render(it) }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission()) TrackerService.start(this) else statusText.text = "Location permission needed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Tracker.init(this)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        speedometer = findViewById(R.id.speedometer)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        powerButton = findViewById(R.id.powerButton)
        unitKmh = findViewById(R.id.unitKmh)
        unitMph = findViewById(R.id.unitMph)
        signalMeter = findViewById(R.id.signalMeter)
        signalQuality = findViewById(R.id.signalQuality)
        constellations = findViewById(R.id.constellations)

        cellMax = Cell(findViewById(R.id.cellMax), "Top")
        cellAvg = Cell(findViewById(R.id.cellAvg), "Average")
        cellDist = Cell(findViewById(R.id.cellDist), "Distance")
        cellUsed = Cell(findViewById(R.id.cellUsed), "In use")
        cellView = Cell(findViewById(R.id.cellView), "In view")
        cellCn0 = Cell(findViewById(R.id.cellCn0), "dB-Hz")

        unitKmh.setOnClickListener { Tracker.setMph(this, false) }
        unitMph.setOnClickListener { Tracker.setMph(this, true) }
        powerButton.setOnClickListener { togglePower() }
        findViewById<View>(R.id.resetTrip).setOnClickListener { Tracker.resetTrip() }
    }

    override fun onStart() {
        super.onStart()
        Tracker.addListener(listener)
    }

    override fun onStop() {
        super.onStop()
        Tracker.removeListener(listener)
    }

    private fun togglePower() {
        when {
            Tracker.state.running -> TrackerService.stop(this)
            hasLocationPermission() -> TrackerService.start(this)
            else -> {
                val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
                permissions.launch(wanted.toTypedArray())
            }
        }
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun render(s: TrackerState) {
        val mph = Tracker.useMph
        if (mph) speedometer.setScale(160, 20, 5, "mph") else speedometer.setScale(240, 40, 10, "km/h")
        speedometer.setSpeed(Tracker.toUnit(s.speedMs))
        speedometer.setMarker(Tracker.toUnit(s.maxMs))

        for ((chip, selected) in listOf(unitKmh to !mph, unitMph to mph)) {
            chip.setBackgroundResource(if (selected) R.drawable.segment_selected else 0)
            chip.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.bg else R.color.text_secondary)
            )
        }

        powerButton.text = if (s.running) "Stop tracking" else "Start tracking"
        powerButton.setBackgroundResource(if (s.running) R.drawable.pill_surface else R.drawable.pill_accent)
        powerButton.setTextColor(ContextCompat.getColor(this, if (s.running) R.color.text_primary else R.color.bg))

        val locked = s.running && s.gpsOn && s.used > 0
        statusDot.background.setTint(
            ContextCompat.getColor(this, if (locked) R.color.accent else R.color.text_secondary)
        )
        statusText.text = when {
            !s.running -> "Tracking off"
            !s.gpsOn -> "GPS is off"
            s.used == 0 -> "Searching for satellites"
            else -> "GPS locked"
        }

        cellMax.set(String.format(Locale.US, "%.0f", Tracker.toUnit(s.maxMs)))
        cellAvg.set(String.format(Locale.US, "%.0f", Tracker.toUnit(s.avgMs)))
        val dist = if (mph) s.distanceM / 1609.344 else s.distanceM / 1000.0
        cellDist.set(String.format(Locale.US, "%.2f", dist))
        cellDist.setLabel(if (mph) "Miles" else "Kilometres")

        cellUsed.set(if (s.running) s.used.toString() else "–")
        cellView.set(if (s.running) s.visible.toString() else "–")
        cellCn0.set(if (s.used > 0) String.format(Locale.US, "%.0f", s.avgCn0) else "–")

        signalMeter.setFraction(if (s.used > 0) Tracker.signalFraction(s.avgCn0) else 0f)
        signalQuality.text = if (s.running) Tracker.signalQuality(s) else "Off"

        renderConstellations(s.constellations)
    }

    private fun renderConstellations(list: List<ConstellationStat>) {
        // reuse rows to avoid rebuilding the list on every GNSS update
        while (constellations.childCount > list.size) constellations.removeViewAt(constellations.childCount - 1)
        while (constellations.childCount < list.size) {
            LayoutInflater.from(this).inflate(R.layout.row_constellation, constellations, true)
        }
        list.forEachIndexed { i, c ->
            val row = constellations.getChildAt(i)
            row.findViewById<TextView>(R.id.name).text = c.system.name
            row.findViewById<TextView>(R.id.country).text = c.system.country
            row.findViewById<TextView>(R.id.fullName).text = c.system.fullName
            row.findViewById<ImageView>(R.id.flag).apply {
                val flag = c.system.flag
                if (flag != null) { setImageResource(flag); visibility = View.VISIBLE } else visibility = View.GONE
            }
            row.findViewById<TextView>(R.id.count).text = "${c.used} / ${c.visible}"
            row.findViewById<MeterView>(R.id.meter).setFraction(
                if (c.visible > 0) c.used.toFloat() / c.visible else 0f
            )
        }
    }
}
