#!/usr/bin/env bash
# Auditoria dos binários nativos (.so): confere a arquitetura REAL pelo cabeçalho ELF, lista as
# dependências e o SHA-256. Não confia em nomes de arquivo, URLs ou metadados.
#
# Uso: bash scripts/audit_cores.sh [pasta-jniLibs | arquivo.apk]
#   sem argumento: audita app/src/main/jniLibs
#
# Cada pasta lib/<abi>/ precisa conter só binários da arquitetura da ABI:
#   armeabi-v7a -> ELF32 / ARM          arm64-v8a -> ELF64 / AArch64
#   x86         -> ELF32 / Intel 80386  x86_64    -> ELF64 / X86-64
# Também falha se um .so depende de uma biblioteca que não é do sistema Android nem vem junto.
# Sai com código 1 se algo estiver errado.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$ROOT/app/src/main/jniLibs}"

command -v readelf >/dev/null 2>&1 || { echo "ERRO: readelf (binutils) não encontrado" >&2; exit 2; }

work=""
trap '[[ -n "$work" ]] && rm -rf -- "$work"' EXIT

if [[ -f "$TARGET" && "$TARGET" == *.apk ]]; then
  work="$(mktemp -d)"
  unzip -oq "$TARGET" 'lib/*' -d "$work" || { echo "ERRO: não foi possível ler lib/ do APK" >&2; exit 2; }
  LIBROOT="$work/lib"
else
  LIBROOT="$TARGET"
fi
[[ -d "$LIBROOT" ]] || { echo "ERRO: pasta não encontrada: $LIBROOT" >&2; exit 2; }

# Bibliotecas que o Android já traz (NDK estável) e, por isso, não precisam vir no APK.
SYSTEM_LIBS=" libc.so libm.so libdl.so liblog.so libz.so libandroid.so libEGL.so libGLESv1_CM.so libGLESv2.so libGLESv3.so libOpenSLES.so libjnigraphics.so libstdc++.so "

expected() { # $1 = abi -> "classe|máquina"
  case "$1" in
    armeabi-v7a) echo "ELF32|ARM" ;;
    arm64-v8a)   echo "ELF64|AArch64" ;;
    x86)         echo "ELF32|Intel 80386" ;;
    x86_64)      echo "ELF64|Advanced Micro Devices X86-64" ;;
    *)           echo "?|?" ;;
  esac
}

errors=0
total=0
for dir in "$LIBROOT"/*/; do
  [[ -d "$dir" ]] || continue
  abi="$(basename "$dir")"
  IFS='|' read -r want_class want_machine <<<"$(expected "$abi")"
  echo "=== ABI $abi (esperado: $want_class / $want_machine)"
  shopt -s nullglob
  for so in "$dir"*.so; do
    total=$((total + 1))
    header="$(readelf -h "$so" 2>/dev/null)"
    class="$(sed -n 's/^ *Class: *//p' <<<"$header" | head -1)"
    machine="$(sed -n 's/^ *Machine: *//p' <<<"$header" | head -1)"
    needed="$(readelf -d "$so" 2>/dev/null | sed -n 's/.*(NEEDED).*\[\(.*\)\].*/\1/p' | tr '\n' ' ')"
    sha="$(sha256sum "$so" | cut -c1-16)"
    status="ok"
    if [[ "$class" != "$want_class" || "$machine" != "$want_machine" ]]; then
      status="ARQUITETURA ERRADA (${class:-sem ELF} / ${machine:-?})"
      errors=$((errors + 1))
    fi
    for dep in $needed; do
      if [[ "$SYSTEM_LIBS" != *" $dep "* && ! -e "$dir$dep" ]]; then
        status="$status; dependência ausente: $dep"
        errors=$((errors + 1))
      fi
    done
    printf '%-52s %-6s %-10s sha256:%s  %s\n' "$(basename "$so")" "$class" "$machine" "$sha" "$status"
    printf '    NEEDED: %s\n' "${needed:-—}"
  done
  shopt -u nullglob
done

echo "---- $total binário(s) auditado(s), $errors problema(s)"
[[ "$total" -gt 0 && "$errors" -eq 0 ]]
