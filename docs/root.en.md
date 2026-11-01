# Root: Echo Show 8, 2nd generation

[Deutsch](root.de.md) · [Continue with installation](install.en.md)

This guide covers the tested **A8H3N2 / athena** device only. It does not apply to other Echo Show generations. Unlocking and flashing can leave the device unusable. Formatting data deletes accounts, settings and internal files. Save anything you need first.

Root and recovery come from **saiyaku-athena**, not Athena. The [original XDA thread](https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-8-2nd-gen-2021-athena.4804958/) is the authority for model and firmware support. Stop if its instructions for your starting version differ from this summary.

## Requirements

- Supported Echo, original power adapter and Micro-USB data cable.
- Linux computer, USB, `adb`, `unzip`, Python 3 and PySerial. Your Linux user needs serial-port access, commonly through the `dialout` or `uucp` group.
- The three original downloads below.

## Files

| File | Source | SHA-256 |
|---|---|---|
| `saiyaku-athena-v1.1.0.zip` | Attachments in the [XDA thread](https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-8-2nd-gen-2021-athena.4804958/) | `15f9c029f160a85f7545fcad41c9ee765763e10a7d9ed2d8061be647cfadfe10` |
| `boot-root.zip` | Attachments in the same XDA thread | `741d904f7e2616c70be2c10f3466efb515769632779546a869f5591a6a9c827a` |
| `update-kindle-athena-PS75741_user_7080_0036274153604.bin` | [FTVDB listing with Amazon download](https://ftvdb.com/echo/firmware/com.amazon.athena.android.os/84ffb126664e1a3569404ba61d580e05-36274153604-fire-os-7574-1-ps75741-7080-2026-09-29/) | `7a76fe6f84847c183e617fe6bb0c311faa9ad5ab56803b3a6115fb763c422a1a` |

Keep the original downloads together and verify them:

```sh
python3 scripts/check-downloads.py /path/to/downloads
```

All three must report `OK`. This script does not unpack, execute or flash anything. Filenames alone do not verify a download.

Amazon firmware, the unlocker and the root package are not redistributed here. Do not substitute a newer version automatically. This repository includes the verifier and Athena's audio-status extension, with its source patch and license under `bridge/`.

## 1. Read the starting firmware

Read the complete software version under the Echo's **Settings → Device Options → About**. Do not update online first. Our tested path started at **7.5.7.2 / PS7572 / build 5414** and ended at **PS75741.7080N**. This does not establish support for other starting versions or arbitrary downgrades.

The pinned echo-dot-assist version needs build **7080**, not 7082. Matching firmware matters more than a higher version number.

## 2. Prepare the unlocker

```sh
python3 -m venv .venv-root
.venv-root/bin/pip install pyserial
unzip /path/to/downloads/saiyaku-athena-v1.1.0.zip -d /path/to/unlocker
```

Also check any requirements included in the original archive. This device was unlocked with **v1.1.0**. Version 1.0.0 rejected its layout metadata; those checks were not bypassed. Another version is not an interchangeable replacement. The pinned echo-dot-assist setup still names v1.0.0, so this guide runs the unchanged v1.1.0 unlocker separately.

## 3. Unlock the bootloader

1. Keep Micro-USB connected to the computer and disconnect the Echo's power adapter.
2. In the extracted `saiyaku-athena` directory, run its original launcher with the Python environment on your path:

   ```sh
   export PATH="/absolute/path/to/.venv-root/bin:$PATH"
   ./meta-step.sh
   ```

3. Read and answer the original program's prompt yourself.
4. Connect the Echo's power adapter only when the program is waiting for the device. Keep USB connected.
5. Wait until it finishes successfully in TWRP. Brief USB disconnects during startup can occur. “META mode” alone does not prove a completed unlock.

For “Permission denied”, fix your Linux serial permissions. For a firmware, layout or security rejection, stop and investigate with the upstream instructions. Do not comment out checks.

## 4. Install the verified firmware in TWRP

Only after a successful unlock and from a starting version supported by the original project. Skip reflashing if the correct firmware is already installed.

1. Open **Advanced → ADB Sideload** in TWRP. Leave extra wipe options unchecked.
2. Run `adb devices`. The correct Echo must appear as `sideload`. With multiple devices, use `adb -s YOUR-SERIAL …` for every command below.
3. Send the verified firmware:

   ```sh
   adb sideload /path/to/downloads/update-kindle-athena-PS75741_user_7080_0036274153604.bin
   ```

4. Check TWRP's final result. A percentage on the computer alone does not confirm success. Stop after an error instead of proceeding to root or trying more packages.
5. For a clean initial setup, choose **Wipe → Format Data** in TWRP and confirm on the device. This also removes internal files. Do not additionally wipe Boot, System, Vendor or other partitions.

If recovery requests a restart, reboot to recovery. Do not keep sending packages to a stuck sideload session. On this model, hold Volume Up while connecting the power adapter to enter recovery. Always wait for any active write to finish before interrupting power.

## 5. Install root

Start **Advanced → ADB Sideload** in TWRP again:

```sh
adb sideload /path/to/downloads/boot-root.zip
```

After TWRP reports success, select **Reboot → System**. Then check:

```sh
adb wait-for-device
adb shell id -u
adb shell getprop ro.product.device
adb shell getprop ro.build.display.id
```

Expected: `0`, `athena`, `PS75741.7080N`. Do not start another update through Amazon's setup. Continue with [installation, section 2](install.en.md#2-prepare-echo-dot-assist).

## Scope

- No universal upgrade/downgrade tool or support for unknown firmware.
- The original root package changes System, Vendor, ADB and SELinux rules. Root ADB is privileged access. Do not expose an open ADB port to the network.
- The following echo-dot-assist installation disables Amazon's stock voice software.
- Recovery/unbricking follows the original XDA instructions. Images from another Echo model are not a substitute.
