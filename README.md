# CityFix

Aplicação Android para comunicação e acompanhamento de ocorrências no espaço público. O cidadão pode consultar problemas da comunidade, registar ocorrências com localização e fotografia/vídeo, acompanhar o estado da resolução e trocar mensagens com os serviços municipais.

## Tecnologias

- Kotlin e Jetpack Compose
- Arquitetura MVVM
- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- Firebase Cloud Messaging
- Persistência local com `SharedPreferences` e Kotlin Serialization
- Google Play Services Location

## Funcionalidades implementadas

- Dashboard com estatísticas e ocorrências abertas
- Lista e detalhe de ocorrências
- Registo de ocorrências com:
  - título, descrição, categoria e situação;
  - localização manual ou localização atual;
  - ocorrência pública ou privada;
  - fotografia ou vídeo anexado
- Abertura da localização no mapa através das coordenadas
- Abertura de anexos associados à ocorrência
- Mensagens em tempo real por ocorrência
- Persistência local para utilização sem ligação
- Autenticação Firebase por email/palavra-passe
- Acesso anónimo
- Sincronização com Firestore
- Upload de anexos para o Firebase Storage
- Preparação de notificações FCM
- Regras de segurança para Firestore e Storage
- Interface e documentação em português

## Estado atual

O projeto compila e foi executado no emulador. Foram validados o arranque, o dashboard, a navegação, a lista e o detalhe de ocorrências, o perfil, o ecrã de autenticação, o acesso anónimo e a inicialização do Firebase.

Para consultar a lista completa do que está implementado e do que falta, ver [`IMPLEMENTACAO.md`](IMPLEMENTACAO.md).

## Como executar

1. Abrir o projeto no Android Studio.
2. Confirmar que `app/google-services.json` corresponde ao projeto Firebase configurado.
3. Sincronizar o Gradle.
4. Executar a configuração `app` num emulador ou dispositivo Android.

Também é possível validar através do terminal:

```text
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

## Configuração Firebase necessária

Na Consola do Firebase, confirmar:

1. Authentication com Email/Palavra-passe ativado;
2. Authentication Anónimo ativado, se o acesso anónimo for utilizado;
3. Firestore criado;
4. Storage criado;
5. Cloud Messaging configurado para os dispositivos pretendidos.

Para publicar as regras locais:

```text
firebase deploy --only firestore:rules,storage
```

## Trabalho ainda pendente

- Testar completamente o registo/login por email depois de ativar o fornecedor na Consola do Firebase.
- Testar a criação de ocorrências com anexos e sincronização cloud de ponta a ponta.
- Implementar notificações automáticas quando o estado de uma ocorrência muda.
- Implementar gestão segura de operadores com Firebase Custom Claims.
- Completar a fila de carregamentos e mensagens offline.
- Adicionar captura direta pela câmara, mapa integrado e geofencing.
- Criar testes unitários, testes Compose/UI e testes com Firebase Emulator.
- Preparar o relatório técnico do projeto.

## Estrutura principal

```text
app/src/main/java/pt/isec/amov/cityfix/
├── data/
│   ├── cloud/       # Firebase, Storage e notificações
│   └── local/       # Persistência local
├── model/           # Modelos de utilizador, ocorrência e mensagens
├── ui/theme/        # Tema visual
├── viewmodel/       # Estado e regras da aplicação
└── MainActivity.kt  # Navegação e ecrãs Compose
```
