# GPSView

GPSView ist eine **private Android-App** (nur zum Sideloading, kein Play Store), die live Details zum aktuellen Standort anzeigt:

- Koordinaten in mehreren Systemen: Dezimalgrad (lat/lon), UTMREF/MGRS (wie bei bayerischen Feuerwehren genutzt) und Plus Codes
- GNSS-Metadaten: Genauigkeit, Anzahl sichtbarer/genutzter Satelliten, ellipsoidische **und** Meereshöhe (MSL)
- Position auf einer Open-Source-Karte, optional mit Satellitenbildern und einer Overlay-Ebene der bayerischen Flurstücksgrenzen (ALKIS)

Die App ist bewusst **sparsam im Akkuverbrauch** und **strikt vordergrundbasiert**: Standortabfragen laufen nur, solange die App sichtbar ist, und stoppen sofort, sobald sie in den Hintergrund wechselt. Es gibt keinen Hintergrunddienst und keine Standortverfolgung.

## Technik

- Kotlin + Jetpack Compose
- min SDK 34 (Android 14+)
- Kartenrendering über MapLibre
- Paket: `de.eckstein.gpsview`

Details zu Architektur und Entscheidungen: [SPEC.md](SPEC.md)

---

## Für Entwickler: Build & Installation

### Voraussetzungen

- JDK 17
- Android SDK (per Android Studio oder Kommandozeilen-Tools)
- Ein Android-Gerät mit aktiviertem USB-Debugging **oder** ein Emulator

Das Projekt bringt den Gradle-Wrapper (`gradlew`) mit, eine lokale Gradle-Installation ist nicht nötig.

### APK bauen

Debug-APK (für Sideloading am schnellsten, automatisch mit Debug-Key signiert):

```bash
./gradlew assembleDebug
```

Die fertige APK liegt danach unter:

```
app/build/outputs/apk/debug/app-debug.apk
```

Release-APK (unsigniert, sofern kein eigener `signingConfig` in `app/build.gradle.kts` hinterlegt ist — muss vor der Installation noch signiert werden):

```bash
./gradlew assembleRelease
```

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

Für den privaten Gebrauch reicht in der Regel die Debug-APK.

### Installation auf einem Gerät

**Option A: über ADB (Gerät per USB verbunden, USB-Debugging aktiviert)**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Option B: APK manuell übertragen**

1. `app-debug.apk` auf das Gerät kopieren (z. B. per USB, Cloud-Speicher oder E-Mail an sich selbst).
2. Auf dem Gerät die Datei öffnen.
3. Falls das Gerät die Installation aus unbekannten Quellen blockiert: **Einstellungen → Sicherheit / Apps → Installation aus unbekannten Quellen** für die jeweilige App (z. B. Dateimanager oder Browser) erlauben.
4. Installation bestätigen.

Nach der Installation fragt die App beim ersten Start nach den Standortberechtigungen (fein und grob) — diese müssen erteilt werden, damit GPSView funktioniert.

### Direkt auf ein verbundenes Gerät installieren und starten

```bash
./gradlew installDebug
```

Dies baut die Debug-APK und installiert sie direkt über ADB auf dem angeschlossenen Gerät/Emulator.
