#!/usr/bin/env python3
"""Download original root files and verify pinned checksums. Never executes or flashes them."""
import argparse
import hashlib
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

_spec = importlib.util.spec_from_file_location('check_downloads', Path(__file__).with_name('check-downloads.py'))
_checker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_checker)
FILES = _checker.FILES
UNLOCK = 'saiyaku-athena-v1.1.0.zip'
ROOT = 'boot-root.zip'
FIRMWARE = 'update-kindle-athena-PS75741_user_7080_0036274153604.bin'
DEFAULT_UNLOCK_URL = 'https://xdaforums.com/attachments/saiyaku-athena-v1-1-0-zip.6397330/'
SOURCE_PAGE = 'https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-8-2nd-gen-2021-athena.4804958/'
FIRMWARE_PAGE = 'https://ftvdb.com/echo/firmware/com.amazon.athena.android.os/84ffb126664e1a3569404ba61d580e05-36274153604-fire-os-7574-1-ps75741-7080-2026-09-29/'

def checksum(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()

def validate_url(url, kind):
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.username or parsed.password or not parsed.hostname:
        raise ValueError('Use an HTTPS download URL without embedded credentials.')
    host = parsed.hostname.lower()
    if kind in (UNLOCK, ROOT):
        allowed = host == 'xdaforums.com' or host.endswith('.xdaforums.com')
    else:
        allowed = (host == 'softwareupdates.amazon.com' or host.endswith('.amazon.com')
                   or host == 'amazonaws.com' or host.endswith('.amazonaws.com')
                   or host == 'cloudfront.net' or host.endswith('.cloudfront.net'))
    if not allowed:
        raise ValueError('Expected the original XDA attachment or an Amazon firmware download URL.')
    return url

def download(url, destination, expected, max_bytes):
    temporary = None
    try:
        request = Request(url, headers={'User-Agent': 'Athena-root-file-downloader/1.0'})
        with urlopen(request, timeout=30) as response:
            if urlsplit(response.geturl()).scheme != 'https':
                raise ValueError('Refusing an unencrypted redirect.')
            if 'text/html' in response.headers.get('Content-Type', '').lower():
                raise ValueError('The server returned a webpage instead of the file. Download it manually from the source page.')
            with tempfile.NamedTemporaryFile(dir=destination.parent, prefix='.athena-download-', delete=False) as stream:
                temporary = Path(stream.name)
                digest = hashlib.sha256()
                total = 0
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > max_bytes:
                        raise ValueError('Download exceeds the expected size limit.')
                    stream.write(chunk)
                    digest.update(chunk)
        if digest.hexdigest() != expected:
            raise ValueError('SHA-256 mismatch. The download was discarded.')
        # A verified file appears under its final name only after the transfer finishes.
        # Exclusive hard-link creation keeps a concurrently created destination intact.
        os.link(temporary, destination)
        temporary.unlink()
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path('downloads'))
    parser.add_argument('--unlock-url', default=DEFAULT_UNLOCK_URL)
    parser.add_argument('--root-url', help='Original boot-root.zip XDA attachment URL')
    parser.add_argument('--firmware-url', help='Original Amazon URL for the pinned 7080 firmware')
    parser.add_argument('--offline', action='store_true', help='Only verify existing files; do not access the network')
    args = parser.parse_args()
    args.directory.mkdir(parents=True, exist_ok=True)
    sources = {UNLOCK: args.unlock_url, ROOT: args.root_url, FIRMWARE: args.firmware_url}
    failed = False
    for name, expected in FILES.items():
        destination = args.directory / name
        if destination.exists():
            if destination.is_file() and checksum(destination) == expected:
                print('OK, existing:', name)
            else:
                print('WRONG CHECKSUM, existing file kept:', name, file=sys.stderr)
                failed = True
            continue
        if args.offline or not sources[name]:
            print('MISSING:', name)
            if not args.offline:
                print('Source:', FIRMWARE_PAGE if name == FIRMWARE else SOURCE_PAGE)
                print('Supply --firmware-url or --root-url as applicable, or download manually into', args.directory)
            failed = True
            continue
        try:
            print('Downloading:', name, flush=True)
            download(validate_url(sources[name], name), destination, expected,
                     2 * 1024**3 if name == FIRMWARE else 150 * 1024**2)
            print('OK, downloaded:', name)
        except (OSError, ValueError) as error:
            print('Download failed for', name + ':', error, file=sys.stderr)
            failed = True
    if failed:
        print('Some files are missing or invalid. Nothing was executed or flashed.', file=sys.stderr)
        return 1
    print('All three files verified. Continue with docs/root.en.md or docs/root.de.md.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
