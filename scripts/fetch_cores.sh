#!/usr/bin/env bash
# Baixa os cores libretro pré-compilados (Android arm64-v8a) e os assets do PPSSPP.
# Usado no Termux e no GitHub Actions. Nada é compilado: são binários oficiais do buildbot da Libretro.
#
# Os cores vão para app/src/main/jniLibs/arm64-v8a/ com o prefixo "lib" exigido pelo Android
# (libmgba_libretro_android.so, ...). Os nomes precisam bater com "coreLib" em assets/plugins/core_*.json
# (há um teste unitário que verifica isso).
#
# Uso:  bash scripts/fetch_cores.sh          # só baixa o que falta
#       FORCE=1 bash scripts/fetch_cores.sh  # baixa tudo de novo
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ABI="arm64-v8a"
JNI="$ROOT/app/src/main/jniLibs/$ABI"
ASSETS="$ROOT/app/src/main/assets/system"
BASE="https://buildbot.libretro.com/nightly/android/latest/$ABI"

# nome do core no buildbot (sem _libretro_android.so)
CORES=(
  mupen64plus_next_gles3   # Nintendo 64
  flycast                  # Dreamcast
  melonds                  # Nintendo DS
  mgba                     # Game Boy Advance
  pcsx_rearmed             # PlayStation
  ppsspp                   # PSP
  melonds                  # Nintendo DS
  flycast                  # Dreamcast
  pcsx2
)

mkdir -p "$JNI" "$ASSETS"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

is_arm64_elf() {
  # ELF64 little-endian, machine AArch64 (EM_AARCH64 = 183).
  python3 - "$1" <<'PY'
import struct, sys
try:
    with open(sys.argv[1], "rb") as f:
        h = f.read(64)
    valid = (
        len(h) >= 20
        and h[:4] == b"\\x7fELF"
        and h[4] == 2
        and h[5] == 1
        and struct.unpack_from("<H", h, 18)[0] == 183
    )
    raise SystemExit(0 if valid else 1)
except (OSError, struct.error):
    raise SystemExit(1)
PY
}

for core in "${CORES[@]}"; do
  out="$JNI/lib${core}_libretro_android.so"
  if [[ -s "$out" && "${FORCE:-0}" != "1" ]] && is_arm64_elf "$out"; then
    echo "ok (já existe)  $core"
    continue
  fi
  curl -fL --retry 3 --retry-delay 2 -sS -o "$tmp/$core.zip" "$BASE/${core}_libretro_android.so.zip"
  unzip -oq "$tmp/$core.zip" -d "$tmp"
  mv "$tmp/${core}_libretro_android.so" "$out"
  is_arm64_elf "$out" || { echo "ERRO: $core não é um ELF64 AArch64 (arm64-v8a) válido" >&2; exit 1; }
  echo "baixado         $core ($(du -h "$out" | cut -f1))"
done

# O core do PPSSPP precisa de fontes/atlas em <system>/PPSSPP/. O app descompacta no primeiro uso.
pp="$ASSETS/PPSSPP.zip"
if [[ ! -s "$pp" || "${FORCE:-0}" == "1" ]]; then
  curl -fL --retry 3 --retry-delay 2 -sS -o "$pp" "https://buildbot.libretro.com/assets/system/PPSSPP.zip"
  echo "baixado         PPSSPP.zip ($(du -h "$pp" | cut -f1))"
else
  echo "ok (já existe)  PPSSPP.zip"
fi

echo "Pronto. Cores em: $JNI"
