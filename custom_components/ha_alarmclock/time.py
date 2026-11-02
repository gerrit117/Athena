"""Editable time entity for each reusable alarm slot."""
from datetime import time
from homeassistant.components.time import TimeEntity
from homeassistant.core import callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from .const import DOMAIN, signal_update
from .device import build_device_info

async def async_setup_entry(hass, entry, async_add_entities):
    store=hass.data[DOMAIN][entry.entry_id]
    added=set()
    @callback
    def sync(device_id):
        device=store.devices.get(device_id)
        if not device:return
        entities=[]
        for slot in device.alarm_slots:
            if (device_id,slot) not in added:
                added.add((device_id,slot))
                entities.append(AlarmTime(store,entry.entry_id,device_id,slot))
        if entities:async_add_entities(entities)
    entry.async_on_unload(async_dispatcher_connect(hass,signal_update(entry.entry_id),sync))
    for device_id in store.devices:sync(device_id)

class AlarmTime(TimeEntity):
    _attr_should_poll=False
    _attr_has_entity_name=True
    _attr_icon='mdi:alarm'
    def __init__(self,store,entry_id,device_id,slot):
        self.store,self.entry_id,self.device_id,self.slot=store,entry_id,device_id,slot
        self._attr_unique_id=f'{device_id}_alarm_time_{slot}'
        self._attr_device_info=build_device_info(store,device_id)
    def alarm(self):
        device=self.store.devices[self.device_id]
        return device.alarms.get(device.alarm_slots.get(self.slot))
    @property
    def available(self):return self.alarm() is not None
    @property
    def name(self):
        alarm=self.alarm()
        return alarm.label or f'Alarm {alarm.time}' if alarm else 'Alarm'
    @property
    def native_value(self):
        alarm=self.alarm()
        return time.fromisoformat(alarm.time) if alarm else None
    async def async_added_to_hass(self):
        @callback
        def changed(device_id):
            if device_id==self.device_id:self.async_write_ha_state()
        self.async_on_remove(async_dispatcher_connect(self.hass,signal_update(self.entry_id),changed))
    async def async_set_value(self,value):
        alarm=self.alarm()
        if not alarm:return
        snapshot=self.store.transport.outbox.data['devices'][self.device_id]
        raw=next(a for a in snapshot['alarms'] if a['id']==alarm.id)
        await self.store.transport.execute(self.device_id,'update_alarm',alarm_id=alarm.id,
            time=value.strftime('%H:%M'),label=alarm.label,repeat_days=alarm.repeat.split(',') if alarm.repeat else [],
            volume=raw.get('volume',75),enabled=alarm.enabled)
