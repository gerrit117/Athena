# Kameras, Klingel und Türöffner

## Kameras auswählen

In der WebUI **Kameras & Klingel → Kameras in der Übersicht** nur die gewünschten Kameras auswählen und speichern. Am Echo ist dieselbe Auswahl unter **Kameras → Kameras auswählen** erreichbar. Die Auswahl verwendet Namen und Entitäts-IDs, ohne Bilder zu laden.

Home Assistant kann beispielsweise auch Staubsaugerkarten als `camera.*` bereitstellen. Diese Bildentitäten müssen nicht in Athenas Kameraübersicht erscheinen. Eine leere Auswahl zeigt keine Kameras in der Übersicht.

## Beim Klingeln automatisch anzeigen

Unter **Kameras & Klingel** die **Klingelkamera**, den **Klingel-Auslöser**, Anzeigedauer und Ton wählen. Die Klingelkamera wird unabhängig von der Kameraübersicht eingestellt. Bei einem neuen Ereignis öffnet sie sich automatisch, auch aus der Nachtansicht. Danach kehrt Athena zur vorherigen Ansicht zurück.

## Optionaler Türöffner

In der WebUI **Kameras & Klingel → Türöffner · optional** eine passende HA-Entität auswählen und speichern. Am Echo ist die Auswahl auch in **Einstellungen → Türöffner · optional** möglich. Ohne Auswahl ist die Funktion ausgeschaltet.

Die Schaltfläche **Tür öffnen** erscheint ausschließlich im automatisch geöffneten Klingelbild. Sie sendet die konfigurierte Aktion an HA. Bei fehlender Verbindung oder nicht verfügbarer Entität ist sie deaktiviert. Eine Rückmeldung bestätigt nur das Absenden des Befehls, nicht die tatsächliche Stellung der Tür.

| Entität | Verwendete HA-Aktion |
|---|---|
| Schloss `lock.*` | `lock.unlock` zum Entriegeln oder ausdrücklich `lock.open` zum Öffnen der Falle |
| Taster `button.*` / `input_button.*` | `press` |
| Skript `script.*` | `turn_on` |
| Schalter `switch.*` / `input_boolean.*` | `turn_on` |
| Tor/Rollladen `cover.*` | `open_cover` |

Das Öffnen der Falle muss vom Schloss unterstützt werden. Bei einem Relais den benötigten Öffnungsimpuls direkt am Aktor oder in HA einrichten. Athena schaltet einen Schalter ein und übernimmt nicht dessen zeitgesteuertes Ausschalten. Ein HA-Skript kann mehrere nötige Schritte zusammenfassen.
