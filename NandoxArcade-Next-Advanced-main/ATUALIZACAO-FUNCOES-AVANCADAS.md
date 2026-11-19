# Nandox Arcade — funções avançadas

## Implementado nesta atualização

- **Editor de controles na tela:** no menu da partida, use “Editar posição dos botões”. Arraste cada área para a posição desejada e toque em “Arraste os controles · Concluir”. Posições ficam salvas por botão e são reutilizadas nas próximas partidas.
- **Save-states globais:** a opção “Saves” da tela inicial lista estados de todos os jogos, com miniatura, título e data. É possível excluir estados ou abrir o jogo associado e carregar aquele estado.
- **Histórico de partidas:** a biblioteca exibe a última captura do jogo e a data da última sessão. A captura é atualizada ao sair/pausar o emulador.
- **N64 — resolução interna real do Mupen64Plus-Next:** opções 4:3 até 3840x2880 e 16:9 até 3840x2160, configuradas através das variáveis oficiais do core (`mupen64plus-43screensize`, `mupen64plus-169screensize`, `mupen64plus-aspect`). A alteração é aplicada ao reiniciar o jogo.
- **Alvo de atualização de tela:** o app solicita ao Android 30/60/90/120 Hz ou automático através de `preferredRefreshRate`. O Android pode ignorar a solicitação; isso não é um limitador rígido de FPS do core.
- **Escala da superfície de vídeo:** 1x, 1,5x, 2x, 3x e 4x. É uma escala do buffer de saída do frontend, não altera por si só a resolução interna de todos os emuladores.

## Limitações técnicas importantes

A API pública do LibretroDroid 0.13.2 disponibiliza variáveis de core antes do carregamento. Por isso, a resolução interna foi conectada diretamente ao Mupen64Plus-Next (N64), que oferece opções oficiais de resolução. Os outros cores não expõem uma opção universal de resolução interna através desta integração.

O seletor 30/60/90/120 Hz é uma solicitação de taxa de atualização do display Android, não uma garantia de FPS. Para limitar o `retro_run()` em qualquer core de forma rígida, seria necessário ampliar o loop de sincronização nativo do LibretroDroid.

A escala 1x–4x aumenta o tamanho da superfície de vídeo. Valores altos consomem mais memória e GPU e podem causar travamentos em aparelhos limitados. A resolução 4K real do N64 é uma opção do core e depende do suporte da GPU/dispositivo.

## Teste recomendado

1. Abra um jogo N64 e configure 1920x1440 ou 3840x2880 em 4:3.
2. Reinicie o jogo para aplicar a variável do core.
3. Abra o menu e teste “Editar posição dos botões”.
4. Salve um estado, saia do jogo e confira a tela inicial em “Saves”.
5. Volte à biblioteca e confirme a miniatura e a data da última partida.

## Build

No Termux, dentro da pasta do projeto:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

A compilação precisa ser validada no Android/Termux com o Gradle e SDK instalados. O ambiente de edição não conseguiu baixar a distribuição Gradle por falta de acesso à rede.
