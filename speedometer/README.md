# Speedometer (Android)

GPS speedometer with a live satellite panel.

- Animated gauge, km/h or mph toggle
- Max speed, average moving speed, trip distance (long-press **MAX** to reset)
- Satellites **in use** vs **available**, average signal (C/N0 dB-Hz) with a quality bar
- Per-constellation breakdown (GPS, GLONASS, Galileo, BeiDou, …)

## Build
Open the `speedometer/` folder in Android Studio (Hedgehog or newer) and press Run,
or with the Android SDK installed: `gradle :app:assembleDebug`.
Requires a real device with GPS (emulators give no satellite data). Min SDK 24.
