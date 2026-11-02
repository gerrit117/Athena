"""Support diagnostics without tokens, alarm labels or household entity lists."""
from importlib.metadata import version
from homeassistant.components.llm import async_get_tools
from homeassistant.helpers.llm import LLMContext
from .const import DOMAIN

async def async_get_config_entry_diagnostics(hass, entry):
    store=hass.data[DOMAIN][entry.entry_id]
    tools=await async_get_tools(hass,LLMContext(platform=DOMAIN,context=None,language='en',assistant='conversation',device_id=None),'assist')
    return {'protocol':2,'aioesphomeapi_version':version('aioesphomeapi'),'device_count':len(store.devices),
            'alarm_count':sum(len(d.alarms) for d in store.devices.values()),
            'pending_commands':sum(c['status']=='pending' for c in store.transport.outbox.data['commands'].values()),
            'assist_tools':[tool.name for tool in tools.tools if tool.name.startswith(DOMAIN+'__')]}
