# HA Alarm Clock · Athena

This Home Assistant integration is derived from [HA Alarm Clock](https://github.com/TargetCrafter/HA-Alarmclock) by TargetCrafter (Johan Aantjes), commit `723509e901afa8989979044863638a369b40d0d0`.

The integration, including Athena's modifications in this directory, is distributed under **GPL-3.0**, see [LICENSE](LICENSE). The Athena Android app implements the HTTP/event protocol independently; no upstream Android app code is included.

Athena modifications (November 2026): durable command queue and device acknowledgements; persisted snapshots; string alarm identifiers; non-optimistic switches; synchronization sensor; HA 2026.11 Assist tools; DE/EN intents and text; bounded authenticated protocol 2 endpoint. This extension requires Athena's protocol 2 client. It does not claim compatibility with the unmodified upstream Android app.

Die HA-Integration basiert auf HA Alarm Clock und steht einschließlich der Athena-Erweiterungen unter GPL-3.0. Die Android-App von Athena enthält keinen übernommenen Android-Code dieses Projekts. Diese erweiterte Integration benötigt den Protokoll-2-Client von Athena.
