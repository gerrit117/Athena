"""HA storage adapter for the acknowledged Athena protocol."""
from __future__ import annotations
from time import time
import re
from zoneinfo import ZoneInfo
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store
from .const import DOMAIN, EVENT_COMMAND, signal_update
from .reliable import CommandOutbox
from .schedule import next_trigger

ID = re.compile(r"^[a-zA-Z0-9-]{1,64}$")
DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]

def validate_snapshot(payload):
    if not isinstance(payload, dict) or payload.get("protocol") != 2:
        raise ValueError("Athena alarm protocol 2 required")
    if not ID.fullmatch(payload.get("device_id", "")):
        raise ValueError("Invalid device id")
    if not isinstance(payload.get("device_name"), str) or len(payload["device_name"]) > 100:
        raise ValueError("Invalid device name")
    ZoneInfo(payload["zone"])
    if len(payload.get("alarms", [])) > 32 or len(payload.get("acks", [])) > 128:
        raise ValueError("Too many alarms or acknowledgements")
    ids = set()
    for alarm in payload.get("alarms", []):
        if not ID.fullmatch(alarm.get("id", "")) or alarm["id"] in ids:
            raise ValueError("Invalid or duplicate alarm id")
        ids.add(alarm["id"])
        if not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", alarm.get("time", "")):
            raise ValueError("Invalid time")
        if not isinstance(alarm.get("enabled"), bool) or len(alarm.get("label", "")) > 80:
            raise ValueError("Invalid alarm")
    for ack in payload.get("acks", []):
        if not ID.fullmatch(ack.get("request_id", "")) or not isinstance(ack.get("ok"), bool):
            raise ValueError("Invalid acknowledgement")
    return payload

class AlarmTransport:
    def __init__(self, hass, entry_id, store):
        self.hass, self.entry_id, self.store = hass, entry_id, store
        self.storage = Store(hass, 1, f"{DOMAIN}.athena.outbox")
        self.outbox = None

    async def load(self):
        data = await self.storage.async_load()
        self.outbox = CommandOutbox(self.storage.async_save, self.notify, data)
        for payload in self.outbox.data["devices"].values():
            self.store.apply_sync(payload)

    def notify(self, device_id):
        # Only a wake-up hint: the app retrieves commands over authenticated HTTP.
        self.hass.bus.async_fire(EVENT_COMMAND, {"device_id": device_id, "command": "sync"})
        async_dispatcher_send(self.hass, signal_update(self.entry_id), device_id)

    async def sync(self, payload):
        validate_snapshot(payload)
        if payload["device_id"] not in self.outbox.data["devices"] and len(self.outbox.data["devices"]) >= 25:
            raise ValueError("Too many devices")
        result = await self.outbox.sync(payload)
        self.store.apply_sync(payload)
        async_dispatcher_send(self.hass, signal_update(self.entry_id), payload["device_id"])
        return result

    async def execute(self, device_id, command, **fields):
        snapshot = self.outbox.data["devices"].get(device_id)
        if snapshot is None:
            raise HomeAssistantError("Athena has not synchronized yet")
        ttl = 120
        if command in ("create_alarm", "update_alarm"):
            zone = fields["zone"] = snapshot["zone"]
            hour, minute = map(int, fields["time"].split(":"))
            now=time()
            scheduled=next_trigger(hour,minute,zone,now,fields.get("repeat_days",[]))
            fields["scheduled_for"]=int(scheduled*1000)
            ttl=scheduled-now
        elif command in ("snooze", "dismiss"):
            fields["alarm_id"] = snapshot.get("ringing", {}).get("alarm_id")
            if not fields["alarm_id"]:
                return {"status": "confirmed", "result": {"ok": True, "inactive": True}}
        else:
            ttl = 86400
        try:
            record = await self.outbox.enqueue(device_id, command, fields, ttl=ttl)
            result = await self.outbox.wait(record["request_id"])
        except ValueError as err:
            raise HomeAssistantError(str(err)) from err
        self.notify(device_id)
        return result
