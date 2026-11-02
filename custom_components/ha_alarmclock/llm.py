"""Contribute acknowledged alarm tools to the HA 2026.11 Assist API."""
import probatio
from homeassistant.components.llm import LLMTools
from homeassistant.core import callback
from homeassistant.helpers import llm
from homeassistant.exceptions import HomeAssistantError
from .const import DOMAIN
from .assist import execute, ALARM_SCHEMA, SCHEMA

class AlarmTool(llm.Tool):
    integration = DOMAIN
    annotations = llm.ToolAnnotations(destructive=False, open_world=False)

    def __init__(self, action, description, schema, store):
        self.action, self.store = action, store
        self.name = f"{DOMAIN}__{action}"
        self.description = description
        self.parameters = probatio.Schema(schema)

    async def async_call(self, hass, tool_input, llm_context):
        try:
            data = await execute(hass, self.store, self.action, tool_input.tool_args, llm_context.device_id)
            return llm.ToolResult(data=data, error=data.get("status") in ("failed", "expired"))
        except HomeAssistantError as err:
            return llm.ToolResult(data={"error": str(err)}, error=True)

@callback
def async_get_tools(hass, llm_context, api_id):
    if api_id != "assist" or not hass.data.get(DOMAIN):
        return None
    store = next(iter(hass.data[DOMAIN].values()))
    # Do not offer actions for an integration that has no paired devices yet.
    if not store.devices:
        return None
    return LLMTools(tools=[
        AlarmTool("create_alarm", "Set an alarm on Athena. time is local HH:MM in 24-hour format; repeat uses mon,tue,wed,thu,fri,sat,sun, empty for once. Confirm success ONLY if confirmed=true; pending means NOT yet saved on the Echo.", ALARM_SCHEMA, store),
        AlarmTool("list_alarms", "List Athena alarms and pending synchronization status.", SCHEMA, store),
        AlarmTool("delete_alarm", "Delete an Athena alarm. Get its alarm_id from list_alarms first.", {**SCHEMA, probatio.Required("alarm_id"): str}, store),
        AlarmTool("update_alarm", "Change an existing Athena alarm by alarm_id. Confirm success ONLY if confirmed=true.", {**ALARM_SCHEMA, probatio.Required("alarm_id"): str}, store),
        AlarmTool("dismiss", "Stop the currently ringing Athena alarm.", SCHEMA, store),
        AlarmTool("snooze", "Snooze the currently ringing Athena alarm for five minutes.", SCHEMA, store),
    ], prompt="Athena alarm tools store alarms on the physical device for offline ringing. Never claim an alarm was saved unless confirmed=true. A pending result must be described as awaiting the Echo's confirmation. Device_id is optional when speaking on the intended satellite; ask which device if ambiguous.")
