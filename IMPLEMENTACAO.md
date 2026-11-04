# CityFix - estado da implementacao

Este documento resume o que esta funcional neste commit e o que ainda precisa de ser configurado ou desenvolvido.

## Ja implementado

- Aplicacao Android em Kotlin com Jetpack Compose, MVVM e navegacao entre Dashboard, Incidents, detalhe e Profile.
- Interface visual Bento com estatisticas, lista de ocorrencias, estados e detalhe.
- Criacao de ocorrencias com titulo, descricao, categoria, situacao, localizacao manual ou localizacao atual e anexo de imagem/video.
- Persistencia local com `SharedPreferences` e Kotlin Serialization para utilizador, ocorrencias e estados.
- Firebase configurado para o package `pt.isec.amov.cityfix`, incluindo:
  - Firebase Authentication;
  - Firestore;
  - Firebase Storage;
  - Firebase Cloud Messaging.
- Login por email/password com tentativa de registo automatico de uma conta nova.
- Acesso anonimo para continuar a utilizar a aplicacao sem conta.
- Sincronizacao de ocorrencias e mensagens por Firestore.
- Upload de anexos para Storage e abertura do media associado no detalhe.
- Mensagens por ocorrencia em tempo real.
- Token FCM guardado no perfil do utilizador e canal Android de notificacoes.
- Permissoes de localizacao, camera, notificacoes e sensores declaradas no manifesto.
- Regras locais de Firestore e Storage, com validacao por utilizador e proprietario da ocorrencia.
- Configuracao `firebase.json` para publicar as regras.
- Plugin Google Services aplicado diretamente no modulo `app`; o Firebase inicializa corretamente no arranque.

## Validado

- `:app:assembleDebug` passa.
- `:app:testDebugUnitTest` passa (nao existem ainda testes unitarios proprios relevantes).
- Aplicacao instalada e executada no emulador.
- Dashboard, navegacao, lista de ocorrencias, detalhe e ecras de perfil/autenticacao verificados.
- Inicializacao Firebase confirmada no Logcat.
- Fluxo de acesso anonimo verificado.

## Ainda falta configurar no Firebase Console

1. Ativar o fornecedor **Email/Password** em Authentication.
2. Ativar **Anonymous** se o acesso anonimo for mantido.
3. Criar/confirmar a base de dados Firestore.
4. Criar/confirmar o Storage.
5. Publicar as regras:

   ```text
   firebase deploy --only firestore:rules,storage
   ```

6. Confirmar as permissoes e configuracao de Cloud Messaging para notificacoes em dispositivos reais.

## Ainda falta implementar ou melhorar

- Mostrar claramente na interface os erros Firebase e o estado de loading durante login, upload e envio.
- Testar end-to-end uma conta email/password depois de ativar esse fornecedor no Console.
- Testar end-to-end a criacao de ocorrencia com fotografia/video, localizacao e sincronizacao cloud.
- Corrigir o listener de ocorrencias para limpar a lista local quando a colecao Firestore fica vazia.
- Implementar notificacoes automaticas quando o estado de uma ocorrencia muda.
- Implementar atribuicao segura de operadores com Firebase Custom Claims e ferramentas administrativas.
- Completar suporte offline para mensagens e fila de uploads.
- Adicionar captura direta pela camera, para alem da selecao de ficheiros.
- Adicionar mapa/geofencing e uma apresentacao visual da localizacao.
- Criar testes unitarios, testes Compose/UI e testes com Firebase Emulator.
- Rever warnings de icones Material deprecated e melhorar acessibilidade/adaptacao a landscape.
- Preparar o relatorio tecnico exigido pelo enunciado.

## Notas para desenvolvimento

- Os incidentes de exemplo sao dados locais para demonstracao.
- O acesso de operador nao pode ser escolhido pelo cliente; deve ser atribuido pela administracao.
- `app/google-services.json` corresponde ao projeto Firebase `cityfix-a454e`.
- As regras locais so entram em vigor no projeto remoto depois do `firebase deploy`.
