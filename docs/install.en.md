# Installation

[Deutsch](install.de.md) · [Root guide](root.en.md)

**Early test version. Tested hardware: Echo Show 8, 2nd generation (2021), model A8H3N2, device name `athena`, firmware `PS75741.7080N`. Other models and firmware versions are not supported by this preview.**

## Requirements

- A supported Echo and its power adapter. USB alone does not provide enough power.
- A Micro-USB data cable, not a charging-only cable.
- A Linux computer with Python 3, Git and Android Platform Tools (`adb`). Installing the APK does not require Android Studio or Node.js.
- Home Assistant on the same local network, with a working Assist pipeline.
- The matching Athena release APK and an extracted copy of this repository.

The complete audio setup is currently documented for Linux. The Python installer uses only the standard library.

## 1. Choose your starting point

| Current state | Start here |
|---|---|
| Stock Fire OS, no root | Follow the [root guide](root.en.md), then return here |
| Root available, echo-dot-assist missing | Section 2 |
| Root and compatible echo-dot-assist installed | Section 3 |
| Existing VACAV / View Assist setup | [Migrate voice ownership](migration.en.md), then section 3 |

Installing Athena on an already rooted device does not require a reset. The Athena installer never flashes firmware.

## 2. Prepare echo-dot-assist

Skip this section if the audio service is already installed. It provides microphone capture, wake words, voice transport and reply playback.

From the Athena directory:

```sh
bash scripts/prepare-eda.sh --fetch
```

This downloads the [reviewed echo-dot-assist revision](https://github.com/Gamer92000/echo-dot-assist/tree/1c00727d332c3b0ee4d13b59f702c78839431694). It does not install anything on the Echo.

Place these original downloads in `vendor/echo-dot-assist/firmware/athena/`:

- `update-kindle-athena-PS75741_user_7080_0036274153604.bin`
- `boot-root.zip`

Sources and checksums are in the [file list](root.en.md#files). Audio setup needs parts of this firmware even on an already rooted device. These downloads are not included in this repository.

```sh
bash scripts/prepare-eda.sh --install
```

The wrapper checks model, root and firmware, then starts the original echo-dot-assist setup. Choose **“Keep it as it is: skip the unlock, firmware and root steps”**, never “Start from scratch”. Follow the tools, build, network and installation steps. The Athena wrapper selects the local source build used to validate the status extension. It needs a compiler, Make, Android NDK, Brotli and e2fsprogs, with several GB of free storage. The upstream setup downloads missing tools.

The upstream installer changes system services, disables Amazon's voice software and configures network restrictions. After reboot, `adb shell id -u` must report `0`.

## 3. Install Athena

Keep power and USB connected. Check first:

```sh
adb devices
python3 scripts/install.py --apk /path/to/Athena.apk --check
```

With multiple devices connected, add `--serial YOUR-SERIAL` to every invocation. The installer will not choose one automatically.

```sh
python3 scripts/install.py --apk /path/to/Athena.apk
```

This installs the app and the bundled status extension for the reviewed audio build. The original stays at `/system/hassmic/hassmic`; the extension goes to `/data/local/hassmic/hassmic`. Firmware and user data are preserved.

An unknown audio build or a different override will not be overwritten. `--app-only` installs the app while keeping audio unchanged. Automatic voice animation needs a compatible status extension. For a different or locally built echo-dot-assist revision, review and rebuild the bridge instead of removing the checksum checks.

## 4. Connect voice directly to Home Assistant

1. In HA, open **Settings → Devices & services → Add integration → ESPHome**.
2. Enter the Echo's IP and port **26053**. The usual ESPHome port 6053 is not correct here.
3. Complete the encryption step offered by the setup flow. An existing installation needs its existing key; do not reset or replace it.
4. Open the new Echo device and select your Assist pipeline and a wake word, such as **Alexa**.
5. Test a spoken request, its reply and Athena's visualization.

For an existing keyed installation, the key is stored at `/data/local/hassmic/state/api_key`. It can be read locally through root ADB. Copy it only into the HA setup dialog; never into screenshots, issues or this repository.

The ESPHome integration is built into HA. No ESPHome firmware builder app is required. VACAV, View Assist and Music Assistant are not required for this setup. Music providers are a separate optional feature.

## 5. Configure the display and WebUI

1. Open Athena's settings on the Echo.
2. Enter the HA address and a long-lived access token created in your HA user profile. Athena stores it encrypted on the Echo.
3. Open `http://ECHO-IP:26804` in a browser.
4. Generate a four-digit one-time code on the Echo and use it to pair the browser.
5. Configure favorites, room/category groups, camera, doorbell trigger, ringtone and optional autostart.

No Lovelace dashboard is needed. The WebUI runs on the Echo. Pairing codes expire after five minutes and are consumed on successful login. Keep administration within your own local network.

## Updates

Use an APK with the same signing key:

```sh
python3 scripts/install.py --apk /path/to/new/Athena.apk
```

Android rejects a different signing key. Do not uninstall the configured app simply to work around this. Existing test APKs use a local debug key; migration to a future release signing key needs separate instructions.

## Rollback

Remove only the status extension and restart the original audio service:

```sh
adb shell 'stop hassmic; rm /data/local/hassmic/hassmic; start hassmic'
```

Uninstalling Athena through Android deletes its local settings. It does not remove root or echo-dot-assist. Follow the upstream documentation to remove that service.
