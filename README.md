# CityFix

Aplicação Android para comunicação e acompanhamento de ocorrências no espaço público. O cidadão pode consultar problemas da comunidade, registar ocorrências com localização e fotografia/vídeo, acompanhar o estado da resolução e trocar mensagens com os serviços municipais. Os operadores municipais gerem o ciclo de vida de cada ticket, desde a abertura até ao encerramento.

## Tecnologias

- Kotlin e Jetpack Compose (Material 3)
- Arquitetura MVVM (ViewModels com `StateFlow`, `ViewModelProvider.Factory` e repositórios criados no objeto `Application`)
- Navegação type-safe (Navigation Compose 2.8+, rotas `@Serializable`)
- Mapas OpenStreetMap (osmdroid)
- Firebase Authentication
- Cloud Firestore (com cache offline nativa)
- Firebase Storage
- Firebase Cloud Messaging
- WorkManager (envio de multimédia e tarefas periódicas em segundo plano)
- Coil 3 (carregamento de imagens)
- Persistência local com Jetpack DataStore (Preferences), com migração automática dos antigos `SharedPreferences`, e Kotlin Serialization
- Sensores Android (acelerómetro) e osmdroid (mapas OpenStreetMap)
- Google Play Services Location

## Funcionalidades implementadas

### Perfis e autenticação
- Registo de munícipe (pendente até validação do NIF por um operador) e de operador (email do domínio municipal, ativo de imediato)
- Validação de NIF (algoritmo e unicidade), telefone e email
- Autenticação por email/palavra-passe e acesso anónimo
- Recuperação da palavra-passe por email ("Esqueci-me da palavra-passe") e email de verificação enviado após o registo
- Subscrição de tópicos FCM por perfil (`cityfix`, `operadores`, `municipes`), anulada no logout
- Edição de perfil com fotografia comprimida localmente
- Validação de munícipes e gestão de categorias pelo operador

### Ocorrências
- Dashboard com total, % de ocorrências ativas e tempo médio de reparação
- Registo com título, descrição, categoria, situação, localização, alcance público/privado e anexo obrigatório (foto ou vídeo, através do seletor de fotos do sistema)
- Ocorrências de grupo: uma ocorrência igual a menos de 10 m adiciona o utilizador como apoiante
- Lista com miniaturas e detalhe com pré-visualização de imagem/vídeo
- Mensagens em tempo real entre munícipe e serviços camarários
- Deteção automática de impactos (opção do perfil do munícipe): o `ImpactDetector` filtra a gravidade do acelerómetro e calcula a aceleração vertical; acima de 8 m/s² propõe reportar a ocorrência com o formulário pré-preenchido (Vias Públicas / Dano) e a localização GPS. O sensor só está registado com a app visível (`DisposableEffect` + `LifecycleEventObserver`, padrão da aula de sensores)

### Multimédia
- Compressão local das imagens (correção de orientação EXIF, máx. 1280 px, JPEG) e geração de miniaturas (256 px)
- Vídeos copiados para armazenamento interno, com miniatura gerada a partir do primeiro frame
- Envio para o Firebase Storage delegado no `MediaUploadWorker` (WorkManager), condicionado à existência de rede e com novas tentativas automáticas
- A ocorrência é gravada de imediato no Firestore, mesmo offline; a multimédia é associada quando o envio termina

### Ciclo de vida do ticket
- Estados: Aberto → Pedido de Informações → Em Resolução → Resolvido → Encerrado
- Histórico completo de transições, com data, autor e nota
- Pedido de informações nos dois sentidos (operador → munícipe e munícipe → autarquia); a resposta da outra parte nas mensagens repõe o estado anterior
- Registo do início dos trabalhos no terreno
- Resolução apenas com foto/vídeo comprovativo, carregado pelo operador
- Reabertura pelo autor até 5 dias após a resolução, com justificação obrigatória
- Encerramento automático após 5 dias (`TicketLifecycleWorker`, com transação para evitar duplicados); um ticket encerrado não admite alterações nem mensagens

### Notificações
- Notificações locais de mudança de estado, novas mensagens e novas ocorrências (para operadores)
- Aviso ao munícipe quando faltam 24 horas para o fecho definitivo do ticket
- Geradas pelo `TicketNotifier`, tanto com a app aberta (listener do Firestore) como em segundo plano (worker periódico de 15 minutos)
- Tocar numa notificação abre o ticket correspondente

## Arquitetura (alinhada com as aulas práticas P07–P10)

- **Pacotes por camada**: `model` (Kotlin puro), `data` (repositórios, workers, notificações), `services`, `viewmodel`, `utils`, `ui.navigation`, `ui.components`, `ui.screens`, `ui.theme`.
- **Domínio**: `IncidentList` concentra as regras de negócio (ocorrências de grupo, estatísticas, reabertura) e ordena as categorias com `Collator`.
- **Navegação**: rotas `@Serializable` em `Destinations.kt`, `NavHost` com `toRoute<>()`, TopAppBar contextual e botão "voltar" do sistema funcional.
- **ViewModels**: partilhados (`AuthViewModel`, `IncidentViewModel`, `OperatorViewModel`) e com âmbito no ecrã (`ReportIncidentViewModel`, `AuthFormViewModel`, `EditProfileViewModel`), estes com `UiState` imutável, validação em tempo real por campo e `isValid` calculado.
- **Ecrãs sem estado**: recebem dados e emitem eventos por lambdas; `@Preview` nos principais.
- **Ocorrências**: até 3 anexos (`PickMultipleVisualMedia` ou câmara com `TakePicture` e `FileProvider`), galeria `LazyVerticalStaggeredGrid`, categorias em `ExposedDropdownMenuBox`, local escolhido no mapa ou por GPS.
- **Layout adaptativo**: em landscape, o detalhe e a nova ocorrência dividem o ecrã 50/50 com o mapa.
- **Geofence de 20 m**: `ProximityService` em primeiro plano (padrão de P10), com `requestLocationUpdates` e `Location.distanceBetween`; os autores são notificados quando um operador está junto da ocorrência. A localização em segundo plano é pedida de forma faseada, depois de um diálogo que explica a necessidade.
- **Persistência local**: instância única de DataStore, leituras como `Flow` com tratamento de `IOException` e escritas `suspend` (aula de Storage).
- **Trabalho em segundo plano**: `CoroutineWorker` para o envio de multimédia e para o ciclo de vida dos tickets.
- **Regras de segurança**: `firestore.rules` e `storage.rules` (cada utilizador só escreve em `users/{uid}/`; anexos de tickets limitados a imagem/vídeo e a 20 MB), seguindo a P11.
- **Internacionalização**: inglês por omissão (`values`) e português (`values-pt`), com plurais e `locales_config` (idioma escolhido por app em Definições, Android 13+). Os ecrãs usam `stringResource`; os ViewModels recebem uma interface `Strings` injetada pelas factories, para não dependerem de `Context`.
- **Acessibilidade**: descrições em todos os ícones e imagens, interruptores com rótulo anunciado pelo leitor de ecrã (`LabeledSwitch` com `toggleable`), alvos de toque de pelo menos 48 dp e cartões com etiqueta de ação.
- **MFA**: código de 6 dígitos enviado por notificação local após a palavra-passe (simulação do 2.º fator). Se a app for fechada antes de o código ser confirmado, a sessão é terminada no arranque seguinte (estado guardado no DataStore).
- **Testes unitários** (`app/src/test`): regras de `IncidentList` (visibilidade, agrupamento a 10 m, estatísticas, reabertura, ordenação), validadores (NIF, telefone, email, perímetro de 10 km) e `ImpactDetector`.

## Estado atual

O projeto compila, passa o lint e os 21 testes unitários, e foi testado no emulador com contas de operador e de munícipe: ciclo completo do ticket, envio de multimédia para o Firebase Storage (plano Blaze), notificações FCM enviadas pela consola, deteção de impactos, recuperação de palavra-passe, MFA e mudança de idioma entre inglês e português.

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
4. Storage criado (necessário para fotos e vídeos);
5. Cloud Messaging configurado para os dispositivos pretendidos.

As regras do Firestore (`firestore.rules`) estão alinhadas com as coleções `users`, `categories` e `tickets`. Para as publicar:

```text
firebase deploy --only firestore:rules,storage
```

## Trabalho ainda pendente

- Notificações push enviadas pelo servidor (Cloud Functions); as de mudança de estado são geradas localmente.
- MFA real por SMS (Firebase Identity Platform); atualmente é simulado por notificação local.
- Testes de UI (Compose).
- Relatório técnico em PDF.

## Estrutura principal

```text
app/src/main/java/pt/isec/amov/cityfix/
├── data/
│   ├── cloud/          # Firebase (Auth, Firestore, Storage) e serviço FCM
│   ├── local/          # Persistência local
│   ├── notifications/  # TicketNotifier: notificações locais
│   ├── work/           # MediaUploadWorker e TicketLifecycleWorker (WorkManager)
│   ├── MediaRepository.kt
│   └── CityFixApp.kt   # Application: repositórios (by lazy), canal e workers
├── model/              # Entidades e IncidentList (regras de negócio)
├── services/           # ProximityService (geofence de 20 m)
├── ui/
│   ├── components/     # IncidentCard, galeria, mapa, dropdown de categorias
│   ├── navigation/     # Destinations.kt (rotas type-safe)
│   ├── screens/        # Ecrãs Compose sem estado
│   └── theme/          # Tema visual
├── utils/              # Validações, LocationTracker e multimédia
├── viewmodel/          # ViewModels, UiStates e factories
└── MainActivity.kt     # NavHost, TopAppBar contextual e ligação aos ViewModels
```
