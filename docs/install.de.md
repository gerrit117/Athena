# Installation

[English](install.en.md) · [Root-Anleitung](root.de.md)

**Frühe Testversion. Unterstützt und am Gerät geprüft: Echo Show 8, 2. Generation (2021), Modell A8H3N2, Gerätename `athena`, Firmware `PS75741.7080N`. Andere Modelle und Firmwarestände sind nicht freigegeben.**

## Was benötigt wird

- Ein passender Echo mit seinem Netzteil. USB allein versorgt ihn nicht ausreichend.
- Ein Micro-USB-Datenkabel, kein reines Ladekabel.
- Ein Linux-PC mit Python 3, Git und Android Platform Tools (`adb`). Für die APK-Installation werden weder Android Studio noch Node.js benötigt.
- Home Assistant im selben lokalen Netz und eine eingerichtete Assist-Pipeline.
- Die Athena-APK aus der zugehörigen Veröffentlichung und der entpackte Athena-Quellcode.

Die vollständige Vorbereitung des Audiounterbaus ist bisher nur unter Linux beschrieben. Das Python-Installationsskript verwendet ausschließlich die Python-Standardbibliothek.

## 1. Ausgangspunkt wählen

| Zustand | Einstieg |
|---|---|
| Originales Fire OS, noch kein Root | Zuerst [Root-Anleitung](root.de.md), dann hier weiter |
| Root vorhanden, echo-dot-assist noch nicht installiert | Abschnitt 2 |
| Root und passendes echo-dot-assist bereits vorhanden | Abschnitt 3 |
| Frühere Einrichtung mit VACAV / View Assist | Erst [Sprachanschluss umstellen](migration.de.md), dann Abschnitt 3 |

Ein vorhandenes Root-Gerät wird für eine reine Athena-Installation nicht zurückgesetzt. Das Installationsskript flasht keine Firmware.

## 2. echo-dot-assist vorbereiten

Dieser Abschnitt wird nur benötigt, wenn der Audiounterbau noch fehlt. Er übernimmt Mikrofone, Aktivierungswort, Sprachübertragung und Antwortwiedergabe.

Im entpackten Athena-Verzeichnis:

```sh
bash scripts/prepare-eda.sh --fetch
```

Das lädt den geprüften [echo-dot-assist-Quellstand](https://github.com/Gamer92000/echo-dot-assist/tree/1c00727d332c3b0ee4d13b59f702c78839431694). Es startet noch keine Installation.

Lege diese Originaldateien unter `vendor/echo-dot-assist/firmware/athena/` ab:

- `update-kindle-athena-PS75741_user_7080_0036274153604.bin`
- `boot-root.zip`

Quellen und Prüfsummen stehen in der [Dateiliste](root.de.md#dateien). Der Audiounterbau benötigt Bestandteile dieser Firmware auch dann, wenn das Gerät bereits gerootet ist. Diese Dateien liegen nicht im Athena-Repository.

```sh
bash scripts/prepare-eda.sh --install
```

Das Skript kontrolliert Modell, Root und Firmware. Danach startet der ursprüngliche Einrichtungsassistent von echo-dot-assist. Wähle **„Keep it as it is: skip the unlock, firmware and root steps“**. Nicht „Start from scratch“ wählen. Folge anschließend den Schritten für Werkzeuge, Build, WLAN und Installation. Der Athena-Helfer wählt den lokalen Quellbuild, gegen den die Status-Erweiterung geprüft wurde. Dafür werden zusätzlich Compiler, Make, Android NDK, Brotli und e2fsprogs benötigt; mehrere GB freien Speicher bereithalten. Der Assistent lädt fehlende Werkzeuge nach.

Das Originalprogramm verändert Systemdienste, deaktiviert die Amazon-Sprachsoftware und richtet seine Netzwerkregeln ein. Nach dem Neustart prüfe `adb shell id -u`: Die Ausgabe muss `0` sein.

## 3. Athena installieren

Netzteil und USB bleiben angeschlossen. Prüfe zuerst:

```sh
adb devices
python3 scripts/install.py --apk /pfad/zur/Athena.apk --check
```

Wenn mehrere Android-Geräte angeschlossen sind, ergänze `--serial DEINE-SERIENNUMMER` bei jedem Aufruf. Der Installer wählt in diesem Fall kein Gerät automatisch aus.

```sh
python3 scripts/install.py --apk /pfad/zur/Athena.apk
```

Das Skript installiert die App und die mitgelieferte Status-Erweiterung für den geprüften Audiounterbau. Das Original bleibt unter `/system/hassmic/hassmic` erhalten. Die Erweiterung liegt unter `/data/local/hassmic/hassmic`. Firmware und Nutzerdaten werden nicht gelöscht.

Ein unbekannter Audiodienst oder ein fremder Override wird nicht überschrieben. Für eine reine App-Installation gibt es `--app-only`. Ohne passende Status-Erweiterung funktioniert die automatische Sprachanimation nicht. Bei einer selbst gebauten oder anderen echo-dot-assist-Version muss die Erweiterung für diesen Stand geprüft und gebaut werden; die Prüfsummenprüfung nicht einfach entfernen.

## 4. Sprache direkt mit Home Assistant verbinden

1. Öffne in HA **Einstellungen → Geräte & Dienste → Integration hinzufügen → ESPHome**.
2. Gib die IP des Echo und Port **26053** ein. Der übliche ESPHome-Port 6053 ist hier falsch.
3. Übernimm den vom Einrichtungsdialog vorgesehenen Verschlüsselungsschritt. Bei einer vorhandenen Installation wird der bereits verwendete Schlüssel benötigt; nicht zurücksetzen oder neu erzeugen.
4. Öffne das angelegte Echo-Gerät. Wähle beim Assistenten deine vorhandene Pipeline und beim Aktivierungswort beispielsweise **Alexa**.
5. Sage einen kurzen Testbefehl und prüfe Antwort und Anzeige.

Falls ein bereits gesetzter Schlüssel benötigt wird, liegt er bei diesem Unterbau unter `/data/local/hassmic/state/api_key`. Er lässt sich über Root-ADB lokal ablesen. Nur in den HA-Einrichtungsdialog übernehmen; nicht in Screenshots, Issues oder das Repository kopieren.

Die ESPHome-Integration ist Bestandteil von HA. Ein ESPHome-Builder beziehungsweise eine ESPHome-App zum Kompilieren von Mikrocontroller-Firmware ist dafür nicht nötig. VACAV, View Assist und Music Assistant werden für diesen Weg nicht benötigt. Musikdienste sind ein separater, optionaler Ausbau.

## 5. Anzeige und WebUI einrichten

1. Öffne Athena am Echo und gehe in die Einstellungen.
2. Trage HA-Adresse und einen langlebigen Zugriffstoken ein. Den Token erstellst du im HA-Benutzerprofil. Er wird auf dem Echo verschlüsselt gespeichert.
3. Öffne im Browser `http://ECHO-IP:26804`.
4. Erzeuge am Echo einen vierstelligen Einmalcode und melde den Browser damit an.
5. Stelle Favoriten, Räume, Kategorien, Kamera, Klingelauslöser und Klingelton ein. Aktiviere bei Bedarf Autostart.

Athena benötigt kein Lovelace-Dashboard. Die WebUI läuft auf dem Echo. Der Einmalcode gilt fünf Minuten und wird bei erfolgreicher Anmeldung verbraucht. Verwaltungszugriff nur im eigenen lokalen Netz bereitstellen.

## Aktualisieren

Verwende dieselbe APK-Signatur wie bei der ersten Installation:

```sh
python3 scripts/install.py --apk /pfad/zur/neuen/Athena.apk
```

Eine andere Signatur wird von Android abgelehnt. Nicht deshalb die App mit ihren gespeicherten Einstellungen deinstallieren. Die bisherige Test-APK ist mit einem lokalen Debug-Schlüssel signiert; ein Wechsel auf einen späteren Veröffentlichungsschlüssel benötigt eine gesonderte Anleitung.

## Rücknahme

Nur die Status-Erweiterung entfernen und den ursprünglichen Audiodienst starten:

```sh
adb shell 'stop hassmic; rm /data/local/hassmic/hassmic; start hassmic'
```

Athena selbst lässt sich über Android deinstallieren. Dabei werden ihre lokalen Einstellungen gelöscht. Das entfernt weder Root noch echo-dot-assist. Für dessen vollständige Rücknahme gelten die Hinweise des Ursprungsprojekts.
