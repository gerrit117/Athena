# Migrate from VACAV / View Assist to direct ESPHome voice

[Deutsch](migration.de.md)

For an existing installation only. A new Athena installation does not require these components.

## Order

1. Back up HA configuration. Record the Echo IP, current pipeline and wake word.
2. Read the existing echo-dot-assist encryption key locally through root ADB from `/data/local/hassmic/state/api_key`. Never publish it in chat, issues or a repository.
3. Disable the VACAV entry for **this Echo** in HA. Keep it for rollback. This closes its voice subscription.
4. If present, remove only this Echo's ESPHome ignore record labelled **“managed by Vision”**. Keep other devices and ignore records.
5. Add the Echo through HA's ESPHome integration: Echo IP, port **26053**, existing key.
6. Select the previous Assist pipeline and wake word on the new ESPHome device.
7. Test a voice command, reply, mute button and Athena visualization. Also test startup after reboot.
8. Only then remove the old VACAV/View Assist entries and dashboards used exclusively by that setup. Update references to old entity IDs in automations, scripts and external clients first.

Do not run VACAV and HA's ESPHome integration as simultaneous voice owners of the same Echo. Athena reads voice status locally and does not open another audio subscriber.

## Music Assistant

Athena and direct ESPHome voice do not need Music Assistant. The previous VACAV upstream configuration checks Music Assistant during startup, so migrate voice before removing it.

Only uninstall Music Assistant if it is no longer wanted for other players, music sources or automations. Uninstalling it removes the service's configuration for all players, not just this Echo.

## Rollback

Disable the new ESPHome entry first, then re-enable the original VACAV entry. Keep the existing encryption key. Never enable both owners at once.

## Hardware verification

The test device has been moved to direct ESPHome voice. HA loads the satellite and exposes wake words and pipeline selection. The subsequent spoken request encountered an already known HTTP 400 issue in the HA pipeline. A complete successful spoken reply after migration has therefore not yet been confirmed. This is not a completed end-to-end voice test.
