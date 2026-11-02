"""Persistent command outbox, independent of Home Assistant for failure tests.

Local snapshots are authoritative for actual alarm state. Pending HA commands
never change that state until the device applies them and sends a snapshot.
"""
from __future__ import annotations

import asyncio
from copy import deepcopy
from time import time
from uuid import uuid4


class CommandOutbox:
    def __init__(self, save, notify, data=None, clock=time):
        self.data = data or {"devices": {}, "commands": {}}
        self.clock = clock
        self.save = save
        self.notify = notify
        self.lock = asyncio.Lock()
        self.waiters = {}

    async def enqueue(self, device_id, command, fields, ttl=86400):
        async with self.lock:
            if device_id not in self.data["devices"]:
                raise ValueError("Device has not synchronized yet")
            commands = self.data["commands"]
            now = self.clock()
            # Keep unfinished commands and one week of results, with a firm bound.
            self.data["commands"] = commands = {
                k: v for k, v in commands.items()
                if now - v["created_at"] < 7 * 86400
            }
            finished=sorted((c for c in commands.values() if c["status"]!="pending"),key=lambda c:c["created_at"])
            for old in finished[:-512]:commands.pop(old["request_id"],None)
            if sum(c["status"] == "pending" for c in commands.values()) >= 128:
                raise ValueError("Too many pending commands")
            request_id = str(uuid4())
            record = {"request_id": request_id, "device_id": device_id,
                      "command": command, **deepcopy(fields), "status": "pending",
                      "created_at": now, "expires_at": now + ttl}
            commands[request_id] = record
            try:
                await self.save(deepcopy(self.data))
            except Exception:
                commands.pop(request_id, None)
                raise
            self.notify(device_id)
            return deepcopy(record)

    async def sync(self, payload):
        device_id = payload["device_id"]
        async with self.lock:
            previous = deepcopy(self.data)
            now = self.clock()
            snapshot = deepcopy(payload)
            acks = snapshot.pop("acks", [])
            snapshot["last_seen"] = now
            self.data["devices"][device_id] = snapshot
            accepted = []
            completed = []
            for ack in acks:
                record = self.data["commands"].get(ack["request_id"])
                if not record:
                    # Old receipts may outlive the bounded server history. Forgetting them
                    # is safe: an unknown command can never be redelivered by this outbox.
                    accepted.append(ack["request_id"])
                    continue
                if record["device_id"] != device_id:
                    continue
                if record["status"] == "pending":
                    record["status"] = "confirmed" if ack.get("ok") is True else "failed"
                    record["result"] = deepcopy(ack)
                    completed.append(record["request_id"])
                accepted.append(record["request_id"])
            for record in self.data["commands"].values():
                if record["status"] == "pending" and record["expires_at"] <= now:
                    record["status"] = "expired"
                    completed.append(record["request_id"])
            try:
                await self.save(deepcopy(self.data))
            except Exception:
                self.data = previous
                raise
            # A result must be durable before callers or devices can observe success.
            for request_id in completed:
                future = self.waiters.get(request_id)
                if future and not future.done():
                    future.set_result(deepcopy(self.data["commands"][request_id]))
            return {"ok": True, "protocol": 2, "acknowledged": accepted,
                    "commands": [deepcopy(c) for c in self.data["commands"].values()
                                 if c["device_id"] == device_id and c["status"] == "pending"]}

    async def wait(self, request_id, timeout=8):
        record = self.data["commands"][request_id]
        if record["status"] != "pending":
            return deepcopy(record)
        future = asyncio.get_running_loop().create_future()
        self.waiters[request_id] = future
        try:
            return await asyncio.wait_for(asyncio.shield(future), timeout)
        except TimeoutError:
            return deepcopy(self.data["commands"][request_id])
        finally:
            self.waiters.pop(request_id, None)
            if not future.done():
                future.cancel()

    def status(self, device_id):
        records = [c for c in self.data["commands"].values() if c["device_id"] == device_id]
        last = max(records, key=lambda c: c["created_at"], default=None)
        return {"pending": sum(c["status"] == "pending" for c in records),
                "last_command": deepcopy(last)}
