# Atualização da interface e leitura de ROMs compactadas

## Incluído nesta revisão

- Leitura de ROMs dentro de arquivos `.zip` e `.7z` ao escanear uma pasta SAF.
- Cada ROM compatível encontrada dentro do arquivo aparece como um jogo separado na biblioteca.
- A ROM é extraída para o cache privado somente quando o jogo é iniciado; o arquivo original não é alterado.
- O menu durante a partida permite selecionar proporção Original, 4:3, 16:9, 3:2 ou Esticado/tela cheia.
- O tamanho dos controles virtuais pode ser ajustado e fica salvo entre sessões.
- O menu de pausa permite salvar um estado manualmente, listar os estados do jogo atual, carregar um deles e excluir qualquer slot.
- Cada save-state tenta gerar uma miniatura PNG da imagem do jogo usando PixelCopy; se a captura falhar, o save continua disponível sem miniatura.
- Ao sair pelo menu do emulador, o app cria um save-state automático antes de fechar.
- O menu permite ligar/desligar áudio e escolher shader Padrão, Nítido, CRT ou LCD.
- A paleta Material padrão foi ajustada para cinza/azul claro com maior contraste.
- A atividade do emulador mantém o modo imersivo existente, escondendo as barras de status e navegação do Android.

## Limitações que ainda precisam de implementação

- A lista de save-states está disponível no menu durante a partida; a tela global de gerenciamento de todos os jogos ainda não foi integrada ao gerenciador geral de saves.
- Miniatura de último jogo na biblioteca e histórico de última partida ainda não foram implementados.
- O controle de FPS por limite fixo e a resolução interna 4K dependem de opções expostas pelo core Libretro. O seletor de proporção altera o enquadramento/escala da imagem, não aumenta a resolução interna do emulador.
- Os controles podem ser redimensionados, mas o editor de arrastar e reposicionar cada botão ainda não foi implementado.
- A disponibilidade e qualidade de save-states dependem de cada core Libretro; alguns cores podem retornar estado vazio ou falhar ao restaurar.

## Dependências adicionadas

- Apache Commons Compress 1.27.1
- XZ for Java 1.10 (suporte ao formato 7z)

## Compilação

No Termux, dentro da pasta do projeto:

```bash
./gradlew :app:assembleDebug
```

A primeira compilação precisa de conexão com a internet para baixar Gradle e dependências. Esta revisão foi inspecionada estaticamente, mas a compilação não pôde ser executada neste ambiente sem acesso ao host `services.gradle.org`.
