# GUI
Orientierung an Tutorium 1
Toggle für alle Sensoren
Toggle für jeweilige Datentypen -> für Position drei Radio Boxen

Textfeld und/oder Graphen
Map?

Frage: Alles untereinander oder Bereiche? Zum Beispiel eine Kachel 'Settings' für die Toggles.

# Sensoren
Orientierung an Berechtigungen aus Tutorium 1, Tutorium 2 und 3, https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview?hl=de
```xml
    <!--Berechtigungen in AndroidManifest.xml-->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
```
Sensorendaten: Accelerometer, Gyroskop, Magnetometer, Position, evtl. virtuelle Sensoren (Lineare Beschleunigung und Gravitation -> "Sensoren" mit von echten Sensoren weiterverarbeiteten Daten, etwa Accelerometer für Lineare Beschleunigung)
Für jeden Datentyp eine Checkbox (Position darunter 3 Radio Boxes für GPS, Netzwerkpositionierung und Fused)
Für Debugging Textausgabe

Accelerometer: x, y, z in m/s^2
- TYPE_ACCELEROMETER
Gyroskop: x, y, z in rad/s
- TYPE_GYROSCOPE
Magnetometer: x, y, z in μT
- TYPE_MAGNETIC_FIELD

Position: longitude, latitude in ° (nicht geprüft)
- 

# Datenspeicherung
Orientierung an Berechtigungen aus Tutorium 1
- android.permission.INTERNET
- android.permission.READ_EXTERNAL_STORAGE
- android.permission.WRITE_EXTERNAL_STORAGE

# Anzeige von gesammelten Daten
Orientierung an Tutorium 3 und 4
Seite 13 in Tutorium 2 zur Verarbeitung (Zusammenfassung/Transformation) von Sensordaten (bspw. Bildung des Mittelwerts für x über einen Zeitraum)
Textform oder Graphen für Beschleunigung, Gyroskop, Magnetometer und virtuelle Sensoren
OpenStreetMaps/Google Maps für Positionsdaten? (oder nur 'Longitude' und 'Latitude' als Text)