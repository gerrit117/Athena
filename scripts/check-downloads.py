#!/usr/bin/env python3
"""Check external root/firmware downloads. Does not unpack, execute or flash them."""
import argparse
import hashlib
from pathlib import Path

FILES = {
    'saiyaku-athena-v1.1.0.zip': '15f9c029f160a85f7545fcad41c9ee765763e10a7d9ed2d8061be647cfadfe10',
    'boot-root.zip': '741d904f7e2616c70be2c10f3466efb515769632779546a869f5591a6a9c827a',
    'update-kindle-athena-PS75741_user_7080_0036274153604.bin': '7a76fe6f84847c183e617fe6bb0c311faa9ad5ab56803b3a6115fb763c422a1a',
}

def check(directory):
    valid = True
    for name, expected in FILES.items():
        path = directory / name
        if not path.is_file():
            print('MISSING:', name)
            valid = False
            continue
        digest = hashlib.sha256()
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(chunk)
        match = digest.hexdigest() == expected
        print(('OK:' if match else 'WRONG CHECKSUM:'), name)
        valid &= match
    return valid

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, help='Folder containing the three original downloads')
    raise SystemExit(0 if check(parser.parse_args().directory) else 1)
