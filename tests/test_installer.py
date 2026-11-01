import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('installer', Path(__file__).parents[1] / 'scripts/install.py')
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)

class DeviceSelectionTest(unittest.TestCase):
    def test_one_ready_device(self):
        self.assertEqual(installer.choose_device('List of devices attached\nabc\tdevice\n'), 'abc')

    def test_multiple_requires_selection(self):
        with self.assertRaises(ValueError):
            installer.choose_device('abc\tdevice\ndef\tdevice')
        self.assertEqual(installer.choose_device('abc\tdevice\ndef\tdevice', 'def'), 'def')

    def test_unready_never_selected(self):
        for status in ('offline', 'unauthorized', 'sideload', 'recovery'):
            with self.subTest(status=status), self.assertRaises(ValueError):
                installer.choose_device('abc\t' + status, 'abc')

    def test_unauthorized_second_device_still_requires_selection(self):
        with self.assertRaises(ValueError):
            installer.choose_device('abc\tdevice\ndef\tunauthorized')

    def test_bridge_matches_distribution(self):
        self.assertEqual(installer.sha(installer.ROOT / 'bridge/hassmic-athena-status'), installer.BRIDGE_SHA)

if __name__ == '__main__':
    unittest.main()
