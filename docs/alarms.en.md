# Alarms with Home Assistant

Athena stores and schedules alarms on the Echo. Home Assistant provides controls, voice commands and automations. An alarm already delivered to the Echo rings without Home Assistant or Wi-Fi. The Echo must remain powered on and the sound must be available locally.

## Setup

1. Install the current Athena APK and connect Athena to Home Assistant.
2. Add `https://github.com/gerrit117/Athena` as a custom **Integration** repository in the HA Marketplace.
3. Install **HA Alarm Clock · Athena** and restart HA. The extended integration is included; the unmodified upstream integration alone is not sufficient.
4. In HA, open **Settings → Devices & services → Add integration**, find **HA Alarm Clock · Athena**, and add it.
5. In Athena's WebUI, open **Alarms**, enable synchronization, select this Echo's ESPHome voice satellite and save. Athena uses its existing HA connection, with no additional token. The HA account must be an administrator.
6. HA creates a device with Athena's name: next alarm, ringing, snooze/dismiss and synchronization status. Each alarm has an enable switch, editable time and next-trigger sensor.

Create an alarm on the Echo, in its WebUI, or with the HA action:

```yaml
action: ha_alarmclock.create_alarm
data:
  time: "07:30"
  label: Wake up
  repeat: [mon, tue, wed, thu, fri]
  volume: 65
```

Omit `repeat` for a one-off at the next occurrence of that time. Add `device_id` when multiple devices are registered. The action can return a response: only `confirmed: true` means that the Echo saved and scheduled the alarm.

## Voice

For HA **2026.11**, the integration contributes alarm tools to the Assist API. Allow Home Assistant device control in your conversation agent. Examples: “Set an alarm for 5 AM”, “Wake me up on weekdays at 7 AM”, “List my alarms”, “Delete the wake-up alarm”, “Stop the alarm”, “Snooze”.

Recognition depends on your conversation agent. Athena processes actual tool commands, never spoken response text. The configured voice satellite selects the originating Echo; ambiguous requests require a device selection.

For the rule-based HA conversation agent, optionally copy the supplied files from `custom_components/ha_alarmclock/custom_sentences/en/` into `<HA configuration>/custom_sentences/en/` and reload conversation configuration. These simple sentences create alarms using hours/minutes. Free-form commands, management and weekdays use the conversation agent's Assist tools.

## Synchronization and outages

- **Synchronized:** the Echo reported the displayed state.
- **Pending:** HA saved a command but the Echo has not confirmed it. A new alarm is not yet safely stored on the Echo.
- **Offline:** HA retains the last known snapshot; alarms already on the Echo keep working.
- **Failed/expired:** the change could not be applied. A new alarm arriving after its intended time is not silently scheduled for the following day.

Local changes upload after reconnection. Pending HA commands then apply in order. An outstanding HA edit takes precedence for the same alarm when delivered. An edit cannot recreate an alarm deleted locally. HA restarts preserve the queue. Confirmed commands are not executed again.

Deleting an alarm in HA during an outage only removes it from the Echo after delivery. It may ring until then. Dismiss and five-minute snooze work on the touchscreen offline; voice commands require a reachable voice pipeline.

Based on [HA Alarm Clock](https://github.com/TargetCrafter/HA-Alarmclock). The derived HA integration is GPL-3.0; see its [license and attribution](../custom_components/ha_alarmclock/NOTICE.md).
