# CityFix - estado da implementação

Este documento resume o que está funcional neste commit e o que ainda precisa de ser configurado ou desenvolvido.

## Já implementado

- Aplicação Android em Kotlin com Jetpack Compose, MVVM e navegação entre Dashboard, Ocorrências, detalhe e Perfil.
- Interface visual Bento com estatisticas, lista de ocorrencias, estados e detalhe.
- Criacao de ocorrencias com titulo, descricao, categoria, situacao, localizacao manual ou localizacao atual e anexo de imagem/video.
- Persistência local com `SharedPreferences` e Kotlin Serialization para utilizador, ocorrências e estados.
- Firebase configurado para o package `pt.isec.amov.cityfix`, incluindo:
  - Firebase Authentication;
  - Firestore;
  - Firebase Storage;
  - Firebase Cloud Messaging.
- Login por email/palavra-passe com tentativa de registo automático de uma conta nova.
- Acesso anónimo para continuar a utilizar a aplicação sem conta.
- Sincronização de ocorrências e mensagens por Firestore.
- Carregamento de anexos para o Storage e abertura do conteúdo associado no detalhe.
- Mensagens por ocorrência em tempo real.
- Token FCM guardado no perfil do utilizador e canal Android de notificações.
- Permissões de localização, câmara, notificações e sensores declaradas no manifesto.
- Regras locais de Firestore e Storage, com validacao por utilizador e proprietario da ocorrencia.
- Configuracao `firebase.json` para publicar as regras.
- Plugin Google Services aplicado diretamente no módulo `app`; o Firebase inicializa corretamente no arranque.

## Validado

- `:app:assembleDebug` passa.
- `:app:testDebugUnitTest` passa (não existem ainda testes unitários próprios relevantes).
- Aplicação instalada e executada no emulador.
- Dashboard, navegação, lista de ocorrências, detalhe e ecrãs de perfil/autenticação verificados.
- Inicialização Firebase confirmada no Logcat.
- Fluxo de acesso anónimo verificado.

## Ainda falta configurar na Consola do Firebase

1. Ativar o fornecedor **Email/Palavra-passe** em Authentication.
2. Ativar **Anónimo** se o acesso anónimo for mantido.
3. Criar/confirmar a base de dados Firestore.
4. Criar/confirmar o Storage.
5. Publicar as regras:

   ```text
   firebase deploy --only firestore:rules,storage
   ```

6. Confirmar as permissões e configuração do Cloud Messaging para notificações em dispositivos reais.

## Ainda falta implementar ou melhorar

- Mostrar claramente na interface os erros Firebase e o estado de carregamento durante login, carregamento e envio.
- Testar de ponta a ponta uma conta de email/palavra-passe depois de ativar esse fornecedor na Consola.
- Testar de ponta a ponta a criação de ocorrência com fotografia/vídeo, localização e sincronização cloud.
- Corrigir o listener de ocorrências para limpar a lista local quando a coleção Firestore fica vazia.
- Implementar notificações automáticas quando o estado de uma ocorrência muda.
- Implementar atribuição segura de operadores com Firebase Custom Claims e ferramentas administrativas.
- Completar suporte offline para mensagens e fila de carregamentos.
- Adicionar captura direta pela câmara, para além da seleção de ficheiros.
- Adicionar mapa/geofencing e uma apresentação visual da localização.
- Criar testes unitários, testes Compose/UI e testes com Firebase Emulator.
- Rever avisos de ícones Material obsoletos e melhorar acessibilidade/adaptação a landscape.
- Preparar o relatório técnico exigido pelo enunciado.

## Notas para desenvolvimento

- As ocorrências de exemplo são dados locais para demonstração.
- O acesso de operador não pode ser escolhido pelo cliente; deve ser atribuído pela administração.
- `app/google-services.json` corresponde ao projeto Firebase `cityfix-a454e`.
- As regras locais só entram em vigor no projeto remoto depois do `firebase deploy`.
