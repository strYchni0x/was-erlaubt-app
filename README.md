# was erlaubt

Android-App, die groß das aktuell erlaubte Tempolimit anzeigt – passend zur Fahrtrichtung.
Datenquelle: OpenStreetMap (Overpass-API). Kein Konto, kein Tracking, keine Werbung.

## Funktionsweise

1. GPS-Position + Kurs (1 Hz, `LocationManager`, ohne Google-Dienste)
2. Straßendaten in Kacheln (~2,2 × 2 km) von Overpass, 30 Tage Cache in `cacheDir/tiles`;
   Nachbarkacheln in Fahrtrichtung werden vorgeladen
3. Map-Matching (`matching/RoadMatcher`): Abstand + Richtungsabweichung, Einbahnstraßen,
   Hysterese (Wechsel erst nach 2 bestätigten Positionen)
4. Tempolimit (`osm/MaxspeedResolver`): `maxspeed:conditional` → `maxspeed:forward/backward`
   → `maxspeed` → `maxspeed:type`/`zone:maxspeed` (DE/AT/CH/NL/FR) → Schätzung aus Straßentyp
   (innerorts 50 / außerorts 100 / Kraftfahrstraße mit getrennten Fahrbahnen ohne Limit;
   „innerorts“ = mind. 3 Wohnstraßen im Umkreis von 150 m)

## Bedienung

- **Lange drücken** auf den Bildschirm: Debug-Infos (Way-ID, Richtung, Abstand, Tags)
- Ausgegrautes Schild = geschätzt (kein Tag in OSM)

## Build

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat testDebugUnitTest assembleDebug
```

Build-Ausgabe liegt unter `D:/build/waserlaubt/` (siehe `settings.gradle.kts`).

## Nächste Schritte

- Testfahrten mit Debug-Overlay, Matching-Parameter justieren
- Offline-Datenpaket aus Geofabrik-Extrakt statt Overpass
