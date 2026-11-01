# Von VACAV / View Assist auf direkte ESPHome-Sprache wechseln

[English](migration.en.md)

Nur für eine bereits vorhandene Einrichtung. Eine neue Athena-Installation benötigt diese Komponenten nicht.

## Reihenfolge

1. Sichere die vorhandene HA-Konfiguration. Notiere Echo-IP, gewählte Sprachpipeline und Aktivierungswort.
2. Lies den vorhandenen echo-dot-assist-Verschlüsselungsschlüssel lokal über Root-ADB aus `/data/local/hassmic/state/api_key`. Nicht im Chat, Repository oder in einem Issue veröffentlichen.
3. Deaktiviere in HA den **VACAV-Eintrag genau dieses Echo**. Noch nicht löschen. Damit wird dessen Sprachverbindung geschlossen.
4. Entferne gegebenenfalls den ESPHome-Ignore-Eintrag **„managed by Vision“ dieses Echo**. Andere ESPHome-Geräte und Ignore-Einträge beibehalten.
5. Füge das Echo über die normale ESPHome-Integration hinzu: Echo-IP, Port **26053**, vorhandener Schlüssel.
6. Wähle die vorherige Assist-Pipeline und das Aktivierungswort am neuen ESPHome-Gerät aus.
7. Teste Sprachbefehl, Antwort, Stummschaltung und Athena-Anzeige. Prüfe ebenfalls den Start nach einem Neustart.
8. Erst anschließend die alten VACAV-/View-Assist-Einträge und die ausschließlich dafür angelegten Dashboards entfernen. Automationen, Skripte oder externe Verweise auf alte Entitäts-IDs vorher anpassen.

Ein zweiter aktiver Sprachbesitzer ist nicht vorgesehen. Nicht gleichzeitig VACAV und die ESPHome-Integration mit demselben Echo betreiben. Athena liest den Sprachzustand lokal; sie öffnet keinen weiteren Audio-Subscriber.

## Music Assistant

Athena und direkte ESPHome-Sprache benötigen Music Assistant nicht. Die bisherige VACAV-Upstream-Konfiguration prüft Music Assistant dagegen beim Start. Deshalb nicht zuerst Music Assistant löschen und erst danach die Sprache umstellen.

Music Assistant nur entfernen, wenn keine anderen Geräte, Musikquellen oder Automationen damit betrieben werden sollen. Die Deinstallation betrifft dessen Einrichtung insgesamt, nicht nur den Echo.

## Rücknahme

Bei Problemen zuerst den neuen ESPHome-Eintrag deaktivieren, dann den alten VACAV-Eintrag wieder aktivieren. Den vorhandenen Verschlüsselungsschlüssel beibehalten. Nicht beide gleichzeitig aktivieren.

## Stand der praktischen Prüfung

Der Wechsel auf direkte ESPHome-Sprache wurde am Testgerät durchgeführt. HA lädt den Satelliten, Aktivierungswort und Pipeline-Auswahl. Bei der anschließenden Sprachabfrage trat ein bereits bekanntes HTTP-400-Problem in der HA-Pipeline auf. Eine vollständig erfolgreiche Sprachantwort nach der Umstellung ist deshalb noch nicht bestätigt. Das ist ausdrücklich kein abgeschlossener Ende-zu-Ende-Test.
