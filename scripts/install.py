#!/usr/bin/env python3
"""Install Athena on a supported, already rooted Echo. Python 3 standard library only."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
BRIDGE_SHA = '83cb5fc1071da29d68864b17cf31a30fadaf56f90997357db8fd1c602d199dff'
BASE_SHA = '9f2a25d6cf4555585b363f46574f2274cc894e6edbe0bded7e55b386da0e8a00'

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()

def choose_device(output, requested=None):
    devices = {}
    for line in output.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[1] in ('device', 'unauthorized', 'offline', 'recovery', 'sideload'):
            devices[parts[0]] = parts[1]
    if requested:
        if devices.get(requested) != 'device':
            raise ValueError('The selected device is not ready in Android: ' + requested)
        return requested
    if len(devices) != 1 or next(iter(devices.values()), None) != 'device':
        raise ValueError('Connect exactly one Android device, or use --serial SERIAL. Recovery/sideload is not supported here.')
    return next(iter(devices))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=ROOT / 'Athena.apk')
    parser.add_argument('--serial', help='ADB serial; required if several devices are connected')
    parser.add_argument('--app-only', action='store_true', help='Keep the existing audio service; do not install the status bridge')
    parser.add_argument('--check', action='store_true', help='Check files and device without changing anything')
    args = parser.parse_args()
    if not shutil.which('adb'):
        raise ValueError('adb is missing. Install Android Platform Tools first.')
    if not args.apk.is_file():
        raise ValueError('APK missing or invalid. Download the release APK and use --apk PATH.')
    with args.apk.open('rb') as stream:
        if stream.read(4) != b'PK\x03\x04':
            raise ValueError('Invalid APK file header.')
    bridge = ROOT / 'bridge/hassmic-athena-status'
    if not args.app_only and (not bridge.is_file() or sha(bridge) != BRIDGE_SHA):
        raise ValueError('Status bridge missing or checksum mismatch.')
    def run(*cmd):
        result = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            # Do not echo arbitrary device output, which can contain credentials.
            raise RuntimeError('Command failed: ' + ' '.join(cmd[:4]))
        return result.stdout.strip()
    serial = choose_device(run('adb', 'devices'), args.serial)
    def adb(*cmd): return run('adb', '-s', serial, *cmd)
    def shell(cmd): return adb('shell', cmd)
    if shell('getprop ro.product.device') != 'athena' or shell('getprop ro.build.version.sdk') != '28':
        raise ValueError('Supported device: Echo Show 8, 2nd generation (athena), Android 9 only.')
    if shell('getprop ro.build.display.id') != 'PS75741.7080N':
        raise ValueError('This preview supports PS75741.7080N only. No firmware was changed.')
    current = ''
    if not args.app_only:
        if shell('id -u') != '0':
            raise ValueError('Root ADB is required for the status bridge. See docs/root.en.md.')
        if shell('test -f /system/hassmic/hassmic && echo present') != 'present':
            raise ValueError('Install echo-dot-assist first. See docs/install.en.md.')
        baseline = shell('sha256sum /system/hassmic/hassmic').split()[0]
        if baseline != BASE_SHA:
            raise ValueError('Unknown echo-dot-assist baseline. Build/review the bridge for your revision; --app-only keeps audio unchanged.')
        current = shell('if [ -f /data/local/hassmic/hassmic ]; then sha256sum /data/local/hassmic/hassmic; else echo absent; fi').split()[0]
        if current not in ('absent', BRIDGE_SHA):
            raise ValueError('A different audio override is installed. It was not overwritten.')
    print('Device:', serial, '| Firmware: PS75741.7080N')
    print('APK SHA-256:', sha(args.apk))
    if args.check:
        print('Checks passed. No changes made.')
        return
    # Package manager validates the APK before the audio service is touched.
    adb('install', '-r', str(args.apk.resolve()))
    if not args.app_only and current != BRIDGE_SHA:
        target = '/data/local/hassmic/hassmic.athena-new'
        adb('push', str(bridge), target)
        try:
            if shell('sha256sum ' + target).split()[0] != BRIDGE_SHA:
                raise RuntimeError('Device bridge checksum mismatch')
            shell('chown root:root ' + target + ' && chmod 755 ' + target)
            shell('mv ' + target + ' /data/local/hassmic/hassmic && stop hassmic && start hassmic')
        finally:
            shell('rm -f ' + target)
    adb('shell', 'am', 'start', '-n', 'de.athena.app/.MainActivity')
    print('Athena installed. Open Settings on the Echo to connect Home Assistant and pair your browser.')
    if args.app_only:
        print('Audio service unchanged. Automatic voice animation needs the Athena status bridge.')
    else:
        print('Original audio binary retained under /system/hassmic/hassmic. See docs/install.en.md for rollback.')

if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print('Error:', error, file=sys.stderr)
        sys.exit(1)
