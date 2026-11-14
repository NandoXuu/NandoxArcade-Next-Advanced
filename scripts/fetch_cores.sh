#!/usr/bin/env bash
# Baixa cores libretro para ARM64 e ARMv7, quando disponíveis, mais os assets do PPSSPP.
# Gera um APK universal instalável em Android TV ARM64 e ARMv7.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/system"
BASE="https://buildbot.libretro.com/nightly/android/latest"

CORES=(
  mupen64plus_next_gles3
  mgba
  pcsx_rearmed
  ppsspp
  melonds
  flycast
  pcsx2
)

mkdir -p "$ASSETS"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

is_elf() { [[ "$(head -c4 "$1" | tail -c3)" == "ELF" ]]; }

for abi in arm64-v8a armeabi-v7a; do
  JNI="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$JNI"
  for core in "${CORES[@]}"; do
    out="$JNI/lib${core}_libretro_android.so"
    if [[ -s "$out" && "${FORCE:-0}" != "1" ]] && is_elf "$out"; then
      echo "ok ($abi)  $core"
      continue
    fi
    if curl -fL --retry 2 --retry-delay 1 -sS -o "$tmp/$core-$abi.zip" \
      "$BASE/$abi/${core}_libretro_android.so.zip"; then
      if unzip -oq "$tmp/$core-$abi.zip" -d "$tmp/$abi"; then
        candidate="$tmp/$abi/${core}_libretro_android.so"
        if [[ -s "$candidate" ]] && is_elf "$candidate"; then
          mv "$candidate" "$out"
          echo "baixado ($abi) $core"
          continue
        fi
      fi
    fi
    if [[ "$abi" == "arm64-v8a" ]]; then
      echo "ERRO: core obrigatório ausente/inválido: $core ($abi)" >&2
      exit 1
    fi
    echo "aviso: $core não disponível para $abi; esse console não terá core ARMv7"
    rm -f "$out"
  done
done

pp="$ASSETS/PPSSPP.zip"
if [[ ! -s "$pp" || "${FORCE:-0}" == "1" ]]; then
  curl -fL --retry 3 --retry-delay 2 -sS -o "$pp" \
    "https://buildbot.libretro.com/assets/system/PPSSPP.zip"
  echo "baixado PPSSPP.zip"
else
  echo "ok (já existe) PPSSPP.zip"
fi

echo "Pronto. Cores em app/src/main/jniLibs/"
