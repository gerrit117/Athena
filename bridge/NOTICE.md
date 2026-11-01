# Audio binary and corresponding source

`hassmic-athena-status` is built from echo-dot-assist revision
`1c00727d332c3b0ee4d13b59f702c78839431694` plus the adjacent status patch.

- Main echo-dot-assist code: MIT, copyright Julian Imhof; see `LICENSE-echo-dot-assist`.
- Vendored Monocypher: BSD-2-Clause or CC0-1.0.
- dr_flac: public domain or MIT-0.
- minimp3: CC0-1.0.
- RNNoise: BSD-3-Clause.
- libfreeaptx: LGPL-2.1-or-later, statically linked. A copy of LGPL 2.1 is in `licenses/`.

The complete corresponding audio source, with the Athena patch applied and original notices retained, is bundled as `echo-dot-assist-source.tar.gz`. It includes the Makefile and instructions to rebuild/relink the executable (`ATHENA-SOURCE.txt`). `build-bridge.sh` provides the pinned upstream build route.

Firmware libraries and wake-word models are obtained from the matching original firmware or the user's own device; they are not included here. The ELF executable links to those libraries dynamically. Neither Athena's MIT license nor the upstream code license grants rights to Amazon's firmware.

**Deutsch:** Das Quellarchiv enthält den vollständigen Audioprogramm-Quellstand einschließlich Patch, Build-Dateien und ursprünglichen Lizenzhinweisen. Bei eigener Änderung der statisch eingebundenen LGPL-Komponente lässt sich das Programm damit neu bauen und linken. Amazon-Firmware, Bibliotheken und Aktivierungswortmodelle werden nicht mitgeliefert.
