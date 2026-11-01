# Root: Echo Show 8, 2. Generation

[English](root.en.md) · [Weiter zur Installation](install.de.md)

Diese Anleitung beschreibt den hier geprüften Weg für **A8H3N2 / athena**. Sie gilt nicht für andere Echo-Show-Generationen. Unlock und Firmwarewechsel können das Gerät unbrauchbar machen. Ein Werksreset löscht Konten, Einstellungen und Dateien. Sichere benötigte Daten vorher.

Root und Recovery stammen von **saiyaku-athena**, nicht von Athena. Maßgeblich sind die Geräte- und Versionshinweise im [Originalbeitrag auf XDA](https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-8-2nd-gen-2021-athena.4804958/). Gibt es dort abweichende Hinweise für deine Ausgangsversion, nicht mit dieser Kurzfassung fortfahren.

## Benötigt

- Echo Show 8, 2. Generation, mit Originalnetzteil und Micro-USB-Datenkabel.
- Linux-PC mit USB, `adb`, `unzip`, Python 3 und PySerial. Für den seriellen Port sind passende Benutzerrechte erforderlich, unter vielen Distributionen über die Gruppe `dialout` oder `uucp`.
- Die drei Originaldownloads aus der folgenden Tabelle.

## Dateien

| Datei | Quelle | SHA-256 |
|---|---|---|
| `saiyaku-athena-v1.1.0.zip` | Anhänge im [XDA-Beitrag](https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-8-2nd-gen-2021-athena.4804958/) | `15f9c029f160a85f7545fcad41c9ee765763e10a7d9ed2d8061be647cfadfe10` |
| `boot-root.zip` | Anhänge im selben XDA-Beitrag | `741d904f7e2616c70be2c10f3466efb515769632779546a869f5591a6a9c827a` |
| `update-kindle-athena-PS75741_user_7080_0036274153604.bin` | [FTVDB-Eintrag mit Amazon-Download](https://ftvdb.com/echo/firmware/com.amazon.athena.android.os/84ffb126664e1a3569404ba61d580e05-36274153604-fire-os-7574-1-ps75741-7080-2026-09-29/) | `7a76fe6f84847c183e617fe6bb0c311faa9ad5ab56803b3a6115fb763c422a1a` |

Lege die unveränderten Downloads zusammen in einen Ordner und prüfe sie:

```sh
python3 scripts/check-downloads.py /pfad/zu/downloads
```

Nur fortfahren, wenn alle drei Dateien mit `OK` gemeldet werden. Das Skript führt keine Datei aus und flasht nichts. Ein gleicher Dateiname reicht nicht als Prüfung.

Amazon-Firmware, Unlocker und Root-Paket werden hier nicht mitgeliefert. Die Versionen werden nicht automatisch durch „die neueste“ ersetzt. Das Repository enthält den Prüfhelfer und die eigene Status-Erweiterung; deren Quellpatch und Lizenz liegen unter `bridge/`.

## 1. Ausgangsversion ablesen

Lies am Echo unter **Einstellungen → Geräteoptionen → Info** die vollständige Softwareversion ab. Nicht vorher online aktualisieren. Der hier durchgeführte Weg begann bei **7.5.7.2 / PS7572 / Build 5414** und endete bei **PS75741.7080N**. Daraus folgt keine Freigabe für beliebige andere Ausgangsversionen oder Downgrades.

Der geprüfte echo-dot-assist-Stand benötigt Build **7080**, nicht 7082. Ziel ist die passende Version, nicht die höchste Versionsnummer.

## 2. Unlocker vorbereiten

```sh
python3 -m venv .venv-root
.venv-root/bin/pip install pyserial
unzip /pfad/zu/downloads/saiyaku-athena-v1.1.0.zip -d /pfad/zum/unlocker
```

Prüfe zusätzlich mitgelieferte Anforderungen des Originalpakets. Auf unserem Gerät wurde **v1.1.0** eingesetzt. Die ältere v1.0.0 lehnte dessen Layout-Metadaten ab. Diese Prüfung wurde nicht umgangen. Eine andere Version ist nicht allein deshalb austauschbar, weil ihr Dateiname ähnlich ist. Der gepinnte echo-dot-assist-Assistent nennt noch v1.0.0; für den hier beschriebenen Root-Weg wird deshalb der unveränderte v1.1.0-Unlocker separat gestartet.

## 3. Bootloader entsperren

1. Micro-USB am PC angeschlossen lassen, das Echo-Netzteil abziehen.
2. Im entpackten Ordner `saiyaku-athena` den Originalstarter ausführen. Die Python-Umgebung muss dabei im Suchpfad liegen:

   ```sh
   export PATH="/absoluter/pfad/zur/.venv-root/bin:$PATH"
   ./meta-step.sh
   ```

3. Lies die Ausgabe und bestätige die Abfrage des Originalprogramms selbst.
4. Erst wenn das Programm auf das Gerät wartet, das Echo-Netzteil einstecken. USB angeschlossen lassen.
5. Den Vorgang vollständig abwarten. Kurze USB-Abmeldungen während des Bootens sind möglich. „META mode“ allein bedeutet noch nicht, dass der Unlock fertig ist. Das Ziel ist der erfolgreiche Abschluss mit TWRP.

Bei „Permission denied“ den seriellen Zugriff des Linux-Benutzers korrigieren. Bei einer Versions-, Layout- oder Sicherheitsablehnung abbrechen und die Ursache anhand des Originalprojekts klären. Keine Prüfungen auskommentieren.

## 4. Geprüfte Firmware in TWRP installieren

Nur nach erfolgreich abgeschlossenem Unlock und für einen vom Originalprojekt unterstützten Ausgangsstand. Bei bereits passender Firmware ist kein erneutes Flashen nötig.

1. In TWRP **Advanced → ADB Sideload** öffnen. Keine zusätzlichen Wipe-Optionen auswählen.
2. Am PC `adb devices` prüfen. Das richtige Gerät muss als `sideload` erscheinen. Bei mehreren angeschlossenen Geräten alle folgenden Befehle mit `adb -s DEINE-SERIENNUMMER …` ausführen.
3. Das geprüfte Firmwarepaket übertragen:

   ```sh
   adb sideload /pfad/zu/downloads/update-kindle-athena-PS75741_user_7080_0036274153604.bin
   ```

4. TWRPs Abschlussmeldung kontrollieren. Ein Prozentwert am PC allein bestätigt keine erfolgreiche Installation. Bei einem Fehler nicht mit Root oder weiteren Flash-Versuchen fortfahren.
5. Für eine saubere Neueinrichtung in TWRP **Wipe → Format Data** wählen und die Löschung am Gerät bestätigen. Dabei werden auch Dateien im internen Speicher gelöscht. Nicht Boot, System, Vendor oder andere Partitionen zusätzlich löschen.

Falls TWRP anschließend ein erneutes Laden der Recovery verlangt, in die Recovery neu starten. Ein hängen gebliebener Sideload-Modus wird nicht durch weiteres blindes Senden von Paketen behoben. Zur Recovery gelangst du bei diesem Gerät mit gedrückter Lautstärke-Plus-Taste beim Einstecken des Netzteils. Einen laufenden Schreibvorgang vorher vollständig abwarten.

## 5. Root installieren

In TWRP wieder **Advanced → ADB Sideload** starten:

```sh
adb sideload /pfad/zu/downloads/boot-root.zip
```

Nach erfolgreicher Abschlussmeldung über **Reboot → System** starten. Danach:

```sh
adb wait-for-device
adb shell id -u
adb shell getprop ro.product.device
adb shell getprop ro.build.display.id
```

Erwartet werden `0`, `athena` und `PS75741.7080N`. Nicht über die Amazon-Einrichtung erneut ein Softwareupdate starten. Jetzt mit [Installation, Abschnitt 2](install.de.md#2-echo-dot-assist-vorbereiten) fortfahren.

## Grenzen dieses Wegs

- Kein universeller Up-/Downgrade-Assistent und keine Freigabe für unbekannte Firmware.
- Das ursprüngliche Root-Paket verändert System, Vendor, ADB und die SELinux-Regeln. Root-ADB ist kein normaler Benutzerzugang. Keinen offenen ADB-Port ins Netz stellen.
- Die Stock-Amazon-Sprachsoftware wird beim anschließenden Einrichten von echo-dot-assist deaktiviert.
- Wiederherstellung und Entbricken richten sich nach dem XDA-Original. Zusätzliche Images aus anderen Echo-Modellen sind kein Ersatz.
