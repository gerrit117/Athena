"""Failure-oriented checks for the durable HA/device command boundary."""
import asyncio
import copy
import importlib.util
from pathlib import Path
import unittest

path=Path(__file__).resolve().parents[1]/'custom_components/ha_alarmclock/reliable.py'
spec=importlib.util.spec_from_file_location('reliable',path)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class OutboxTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.saved=None;self.notifications=[];self.now=1000;self.fail=False
        async def save(data):
            if self.fail:raise OSError('disk full')
            self.saved=copy.deepcopy(data)
        self.save=save
        self.box=module.CommandOutbox(save,self.notifications.append,clock=lambda:self.now)
        await self.box.sync({'device_id':'echo','alarms':[]})

    async def test_offline_command_survives_ha_restart_without_false_success(self):
        record=await self.box.enqueue('echo','create_alarm',{'time':'07:00'})
        result=await self.box.wait(record['request_id'],timeout=.001)
        self.assertEqual('pending',result['status'])
        restored=module.CommandOutbox(self.save,self.notifications.append,copy.deepcopy(self.saved),clock=lambda:self.now)
        reply=await restored.sync({'device_id':'echo','alarms':[]})
        self.assertEqual(record['request_id'],reply['commands'][0]['request_id'])
        self.assertEqual([],restored.data['devices']['echo']['alarms'])

    async def test_ack_completes_waiter_only_after_durable_snapshot(self):
        record=await self.box.enqueue('echo','create_alarm',{})
        waiter=asyncio.create_task(self.box.wait(record['request_id']))
        await asyncio.sleep(0)
        await self.box.sync({'device_id':'echo','alarms':[{'id':'one'}], 'acks':[{'request_id':record['request_id'],'ok':True}]})
        self.assertEqual('confirmed',(await waiter)['status'])
        self.assertEqual('confirmed',self.saved['commands'][record['request_id']]['status'])
        self.assertEqual([{'id':'one'}],self.saved['devices']['echo']['alarms'])

    async def test_disk_failure_never_acknowledges_device_or_service(self):
        record=await self.box.enqueue('echo','create_alarm',{})
        self.fail=True
        with self.assertRaises(OSError):
            await self.box.sync({'device_id':'echo','acks':[{'request_id':record['request_id'],'ok':True}]})
        self.assertEqual('pending',self.box.data['commands'][record['request_id']]['status'])
        self.assertEqual('pending',(await self.box.wait(record['request_id'],.001))['status'])

    async def test_another_device_cannot_ack_a_command(self):
        record=await self.box.enqueue('echo','create_alarm',{})
        reply=await self.box.sync({'device_id':'other','acks':[{'request_id':record['request_id'],'ok':True}]})
        self.assertEqual([],reply['acknowledged'])
        self.assertEqual('pending',self.box.data['commands'][record['request_id']]['status'])

    async def test_expired_create_is_not_delivered_late(self):
        record=await self.box.enqueue('echo','create_alarm',{},ttl=10)
        self.now+=11
        reply=await self.box.sync({'device_id':'echo'})
        self.assertEqual([],reply['commands'])
        self.assertEqual('expired',self.box.data['commands'][record['request_id']]['status'])

    async def test_replayed_ack_is_safe_and_is_not_redelivered(self):
        record=await self.box.enqueue('echo','create_alarm',{})
        payload={'device_id':'echo','acks':[{'request_id':record['request_id'],'ok':True}]}
        await self.box.sync(payload)
        reply=await self.box.sync(payload)
        self.assertEqual([],reply['commands'])
        self.assertEqual([record['request_id']],reply['acknowledged'])

    async def test_device_failure_is_not_reported_as_confirmation(self):
        record=await self.box.enqueue('echo','update_alarm',{})
        await self.box.sync({'device_id':'echo','acks':[{'request_id':record['request_id'],'ok':False,'error':'deleted locally'}]})
        self.assertEqual('failed',(await self.box.wait(record['request_id']))['status'])

    async def test_deleted_local_alarm_is_not_restored_by_snapshot_merge(self):
        await self.box.sync({'device_id':'echo','alarms':[{'id':'one'}]})
        record=await self.box.enqueue('echo','update_alarm',{'alarm_id':'one'})
        await self.box.sync({'device_id':'echo','alarms':[], 'acks':[{'request_id':record['request_id'],'ok':False}]})
        self.assertEqual([],self.box.data['devices']['echo']['alarms'])

if __name__=='__main__':unittest.main()
