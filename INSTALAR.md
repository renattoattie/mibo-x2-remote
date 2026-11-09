# Mibo Local sem ADB — versão de teste 0.2

Esta edição é separada do controle ADB que já funciona. Ela usa dois aplicativos com nomes e identificadores diferentes, permitindo manter a versão anterior como alternativa.

## 1. Instalar o receptor no Mibo

1. Copie **Mibo-Receptor-Local-v0.2.apk** para um pendrive.
2. Conecte o pendrive no Mibo. Use o mouse USB para abrir o gerenciador de arquivos e executar o APK. Se o Android solicitar, autorize a instalação por esse gerenciador de arquivos.
3. Abra **Mibo Receptor Local**.
4. Toque em **Abrir Acessibilidade**. No menu mostrado na sua TV, procure **Serviços → Mibo Receptor Local** e ative-o após ler a permissão.
5. Volte ao receptor. Ele deve mostrar **Recepção ativa** e o **IP do Mibo**.
6. Toque em **Gerar código**. O código tem 12 caracteres, agrupados para facilitar a leitura, e vale por cinco minutos.

O serviço de Acessibilidade consulta localmente os elementos da tela, move o foco, aciona itens, executa gestos e ações como Início e Voltar. Textos e imagens da tela não são transmitidos ao celular.

## 2. Instalar o controle no Xiaomi

1. Transfira **Mibo-Controle-Local-v0.2.apk** para o celular e instale.
2. Abra **Mibo Controle Local** (não o controle ADB antigo).
3. Conecte o celular ao Wi-Fi da mesma rede do Mibo. O Mibo pode ficar no cabo.
4. Digite o IP e o código mostrados pelo receptor. Espaços no código são opcionais.
5. Toque em **Parear**. Depois disso, o celular salva a autorização e basta usar **Conectar** nas próximas vezes.

Ao sair do aplicativo do celular, a conexão é encerrada. Ao voltar, toque em Conectar. Não há autorização de depuração neste fluxo.

## 3. Testar antes de aposentar o controle antigo

- Teste **Início**, **Voltar** e **volume**.
- Abra cada aplicativo que costuma usar e teste as setas e **OK**.
- Se uma tela não responder às setas, ative **Modo ponteiro** no celular. As setas movem uma mira na TV; **Tocar** envia um toque naquele ponto.
- Teste também o app após fechar/reabrir o controle e após reiniciar o Mibo. O firmware pode gerenciar serviços em segundo plano de maneira diferente.

**Limitações do Android 10:** o modo de setas usa foco e ações de Acessibilidade, não a injeção de teclas de um controle físico. Telas desenhadas de modo próprio podem não expor seus elementos. O ponteiro usa gestos de toque e também depende da compatibilidade do aplicativo. Volume atua sobre o áudio do Android; o volume próprio da TV pode ser separado. Recentes e Reproduzir/Pausar podem não ser tratados pelo firmware ou pelo app aberto.

Uma confirmação de comando significa que a ação foi aceita/enviada pela API; não garante que o aplicativo tenha mostrado o resultado desejado. O tempo em ms inclui a espera na fila e a resposta do receptor.

## 4. Desligar a depuração

Depois de verificar os comandos essenciais, desligue **Depuração USB** no Mibo e repita o teste com o **Mibo Controle Local**. Essa edição não depende de ADB nem das opções do desenvolvedor.

O controle antigo deixará de funcionar enquanto a depuração estiver desligada. Se precisar voltar a ele, reative a depuração e use seu APK anterior. Os novos aplicativos não modificam essa configuração automaticamente.

## Segurança e privacidade

- O receptor escuta na porta local **8765**, apenas com o serviço de Acessibilidade ativo e a recepção habilitada. Não abra essa porta para a internet.
- Pareamento com código aleatório temporário de 60 bits (12 caracteres) e desafio/resposta HMAC-SHA256; após parear, o celular usa um token aleatório de 256 bits.
- Sessões derivam chaves novas com nonces aleatórios e usam AES-GCM, com contadores para rejeitar repetição e alteração de mensagens. O código não é enviado em texto aberto.
- Este protocolo é específico do projeto, usa primitivas criptográficas da plataforma e foi testado localmente; não passou por auditoria criptográfica independente. Use somente em rede confiável.
- O código é invalidado após o pareamento. Cinco falhas de autenticação causam um bloqueio temporário de um minuto. Gerar novo código na TV reinicia essa espera.
- Até 16 celulares cadastrados e 8 conexões simultâneas. Cada pareamento adiciona uma autorização independente. **Celulares autorizados** permite remover apenas o celular escolhido; **Pausar recepção** fecha todas as conexões.
- Não há conta, anúncios, analytics ou servidor externo. As identidades ficam no armazenamento privado dos apps, com backup automático desativado.
- O receptor aceita somente os comandos definidos no código; não oferece execução de shell, upload de arquivos ou captura da tela.

## Resultado da validação

Os dois APKs foram compilados e tiveram suas assinaturas verificadas. Os testes locais cobrem pareamento, autorização salva, rejeição de credencial incorreta, repetição e adulteração de mensagens, sequência de comandos e geometria de foco.

**Ainda precisa de validação física no Mibo:** ativação do serviço, permanência após reinício, navegação nos aplicativos e comportamento dos gestos. Esta é uma versão de teste, não uma garantia de substituição completa do controle físico.

## Código e compilação

Os diretórios `common`, `receptor`, `controle` e `tests` incluem o código-fonte. `build.ps1` usa JDK 17, Android SDK Platform 35 e Build Tools 35.0.0. Informe `-Jdk`, `-BuildTools`, `-AndroidJar`, `-WorkDir` e `-SigningDir` com caminhos locais. Mantenha WorkDir e SigningDir fora de qualquer publicação; preserve sua chave de assinatura para atualizações.

## Atualizar e adicionar celulares

Instale os APKs 0.2 por cima dos anteriores, sem desinstalar nem apagar dados. Atualize primeiro o receptor e depois o controle em cada celular. A autorização antiga é preservada como **Celular anterior**; o controle 0.1 já pareado continua compatível, mas novos pareamentos devem usar o controle 0.2.

Gere um novo código na TV para cada celular. O código só pode ser usado uma vez. No celular, ao conectar, aparece um ID de oito caracteres; o mesmo ID aparece em **Celulares autorizados** na TV. Para remover um, selecione esse ID e confirme. Isso encerra suas conexões e impede reconexão, sem remover os demais. Para autorizar novamente, faça novo pareamento.

Os comandos de todos os celulares controlam a mesma tela e são processados em sequência. Ao atingir oito conexões, feche um controle antes de conectar outro. Ao atingir 16 cadastros, remova um antes de gerar novo pareamento. Reinstalar o controle sem preservar seus dados exige novo pareamento.
