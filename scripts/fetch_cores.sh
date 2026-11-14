#!/usr/bin/env bash
# Baixa os cores libretro LEVES para armeabi-v7a (ARM 32 bits, ex.: Philips Android TV 11) e arm64-v8a.
# O APK universal sai instalável em TVs/boxes ARMv7 e ARM64.
#
# Cada .so baixado tem o cabeçalho ELF conferido (classe e arquitetura): o nome da pasta do servidor
# não prova nada. Um arquivo 64 bits dentro da pasta armeabi-v7a (ou o contrário) é descartado e o
# build falha, em vez de gerar um APK que só mostra "ABI incompatível" na TV.
#
# Emuladores pesados (Flycast, melonDS, PPSSPP, PCSX2) não fazem parte da configuração ativa:
# os manifestos antigos estão em docs/plugins-desativados/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="https://buildbot.libretro.com/nightly/android/latest"

# Obrigatórios nas duas ABIs: se faltar ou for inválido, o build falha.
REQUIRED_CORES=(
  fceumm
  snes9x2010
  gambatte
  mgba
  genesis_plus_gx
  pcsx_rearmed
)

# Opcionais: se não houver um .so válido para a ABI, só avisa (esse console fica sem core).
OPTIONAL_CORES=(
  mupen64plus_next_gles3
)

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Arquitetura real do .so, lida do cabeçalho ELF: arm32 | arm64 | other | notelf
elf_arch() {
  local hex cls mach
  hex="$(od -An -tx1 -N20 "$1" | tr -d ' \n')"
  [[ "${hex:0:8}" == "7f454c46" ]] || { echo notelf; return; }  # magic \x7fELF
  cls="${hex:8:2}"      # 01 = 32 bits, 02 = 64 bits
  mach="${hex:36:4}"    # e_machine (little endian): 2800 = ARM, b700 = AArch64
  if [[ "$cls" == "01" && "$mach" == "2800" ]]; then echo arm32
  elif [[ "$cls" == "02" && "$mach" == "b700" ]]; then echo arm64
  else echo other
  fi
}

want_arch() {
  case "$1" in
    armeabi-v7a) echo arm32 ;;
    arm64-v8a) echo arm64 ;;
    *) echo "ABI desconhecida: $1" >&2; return 1 ;;
  esac
}

# fetch_one <abi> <core>  ->  0 = .so válido no lugar; 1 = falhou
fetch_one() {
  local abi="$1" core="$2" want out zip dir cand got
  want="$(want_arch "$abi")"
  out="$ROOT/app/src/main/jniLibs/$abi/lib${core}_libretro_android.so"

  if [[ -s "$out" && "${FORCE:-0}" != "1" && "$(elf_arch "$out")" == "$want" ]]; then
    echo "ok ($abi, $want) $core"
    return 0
  fi
  rm -f "$out"

  zip="$tmp/$core-$abi.zip"
  dir="$tmp/$abi-$core"
  mkdir -p "$dir"
  if curl -fL --retry 3 --retry-delay 2 -sS -o "$zip" "$BASE/$abi/${core}_libretro_android.so.zip" \
    && unzip -oq "$zip" -d "$dir"; then
    cand="$dir/${core}_libretro_android.so"
    if [[ -s "$cand" ]]; then
      got="$(elf_arch "$cand")"
      if [[ "$got" == "$want" ]]; then
        mv "$cand" "$out"
        echo "baixado ($abi, $got) $core"
        return 0
      fi
      echo "descartado ($abi) $core: o .so é '$got', esperado '$want'" >&2
    fi
  fi
  return 1
}

expected=()
for core in "${REQUIRED_CORES[@]}" "${OPTIONAL_CORES[@]}"; do
  expected+=("lib${core}_libretro_android.so")
done

failed=0
for abi in armeabi-v7a arm64-v8a; do
  JNI="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$JNI"

  for core in "${REQUIRED_CORES[@]}"; do
    if ! fetch_one "$abi" "$core"; then
      echo "ERRO: core obrigatório ausente/inválido: $core ($abi)" >&2
      failed=1
    fi
  done

  for core in "${OPTIONAL_CORES[@]}"; do
    if ! fetch_one "$abi" "$core"; then
      echo "aviso: $core não disponível/válido para $abi; esse console ficará sem core nesta ABI"
    fi
  done

  # remove cores de execuções antigas que não fazem mais parte da lista (ex.: flycast, ppsspp, pcsx2, melonds)
  for f in "$JNI"/lib*_libretro_android.so; do
    [[ -e "$f" ]] || continue
    keep=0
    for e in "${expected[@]}"; do
      [[ "$(basename "$f")" == "$e" ]] && keep=1
    done
    if [[ "$keep" -eq 0 ]]; then
      echo "removido core fora da configuração ativa ($abi): $(basename "$f")"
      rm -f "$f"
    fi
  done
done

# o PPSSPP.zip só servia ao PPSSPP, que saiu da configuração ativa
rm -f "$ROOT/app/src/main/assets/system/PPSSPP.zip"

if [[ "$failed" -ne 0 ]]; then
  echo "Falha: faltam cores obrigatórios (veja os erros acima)." >&2
  exit 1
fi
echo "Pronto. Cores em app/src/main/jniLibs/"
