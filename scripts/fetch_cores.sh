#!/usr/bin/env bash
# Baixa os cores libretro pré-compilados (binários oficiais do buildbot da Libretro) para o APK.
# Nada é compilado aqui. Usado no Termux e no GitHub Actions.
#
# ABIs:
#   armeabi-v7a  OBRIGATÓRIA. É a ARM de 32 bits das Android TVs (ex.: Philips 50PUG7406/78).
#   arm64-v8a    opcional (celulares e TVs de 64 bits); se faltar, só avisa.
#
# Cada binário baixado é conferido pelo cabeçalho ELF (readelf -h): "armeabi-v7a" precisa ser
# ELF32/ARM e "arm64-v8a" precisa ser ELF64/AArch64. O nome do arquivo ou da URL não prova nada.
#
# Os cores vão para app/src/main/jniLibs/<abi>/ com o prefixo "lib" exigido pelo Android
# (libmgba_libretro_android.so, ...). Os nomes precisam bater com "coreLib" em
# assets/plugins/core_*.json (há um teste unitário que verifica isso).
#
# Uso:  bash scripts/fetch_cores.sh          # só baixa o que falta
#       FORCE=1 bash scripts/fetch_cores.sh  # baixa tudo de novo
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="https://buildbot.libretro.com/nightly/android/latest"

REQUIRED_ABIS=(armeabi-v7a)
OPTIONAL_ABIS=(arm64-v8a)

# nome do core no buildbot (sem _libretro_android.so)
CORES=(
  fceumm                    # Nintendo NES
  quicknes                  # Nintendo NES (alternativa)
  snes9x                    # Super Nintendo
  gambatte                  # Game Boy / Game Boy Color
  gearboy                   # Game Boy / Game Boy Color (alternativa)
  mgba                      # Game Boy Advance
  gpsp                      # Game Boy Advance (alternativa leve)
  genesis_plus_gx           # Mega Drive / Genesis
  picodrive                 # Mega Drive / Genesis (alternativa)
  pcsx_rearmed              # PlayStation
  mupen64plus_next_gles3    # Nintendo 64 (precisa de OpenGL ES 3.0)
  mupen64plus_next_gles2    # Nintendo 64 (alternativa)
)

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Confere a arquitetura REAL do binário pelo cabeçalho ELF.
verify_elf() { # $1 = arquivo, $2 = abi
  local info
  if command -v readelf >/dev/null 2>&1; then
    info="$(readelf -h "$1" 2>/dev/null)" || return 1
    case "$2" in
      armeabi-v7a) grep -Eq 'Class:[[:space:]]+ELF32' <<<"$info" && grep -Eq 'Machine:[[:space:]]+ARM$' <<<"$info" ;;
      arm64-v8a)   grep -Eq 'Class:[[:space:]]+ELF64' <<<"$info" && grep -Eq 'Machine:[[:space:]]+AArch64' <<<"$info" ;;
      *) return 1 ;;
    esac
  elif command -v file >/dev/null 2>&1; then
    info="$(file -b "$1")"
    case "$2" in
      armeabi-v7a) grep -Eq 'ELF 32-bit LSB.*ARM' <<<"$info" ;;
      arm64-v8a)   grep -Eq 'ELF 64-bit LSB.*ARM aarch64' <<<"$info" ;;
      *) return 1 ;;
    esac
  else
    echo "ERRO: instale readelf (binutils) ou file para conferir os binários" >&2
    return 1
  fi
}

fetch() { # $1 = core, $2 = abi ; devolve 0 se o binário ficou correto em jniLibs
  local core="$1" abi="$2"
  local jni="$ROOT/app/src/main/jniLibs/$abi"
  local out="$jni/lib${core}_libretro_android.so"
  mkdir -p "$jni"
  if [[ -s "$out" && "${FORCE:-0}" != "1" ]] && verify_elf "$out" "$abi"; then
    echo "ok       ($abi) $core"
    return 0
  fi
  rm -f "$out"
  local url="$BASE/$abi/${core}_libretro_android.so.zip"
  local work="$tmp/$abi/$core"
  mkdir -p "$work"
  if ! curl -fL --retry 3 --retry-delay 2 -sS -o "$work/core.zip" "$url"; then
    echo "falhou   ($abi) $core  <- $url" >&2
    return 1
  fi
  if ! unzip -oq "$work/core.zip" -d "$work" || [[ ! -s "$work/${core}_libretro_android.so" ]]; then
    echo "zip sem ${core}_libretro_android.so ($abi)" >&2
    return 1
  fi
  if ! verify_elf "$work/${core}_libretro_android.so" "$abi"; then
    echo "ERRO: $core ($abi) NÃO tem a arquitetura esperada (cabeçalho ELF)" >&2
    return 1
  fi
  mv "$work/${core}_libretro_android.so" "$out"
  echo "baixado  ($abi) $core ($(du -h "$out" | cut -f1))"
}

for abi in "${REQUIRED_ABIS[@]}"; do
  for core in "${CORES[@]}"; do
    fetch "$core" "$abi" || { echo "ERRO: core obrigatório ausente/inválido: $core ($abi)" >&2; exit 1; }
  done
done

for abi in "${OPTIONAL_ABIS[@]}"; do
  for core in "${CORES[@]}"; do
    fetch "$core" "$abi" || echo "aviso: $core indisponível para $abi; esse console não terá core nessa ABI"
  done
done

# Remove binários de cores que não fazem mais parte da lista (ex.: Flycast, melonDS, PPSSPP, PCSX2),
# para que não voltem ao APK por restos de uma execução antiga.
for dir in "$ROOT"/app/src/main/jniLibs/*/; do
  [[ -d "$dir" ]] || continue
  for so in "$dir"lib*_libretro_android.so; do
    [[ -e "$so" ]] || continue
    name="$(basename "$so")"; name="${name#lib}"; name="${name%_libretro_android.so}"
    keep=0
    for core in "${CORES[@]}"; do [[ "$core" == "$name" ]] && keep=1; done
    if [[ "$keep" == "0" ]]; then rm -f "$so"; echo "removido (fora da lista) $(basename "$dir")/$(basename "$so")"; fi
  done
done

echo "Pronto. Cores em: $ROOT/app/src/main/jniLibs/"
