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

## Offline-Pakete

In den Optionen (Zahnrad oben rechts) lassen sich Straßendaten je Bundesland oder für ganz
Deutschland herunterladen (System-Downloadmanager, optional nur über WLAN). Mit Paket kommen
Kacheln ohne Netz; an Paketgrenzen wird online ergänzt, ohne Paket wie bisher Overpass.

Pakete bauen (Python mit `pip install osmium shapely`):

```
python tools/build_all.py D:/build/waserlaubt-data            # alle 16 Bundesländer
python tools/build_all.py D:/build/waserlaubt-data bremen     # einzelne
```

`out/` enthält danach `<bundesland>.wepk` und `index.json` zum Hochladen. Die App lädt von
`PACKAGE_BASE_URL` (siehe `app/build.gradle.kts`): GitHub-Releases von
[was-erlaubt-daten](https://github.com/strYchni0x/was-erlaubt-daten), dort monatlich per Action gebaut.
Lokal statt GitHub: URL auf `http://127.0.0.1:8765/` setzen (nur Debug erlaubt Klartext),
zum Testen: `python -m http.server 8765 --directory out` und `adb reverse tcp:8765 tcp:8765`.
Größe: Niedersachsen 21,8 MB, Deutschland etwa 200 MB.

## Bedienung

- **Lange drücken** auf den Bildschirm: Debug-Infos (Way-ID, Richtung, Abstand, Tags)
- **Bild-im-Bild**: Beim Wechsel in eine andere App (z. B. Navigation) läuft die Anzeige als kleines
  Fenster weiter (abschaltbar in den Optionen)
- Ausgegrautes Schild = geschätzt (kein Tag in OSM)

## Build

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat testDebugUnitTest assembleDebug
```

Build-Ausgabe liegt unter `D:/build/waserlaubt/` (siehe `settings.gradle.kts`).

## Nächste Schritte

- Testfahrten mit Debug-Overlay, Matching-Parameter justieren
- Tools liegen doppelt (hier und in was-erlaubt-daten) – Paketformat bei Änderungen in beiden anpassen

## Lizenz

Copyright © 2026 Florian Willnat

Dieses Programm ist freie Software: Sie können es unter den Bedingungen der
[GNU General Public License](LICENSE), Version 3 oder (nach Ihrer Wahl) jeder späteren
Version, weitergeben und/oder ändern. Es wird ohne jede Gewährleistung bereitgestellt.

Straßendaten © OpenStreetMap-Mitwirkende, [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
(gilt auch für die Testdaten in `app/src/test/resources`).
