"""Alarm services and intent routing. Success requires the device's acknowledgement.

Adapted from HA Alarm Clock by TargetCrafter (GPL-3.0); Athena additions 2026.
"""
from __future__ import annotations
import re
import probatio as vol
from homeassistant.core import ServiceCall, SupportsResponse
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers import config_validation as cv, intent, entity_registry as er, device_registry as dr
from .const import DOMAIN
from .transport import DAYS

TIME = re.compile(r"^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$", re.I)

def parse_time_text(text):
    match = TIME.fullmatch(text.strip())
    if not match:
        raise HomeAssistantError("Time must use HH:MM, for example 07:30")
    hour, minute, period = int(match[1]), int(match[2] or 0), (match[3] or "").lower()
    if period:
        if not 1 <= hour <= 12:
            raise HomeAssistantError("Invalid 12-hour time")
        hour = hour % 12 + (12 if period == "pm" else 0)
    if hour not in range(24) or minute not in range(60):
        raise HomeAssistantError("Invalid time")
    return f"{hour:02d}:{minute:02d}"

def resolve_device(hass, store, requested=None, origin=None):
    devices = store.devices
    registry = dr.async_get(hass)
    if requested:
        if requested in devices:
            return requested
        device = registry.async_get(requested)
        if device:
            for domain, value in device.identifiers:
                if domain == DOMAIN and value in devices:
                    return value
        raise HomeAssistantError("Unknown Athena device")
    if origin:
        entities = er.async_get(hass)
        matches = []
        for device_id, payload in store.transport.outbox.data["devices"].items():
            entity = entities.async_get(payload.get("voice_satellite", ""))
            if entity and entity.device_id == origin:
                matches.append(device_id)
        if len(matches) == 1:
            return matches[0]
    if len(devices) == 1:
        return next(iter(devices))
    raise HomeAssistantError("Select an Athena device / Athena-Gerät auswählen")

async def execute(hass, store, action, data, origin=None):
    device_id = resolve_device(hass, store, data.get("device_id"), origin)
    if action == "list_alarms":
        snapshot = store.transport.outbox.data["devices"][device_id]
        return {"device_id": device_id, "alarms": snapshot.get("alarms", []),
                "sync": store.transport.outbox.status(device_id), "last_seen": snapshot.get("last_seen")}
    fields = {}
    if action in ("create_alarm", "update_alarm"):
        fields = {"time": parse_time_text(data["time"]), "label": data.get("label", "").strip(),
                  "repeat_days": data.get("repeat", []), "volume": data.get("volume", 75)}
        if len(fields["label"]) > 80 or any(day not in DAYS for day in fields["repeat_days"]):
            raise HomeAssistantError("Invalid label or repeat days")
        if not 1 <= fields["volume"] <= 100:
            raise HomeAssistantError("Volume must be between 1 and 100")
    if action in ("update_alarm", "delete_alarm"):
        alarm_id = str(data["alarm_id"])
        if alarm_id not in store.devices[device_id].alarms:
            raise HomeAssistantError("Unknown alarm / Unbekannter Wecker")
        fields["alarm_id"] = alarm_id
    result = await store.transport.execute(device_id, action, **fields)
    # Expose only a concise response; the full command remains in the sync sensor.
    return {"device_id": device_id, "status": result["status"],
            "request_id": result.get("request_id"), "result": result.get("result"),
            "confirmed": result["status"] == "confirmed"}

SCHEMA = {
    vol.Optional("device_id"): cv.string,
}
ALARM_SCHEMA = {
    **SCHEMA, vol.Required("time"): cv.string,
    vol.Optional("label", default=""): cv.string,
    vol.Optional("repeat", default=list): [vol.In(DAYS)],
    vol.Optional("volume", default=75): vol.All(vol.Coerce(int), vol.Range(min=1, max=100)),
}

def async_setup_assist(hass, store):
    schemas = {"create_alarm": ALARM_SCHEMA,
               "update_alarm": {**ALARM_SCHEMA, vol.Required("alarm_id"): cv.string},
               "delete_alarm": {**SCHEMA, vol.Required("alarm_id"): cv.string},
               "list_alarms": SCHEMA, "dismiss": SCHEMA, "snooze": SCHEMA}
    for name, schema in schemas.items():
        async def handle(call: ServiceCall, action=name):
            return await execute(hass, store, action, call.data)
        hass.services.async_register(DOMAIN, name, handle, schema=vol.Schema(schema), supports_response=SupportsResponse.OPTIONAL)
    intent.async_register(hass, CreateAlarmIntentHandler(store))

class CreateAlarmIntentHandler(intent.IntentHandler):
    intent_type = "HaAlarmClockCreateAlarm"
    slot_schema = {vol.Required("hour"): vol.All(vol.Coerce(int), vol.Range(min=0, max=23)),
                   vol.Optional("minute", default=0): vol.All(vol.Coerce(int), vol.Range(min=0, max=59))}

    def __init__(self, store):
        self.store = store

    async def async_handle(self, intent_obj):
        slots = self.async_validate_slots(intent_obj.slots)
        result = await execute(intent_obj.hass, self.store, "create_alarm", {
            "time": f'{slots["hour"]["value"]:02d}:{slots["minute"]["value"]:02d}'
        }, intent_obj.device_id)
        response = intent_obj.create_response()
        de = (intent_obj.language or "").startswith("de")
        if result["confirmed"]:
            response.async_set_speech("Der Wecker ist auf dem Echo gespeichert." if de else "The alarm is saved on the Echo.")
        else:
            response.async_set_speech("Der Echo hat den Wecker noch nicht bestätigt. Bitte prüfe die Verbindung." if de else "The Echo has not confirmed the alarm. Please check the connection.")
        return response
