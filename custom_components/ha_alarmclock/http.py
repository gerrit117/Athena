"""Authenticated snapshot upload and pending-command retrieval."""
import json
from aiohttp import web
from homeassistant.components.http import HomeAssistantView
from .const import DOMAIN

class AlarmClockSyncView(HomeAssistantView):
    url = "/api/ha_alarmclock/sync"
    name = "api:ha_alarmclock:sync"
    requires_auth = True

    def __init__(self, hass, entry_id, store):
        self.hass, self.entry_id, self.store = hass, entry_id, store

    async def post(self, request):
        if self.entry_id not in self.hass.data.get(DOMAIN, {}):
            return web.json_response({"error": "integration_unloaded"}, status=410)
        if not request["hass_user"].is_admin:
            return web.json_response({"error": "admin_required"}, status=403)
        try:
            if request.content_length is None or request.content_length > 65536:
                raise ValueError("Invalid payload size")
            payload = json.loads(await request.read())
            result = await self.store.transport.sync(payload)
        except (ValueError, KeyError, TypeError, IndexError):
            return web.json_response({"error": "malformed_payload"}, status=400)
        return web.json_response(result)
