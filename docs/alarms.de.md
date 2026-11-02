# Wecker mit Home Assistant

Athena speichert und plant die Wecker auf dem Echo. Home Assistant dient zur Bedienung, Sprachsteuerung und für Automationen. Ein bereits übertragener Wecker benötigt zum Klingeln weder Home Assistant noch WLAN. Das Echo muss eingeschaltet und mit Strom versorgt sein; der Ton muss lokal vorliegen.

## Einrichten

1. Die aktuelle Athena-APK installieren und Home Assistant in Athena verbinden.
2. Dieses Repository im HA-Marketplace als benutzerdefinierte **Integration** hinzufügen: `https://github.com/gerrit117/Athena`.
3. **HA Alarm Clock · Athena** installieren und HA neu starten. Die erweiterte Integration wird mitgeliefert; die unveränderte Integration von HA Alarm Clock allein genügt nicht.
4. In HA unter **Einstellungen → Geräte & Dienste → Integration hinzufügen** nach **HA Alarm Clock · Athena** suchen und hinzufügen.
5. In Athenas WebUI unter **Wecker** die Synchronisierung einschalten und den ESPHome-Sprachsatelliten dieses Echo auswählen. Speichern. Die vorhandene HA-Verbindung wird verwendet; kein zusätzlicher Token nötig. Der HA-Benutzer muss Administrator sein.
6. In HA erscheint ein Gerät mit Athenas Gerätenamen. Es enthält nächsten Wecker, Klingelstatus, Schlummern/Ausschalten und Synchronisierungsstatus. Vorhandene Wecker erhalten Schalter, Uhrzeit und nächsten Auslösezeitpunkt.

Ein Testwecker lässt sich am Echo, in der WebUI oder mit der HA-Aktion `ha_alarmclock.create_alarm` erstellen:

```yaml
action: ha_alarmclock.create_alarm
data:
  time: "07:30"
  label: Aufstehen
  repeat: [mon, tue, wed, thu, fri]
  volume: 65
```

Ohne `repeat` gilt der Wecker einmalig für die nächste passende Uhrzeit. Bei mehreren Echo-Geräten zusätzlich `device_id` angeben. Die HA-Aktion kann eine Antwort liefern: Nur `confirmed: true` bedeutet, dass der Echo den Wecker gespeichert und eingeplant hat.

## Sprache

Für HA **2026.11** stellt die Integration Wecker-Werkzeuge für die Assist-API bereit. Im Gesprächsagenten muss die HA-Gerätesteuerung erlaubt sein. Beispiele:

- „Stell einen Wecker für 5 Uhr.“
- „Weck mich werktags um 7 Uhr.“
- „Welche Wecker sind eingestellt?“
- „Lösche den Wecker Aufstehen.“
- „Wecker ausschalten.“ / „Schlummern.“

Die Erkennung hängt vom gewählten Gesprächsagenten ab. Die Integration arbeitet mit tatsächlichen Werkzeugaufrufen; sie liest keine gesprochenen Antworten mit. Bei mehreren Geräten wird der zugeordnete Sprachsatellit verwendet. Ist die Zuordnung unklar, muss ein Gerät gewählt werden.

Optional für den regelbasierten HA-Gesprächsagenten: die mitgelieferten Dateien aus `custom_components/ha_alarmclock/custom_sentences/de/` nach `<HA-Konfiguration>/custom_sentences/de/` kopieren und die Gesprächskonfiguration neu laden. Diese einfachen Sätze unterstützen das Anlegen mit Stunde/Minute. Freie Formulierungen, Verwalten und Wochentage verwenden die Assist-Werkzeuge des Gesprächsagenten.

## Synchronisierung und Ausfälle

- **Synchronisiert:** Der Echo hat den zuletzt angezeigten Stand gemeldet.
- **Bestätigung ausstehend:** Die Änderung ist in HA gespeichert, aber noch nicht auf dem Echo bestätigt. Ein neuer Wecker ist dadurch noch nicht abgesichert.
- **Gerät offline:** Der letzte bekannte Stand bleibt in HA sichtbar. Die auf dem Echo gespeicherten Wecker laufen weiter.
- **Fehlgeschlagen/abgelaufen:** Die Änderung konnte nicht übernommen werden. Eine neue Weckzeit wird nach ihrem geplanten Zeitpunkt nicht verspätet als Wecker für den Folgetag angelegt.

Änderungen in Athena werden beim Wiederverbinden nach HA übertragen. Ausstehende HA-Befehle werden in ihrer Reihenfolge angewendet. Bei Änderungen am selben Wecker erhält ein noch ausstehender HA-Änderungsbefehl nach dem Verbinden Vorrang. Ein lokal gelöschter Wecker wird durch einen Änderungsbefehl nicht wiederhergestellt. HA-Neustarts löschen die Warteschlange nicht. Bereits bestätigte Befehle werden nicht erneut ausgeführt.

Löschen in HA während einer Unterbrechung stoppt einen lokal noch gespeicherten Wecker erst nach der Übertragung. Bis dahin kann er klingeln. Fünf Minuten Schlummern und Ausschalten funktionieren direkt am Echo auch offline; Sprachbefehle benötigen eine erreichbare Sprachpipeline.

Die Weckerverwaltung basiert auf [HA Alarm Clock](https://github.com/TargetCrafter/HA-Alarmclock). Die abgeleitete HA-Integration steht unter GPL-3.0, siehe deren [Lizenz und Herkunft](../custom_components/ha_alarmclock/NOTICE.md).
