package com.example.speedometer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
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
        init { root.findViewById<TextView>(R.id.label).text = label }
        fun set(text: String) { value.text = text }
    }

    private lateinit var speedometer: SpeedometerView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
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
        findViewById<View>(R.id.resetTrip).setOnClickListener { Tracker.resetTrip() }
    }

    override fun onStart() {
        super.onStart()
        Tracker.addListener(listener)
        if (hasLocationPermission()) {
            TrackerService.start(this)
        } else {
            val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
            permissions.launch(wanted.toTypedArray())
        }
    }

    override fun onStop() {
        super.onStop()
        Tracker.removeListener(listener)
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

        val locked = s.gpsOn && s.used > 0
        statusDot.background.setTint(
            ContextCompat.getColor(this, if (locked) R.color.accent else R.color.text_secondary)
        )
        statusText.text = when {
            !s.gpsOn -> "GPS is off"
            s.used == 0 -> "Searching for satellites"
            else -> "GPS locked"
        }

        cellMax.set(String.format(Locale.US, "%.0f", Tracker.toUnit(s.maxMs)))
        cellAvg.set(String.format(Locale.US, "%.0f", Tracker.toUnit(s.avgMs)))
        val dist = if (mph) s.distanceM / 1609.344 else s.distanceM / 1000.0
        cellDist.set(String.format(Locale.US, "%.2f", dist))
        findViewById<TextView>(R.id.cellDist).findViewById<TextView>(R.id.label).text =
            if (mph) "Miles" else "Kilometres"

        cellUsed.set(s.used.toString())
        cellView.set(s.visible.toString())
        cellCn0.set(if (s.used > 0) String.format(Locale.US, "%.0f", s.avgCn0) else "–")

        signalMeter.setFraction(if (s.used > 0) Tracker.signalFraction(s.avgCn0) else 0f)
        signalQuality.text = Tracker.signalQuality(s)

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
            row.findViewById<TextView>(R.id.name).text = c.name
            row.findViewById<TextView>(R.id.count).text = "${c.used} / ${c.visible}"
            row.findViewById<MeterView>(R.id.meter).setFraction(
                if (c.visible > 0) c.used.toFloat() / c.visible else 0f
            )
        }
    }
}
