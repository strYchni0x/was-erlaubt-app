# 6. Geschlossener Test

Pflicht für neue private Entwicklerkonten: **mindestens 12 Tester, 14 Tage lang**, bevor der Zugriff
auf die Produktion beantragt werden kann.

Play Console → **Testen und veröffentlichen → Testen → Geschlossener Test**

## Track einrichten

| Schritt | Eingabe |
|---|---|
| Track | Den von Google angelegten Track „Geschlossener Test – Alpha“ verwenden |
| Länder/Regionen | **Deutschland** |
| Tester | E-Mail-Liste **„Fulltxt closed test“** wiederverwenden (15 Personen) oder neue Liste anlegen |
| Feedback-URL / E-Mail | `florian@willnat.org` |

## Release erstellen

1. **Neuen Release erstellen**
2. Bei der ersten Veröffentlichung: **Play App-Signatur** bestätigen („Von Google generierten Schlüssel
   verwenden“). Der eigene Upload-Schlüssel bleibt der Schlüssel zum Hochladen.
3. App-Bundle hochladen: `D:\build\waserlaubt\app\outputs\bundle\release\app-release.aab`
4. Release-Name: `1.0.0 (1)` (wird automatisch vorgeschlagen)
5. Versionshinweise:

```
<de-DE>
Erste Version: großes Tempo-Schild passend zur Fahrtrichtung, Offline-Karten je Bundesland, kleines Fenster über der Navigation.
</de-DE>
<en-US>
First release: large speed limit sign for your direction of travel, offline maps per German state, picture-in-picture window over your navigation app.
</en-US>
```

6. **Weiter → Speichern → Zur Überprüfung senden**

## Nach der Freigabe

- Opt-in-Link für Tester (unter „Tester“ im Track): an die Testerliste schicken. Tester müssen
  **mit dem Link teilnehmen und die App installieren** – nur dann zählen sie.
- Vorher die selbst installierte Version vom Handy deinstallieren (anderer Signaturschlüssel als die
  von Google signierte Fassung).
- Die 14 Tage beginnen, sobald mindestens 12 Tester dabei sind.
- Während des Tests Rückmeldungen sammeln – sie werden für den Antrag in
  [07-produktion.md](07-produktion.md) gebraucht.

## Updates während des Tests

versionCode in `app/build.gradle.kts` erhöhen (1 → 2 …), `gradlew clean bundleRelease`, die
optimierte Fassung vorher auf dem Handy testen, dann im selben Track einen neuen Release anlegen.
