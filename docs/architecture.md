# Architecture / Aufbau

```text
Home Assistant ── authenticated WebSocket ── AthenaRuntime ── Compose display
                                               │
Browser ── paired local HTTP session ── WebServer┘

HA ESPHome integration ── encrypted native API ── echo-dot-assist ── microphone / speaker
                                                      │
AthenaRuntime ── IPv4 loopback, read-only status ───────┘
```

**DE:** Die App verbindet Anzeige und Konfiguration. Der native Audiounterbau hält die Sprachverbindung zu HA. Der lokale Statusendpunkt überträgt weder Audiodaten noch Tokens. `VoiceVisualizer` und `ThemeRegistry` trennen Darstellung und Zustand. `PackageManifest` definiert einen Vertrag für spätere Pakete; ein Paketinstaller ist noch nicht implementiert.

**EN:** The app owns display and configuration; the native audio service owns voice transport. Loopback status carries no audio or credentials. `VoiceVisualizer` and `ThemeRegistry` separate rendering from state. `PackageManifest` defines a future package contract, not an existing package installer.

## Optional media receivers / Optionale Medienempfänger

**DE:** Gemeinsamer vorgesehener Zustand: Protokoll, Verfügbarkeit, Aktivierung, Sitzungs-ID, Wiedergabe, Titel, Interpret, Album, Cover, Lautstärke und unterstützte Aktionen. Steuerungen erscheinen nur, wenn der Empfänger sie an den Sender weitergeben kann. Ausschalten beendet Empfang und Netzwerkerkennung. Fehlende Metadaten werden ohne erfundene Titel behandelt. Sprache, Klingel und Wecker erhalten Vorrang vor Musik.

**EN:** Planned shared state includes protocol, availability, enabled state, session ID, playback, title, artist, album, artwork, volume and supported actions. Controls appear only when supported. Disabling stops reception and discovery. Missing metadata is handled explicitly. Voice, doorbell and alarms take priority over music.

**DE:** AirPlay 2 wird mit [Shairport Sync](https://github.com/mikebrady/shairport-sync/blob/master/AIRPLAY2.md) und [NQPTP](https://github.com/mikebrady/nqptp) geprüft. Synchrone Mehrraumwiedergabe benötigt eine passende Android-Audioausgabe mit verlässlicher Zeitmessung. Eine ungetestete PCM-Pipe ist kein Nachweis. Noch kein auf dem Echo getestetes AirPlay-2-Modul.

**EN:** AirPlay 2 is being evaluated using Shairport Sync and NQPTP. Multiroom synchronization requires a suitable Android output backend with timing feedback. An untested PCM pipe is not proof. No AirPlay 2 module has yet been verified on the Echo.

**DE:** [Google Cast Connect](https://developers.google.com/cast/docs/android_tv_receiver) setzt auf die Cast-Infrastruktur von Android-TV-Geräten. Es ist kein allgemeiner Fire-OS-Empfänger. Cast-Senderfunktionen oder die Steuerung vorhandener HA-Mediaplayer sind davon getrennte Funktionen. Google-Cast-Empfang wird derzeit nicht unterstützt oder zugesagt.

**EN:** Google Cast Connect builds on Android TV's Cast infrastructure, rather than providing a generic Fire OS receiver. A Cast sender or controller for existing HA players is a different function. Cast reception is not currently supported or promised.
