#!/usr/bin/env bash
# Fetch the reviewed audio source. Optional installation starts upstream's guided setup.
set -euo pipefail
athena_root=$(cd -- "$(dirname -- "$0")/.." && pwd)
athena_revision=1c00727d332c3b0ee4d13b59f702c78839431694
athena_source="$athena_root/vendor/echo-dot-assist"
athena_mode=${1:---fetch}
case "$athena_mode" in
  --fetch|--install) ;;
  *) echo "Usage: $0 [--fetch|--install]" >&2; exit 2 ;;
esac
command -v git >/dev/null || { echo "Install git first." >&2; exit 1; }
if [[ ! -e "$athena_source" ]]; then
  mkdir -p "$athena_root/vendor"
  git clone --filter=blob:none --no-checkout https://github.com/Gamer92000/echo-dot-assist.git "$athena_source"
  git -C "$athena_source" checkout --detach "$athena_revision"
fi
[[ $(git -C "$athena_source" rev-parse HEAD) == "$athena_revision" ]] || {
  echo "Different source revision. Keep your changes; use a fresh Athena checkout." >&2; exit 1;
}
[[ -z $(git -C "$athena_source" status --porcelain --untracked-files=no) ]] || {
  echo "Tracked upstream files were changed. Refusing to run them." >&2; exit 1;
}
if [[ "$athena_mode" == --fetch ]]; then
  echo "Source ready: $athena_source"
  echo "Next: docs/install.en.md or docs/install.de.md"
  exit 0
fi
command -v adb >/dev/null || { echo "Install Android Platform Tools first." >&2; exit 1; }
athena_devices=$(adb devices | awk 'NR>1 && NF>=2 {n++} END {print n+0}')
[[ "$athena_devices" == 1 ]] || { echo "Connect exactly one device over USB." >&2; exit 1; }
[[ $(adb shell getprop ro.product.device | tr -d '\r') == athena ]] || { echo "Wrong device." >&2; exit 1; }
[[ $(adb shell id -u | tr -d '\r') == 0 ]] || { echo "Root ADB required. Read docs/root.en.md." >&2; exit 1; }
[[ $(adb shell getprop ro.build.display.id | tr -d '\r') == PS75741.7080N ]] || {
  echo "Only rooted PS75741.7080N is supported. No firmware was changed." >&2; exit 1;
}
if adb shell 'test -f /system/hassmic/hassmic && echo installed' | tr -d '\r' | grep -qx installed; then
  echo "echo-dot-assist is already installed. Use scripts/install.py; audio setup was not repeated."
  exit 0
fi
echo "Starting the original guided setup on the rooted Echo."
echo "Choose: Keep it as it is: skip the unlock, firmware and root steps."
echo "Do not choose Start from scratch."
# The bundled status binary was tested against this revision's local source build.
# Pin the generated build-mode setting rather than accepting an unverified release binary.
mkdir -p "$athena_source/build/athena"
printf '%s\n' source > "$athena_source/build/athena/setup.build"
cd "$athena_source"
exec bash scripts/setup.sh athena
