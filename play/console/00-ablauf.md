# Play Console – Ablauf für „was erlaubt“

Reihenfolge, in der die Play Console die Angaben verlangt. Jede Datei entspricht einem Bereich.

| # | Bereich in der Play Console | Datei | erledigt |
|---|---|---|---|
| 1 | App erstellen | [01-app-erstellen.md](01-app-erstellen.md) | ☐ |
| 2 | Richtlinien → App-Inhalte (ohne Datensicherheit) | [02-app-inhalte.md](02-app-inhalte.md) | ☐ |
| 3 | Richtlinien → App-Inhalte → Datensicherheit | [03-datensicherheit.md](03-datensicherheit.md) | ☐ |
| 4 | Wachstum → Store-Präsenz → Haupteintrag im Store | [04-store-eintrag.md](04-store-eintrag.md) | ☐ |
| 5 | Wachstum → Store-Präsenz → Store-Einstellungen | [05-store-einstellungen.md](05-store-einstellungen.md) | ☐ |
| 6 | Testen und veröffentlichen → Geschlossener Test | [06-geschlossener-test.md](06-geschlossener-test.md) | ☐ |
| 7 | Nach 14 Tagen: Zugriff auf Produktion beantragen | [07-produktion.md](07-produktion.md) | ☐ |

## Dateien

| Was | Pfad |
|---|---|
| App-Bundle (AAB) 1.0.0 | `D:\build\waserlaubt\app\outputs\bundle\release\app-release.aab` |
| Store-Icon 512 × 512 | `play/graphics/icon-512.png` |
| Titelgrafik 1024 × 500 | `play/graphics/feature-graphic-1024x500.png` |
| Screenshots (1260 × 2520) | `play/screenshots/` |
| Datenschutzerklärung | https://strychni0x.github.io/was-erlaubt-app/datenschutz.html |

## Daten der App

| | |
|---|---|
| Paketname | `de.waserlaubt.app` (nicht mehr änderbar) |
| Version | 1.0.0 (versionCode 1) |
| Ziel-SDK | 36 (Android 16) · min. SDK 26 (Android 8.0) |
| Berechtigungen | Standort genau/ungefähr (nur Vordergrund), Internet |
| Upload-Schlüssel | `waserlaubt-upload.jks` + `keystore.properties` im Projektordner – **gesichert?** |
