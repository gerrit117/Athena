#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 ]]; then
  echo "Usage: $0 /path/to/echo-dot-assist /path/to/android-ndk-r21e" >&2
  exit 2
fi
eda_source=$(realpath "$1")
athena_ndk=$(realpath "$2")
athena_bridge_dir=$(cd -- "$(dirname -- "$0")" && pwd)
expected=1c00727d332c3b0ee4d13b59f702c78839431694
[[ $(git -C "$eda_source" rev-parse HEAD) == "$expected" ]] || { echo "Unexpected EDA revision" >&2; exit 1; }
athena_build_dir=$(mktemp -d)
trap 'rm -rf -- "$athena_build_dir"' EXIT
git -C "$eda_source" archive "$expected" | tar -x -C "$athena_build_dir"
ln -s "$eda_source/firmware" "$athena_build_dir/firmware"
patch -d "$athena_build_dir" -p1 < "$athena_bridge_dir/echo-dot-assist-athena-status.patch"
make -C "$athena_build_dir" DEVICE=athena NDK="$athena_ndk" BUILD=1c00727-athena1 VERSION=2026.10.20.154939 BUILD_TIME='Oct 20 2026 15:49:39' build/athena/hassmic
cp "$athena_build_dir/build/athena/hassmic" "$athena_bridge_dir/hassmic-athena-status"
