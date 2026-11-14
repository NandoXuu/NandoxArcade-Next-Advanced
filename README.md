<p align="center">
  <img src="docs/assets/thumb.png" alt="NDXEMULATOR" width="100%">
</p>

<h1 align="center">NDXEMULATOR</h1>

<p align="center">
  Emulador de jogos retrô para Android, com biblioteca, saves automáticos e controle físico.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/vers%C3%A3o-0.1.0-7b5cff" alt="versão 0.1.0">
  <img src="https://img.shields.io/badge/Android-arm64-3ddc84" alt="Android arm64">
  <img src="https://img.shields.io/badge/Kotlin-Compose-7f52ff" alt="Kotlin e Compose">
</p>

---

## O que ele faz

- Biblioteca de jogos organizada por console, com capas e miniaturas dos seus saves.
- Lê ROMs dentro de arquivos `.zip`, `.7z` e pastas, sem precisar extrair.
- Save-state automático ao pausar, ao sair e a cada 60 segundos. Ao abrir o jogo de novo, ele continua de onde parou.
- Saves manuais com miniatura, lista de todos os saves e opção de excluir qualquer um deles.
- Configurações de vídeo por jogo: tamanho da imagem, shader, taxa de atualização, escala de renderização e resolução interna do N64.
- Velocidade de jogo em 1×, 2× e 4×.
- Controles na tela que somem sozinhos quando um gamepad é detectado e voltam ao tocar na tela.
- Posição e tamanho dos botões na tela podem ser editados.
- Backup dos saves para uma pasta escolhida por você, com backup automático ao voltar do emulador.

## Consoles e núcleos

| Console | Núcleo |
| --- | --- |
| PlayStation 1 | PCSX ReARMed (nativo), DuckStation (app externo) |
| PlayStation 2 | PCSX2 |
| PSP | PPSSPP |
| Nintendo 64 | Mupen64Plus-Next |
| Nintendo DS | melonDS |
| Game Boy Advance | mGBA |
| Dreamcast | Flycast |
| GameCube e Wii | Dolphin (app externo) |

Você pode ativar ou desativar cada emulador em **Configurações → Emuladores e plugins**.

## Como usar

1. Em **Configurações → Pastas e armazenamento**, adicione a pasta onde estão seus jogos.
2. Escolha um console na tela inicial e abra a biblioteca dele.
3. Toque no jogo para ver as opções: Continuar, Novo Jogo, Reiniciar Save, Configurações do Jogo ou Excluir Jogo.

Para PS2, o PCSX2 precisa da BIOS do console. Coloque os arquivos em `Android/data/com.nandox.arcade/files/system`.

## Saves e backup

Os saves ficam dentro da pasta do app, então atualizar o NDXEMULATOR não apaga nada. Desinstalar, porém, apaga essa pasta. Para se proteger, escolha um **Destino dos backups** fora do app em **Dados salvos** e ative o backup automático. Para recuperar os saves depois de reinstalar, use **Restaurar** na mesma tela.

## Controles

Funcionam controles Bluetooth e USB, como Xbox, PlayStation e controles genéricos. Os botões da tela aparecem sozinhos quando nenhum controle está conectado.

## Compilar

O projeto usa GitHub Actions. Cada push para `main` gera um APK de depuração que você baixa na aba **Actions**, no artefato `nandox-arcade-debug`.

Para compilar localmente:

```bash
bash scripts/fetch_cores.sh
./gradlew assembleDebug
```

## Créditos

Os núcleos libretro vêm do [buildbot oficial da Libretro](https://buildbot.libretro.com). A integração com o libretro usa a biblioteca [LibretroDroid](https://github.com/Swordfish90/LibretroDroid).


https://github.com/NandoXuu/NandoxArcade-Next-Advanced/actions/runs/43646258943/artifacts/14134066729
