import importlib.util
from pathlib import Path
from datetime import datetime
import unittest
path=Path(__file__).resolve().parents[1]/'custom_components/ha_alarmclock/schedule.py'
spec=importlib.util.spec_from_file_location('schedule',path);s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
def epoch(text):return datetime.fromisoformat(text.replace('Z','+00:00')).timestamp()
class ScheduleTests(unittest.TestCase):
 def test_spring_gap_moves_forward_instead_of_backwards(self):
  self.assertEqual(epoch('2026-03-29T01:30:00Z'),s.next_trigger(2,30,'Europe/Berlin',epoch('2026-03-28T22:00:00Z')))
 def test_fall_second_occurrence_can_still_be_selected(self):
  self.assertEqual(epoch('2026-10-25T01:30:00Z'),s.next_trigger(2,30,'Europe/Berlin',epoch('2026-10-25T00:45:00Z')))
 def test_weekday_repeat_skips_weekend(self):
  self.assertEqual(epoch('2026-11-09T04:00:00Z'),s.next_trigger(5,0,'Europe/Berlin',epoch('2026-11-06T05:00:00Z'),['mon','tue','wed','thu','fri']))
 def test_exact_current_minute_is_not_in_the_future(self):
  self.assertEqual(epoch('2026-11-03T00:00:00Z'),s.next_trigger(0,0,'UTC',epoch('2026-11-02T00:00:00Z')))
 def test_invalid_day_is_rejected(self):
  with self.assertRaises(ValueError):s.next_trigger(5,0,'UTC',0,['monday'])
