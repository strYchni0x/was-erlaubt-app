# 3. Datensicherheit

Play Console → **App-Inhalte → Datensicherheit**

## Hintergrund (nicht eintragen, nur zur Begründung)

- Google wertet **jede Übertragung vom Gerät** als „Erhebung“, auch wenn der Entwickler nichts erhält.
- Der **genaue Standort** bleibt auf dem Gerät → wird **nicht** angegeben.
- Ohne Offline-Paket fragt die App Straßen für eine Kachel von ca. **2 × 2 km** bei der Overpass-API an
  (Server in DE/AT). Die Kachelgrenzen entsprechen dem **ungefähren Standort** → wird angegeben.
- Offline-Pakete kommen von GitHub, dabei wird kein Standort übertragen.
- Keine Analyse-, Absturz- oder Werbe-SDKs, keine Google-Dienste in der App.
- Die IP-Adresse zählt laut Google nicht als eigener Datentyp.

## Schritt 1 – Datenerhebung und Sicherheit

| Frage | Antwort |
|---|---|
| Erhebt oder teilt Ihre App die erforderlichen Nutzerdatentypen? | **Ja** |
| Sind alle von Ihrer App erhobenen Nutzerdaten bei der Übertragung verschlüsselt? | **Ja** (ausschließlich HTTPS) |
| Bieten Sie Nutzern die Möglichkeit, das Löschen ihrer Daten anzufordern? | **Nein** – es wird nichts gespeichert, das gelöscht werden könnte |

Falls zusätzlich gefragt: Konto-Erstellung → **Die App erlaubt keine Kontoerstellung**.

## Schritt 2 – Datentypen

Nur ankreuzen: **Standort → Ungefährer Standort**

Alles andere **nicht** ankreuzen (Genauer Standort, Personenbezogene Daten, Finanzdaten, Gesundheit,
Nachrichten, Fotos/Videos, Audio, Dateien, Kalender, Kontakte, App-Aktivität, Web-Browsing,
App-Informationen und -Leistung, Geräte- oder andere IDs).

## Schritt 3 – Ungefährer Standort: Nutzung und Verarbeitung

| Frage | Antwort |
|---|---|
| Werden diese Daten erhoben, geteilt oder beides? | **Erhoben** (nicht geteilt) |
| Werden diese Daten flüchtig verarbeitet? | **Ja** |
| Ist die Erhebung erforderlich oder optional? | **Datenerhebung ist erforderlich** |
| Zwecke der Erhebung | ☑ **App-Funktionen** – sonst nichts |

Begründung „nicht geteilt“: Die Overpass-Server beantworten nur die Anfrage nach Straßendaten für die
App (Dienstleister-Ausnahme in Googles Definition); sie bekommen die Daten nicht zur eigenen Verwendung.

Begründung „erforderlich“: Ohne installiertes Offline-Paket kann die App ohne diese Anfrage nicht
arbeiten. (Alternativ vertretbar: „optional“, weil man mit Offline-Paket ohne Übertragung auskommt –
„erforderlich“ ist die vorsichtigere Angabe.)

## Schritt 4 – Vorschau

Im Store sollte danach stehen:
- **Keine Daten mit Dritten geteilt**
- **Erhobene Daten:** Standort (ungefährer Standort) – App-Funktionen
- **Daten werden bei der Übertragung verschlüsselt**
- **Du kannst das Löschen deiner Daten nicht anfordern** (weil keine gespeichert werden)
