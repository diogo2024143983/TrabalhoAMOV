# CityFix

Aplicação Android para comunicação e acompanhamento de ocorrências no espaço público. O cidadão pode consultar problemas da comunidade, registar ocorrências com localização e fotografia/vídeo, acompanhar o estado da resolução e trocar mensagens com os serviços municipais. Os operadores municipais gerem o ciclo de vida de cada ticket, desde a abertura até ao encerramento.

## Tecnologias

- Kotlin e Jetpack Compose (Material 3)
- Arquitetura MVVM (ViewModels com `StateFlow`)
- Firebase Authentication
- Cloud Firestore (com cache offline nativa)
- Firebase Storage
- Firebase Cloud Messaging
- WorkManager (envio de multimédia e tarefas periódicas em segundo plano)
- Coil 3 (carregamento de imagens)
- Persistência local com `SharedPreferences` e Kotlin Serialization
- Google Play Services Location

## Funcionalidades implementadas

### Perfis e autenticação
- Registo de munícipe (pendente até validação do NIF por um operador) e de operador (email do domínio municipal, ativo de imediato)
- Validação de NIF (algoritmo e unicidade), telefone e email
- Autenticação por email/palavra-passe e acesso anónimo
- Edição de perfil com fotografia comprimida localmente
- Validação de munícipes e gestão de categorias pelo operador

### Ocorrências
- Dashboard com total, % de ocorrências ativas e tempo médio de reparação
- Registo com título, descrição, categoria, situação, localização, alcance público/privado e anexo obrigatório (foto ou vídeo, através do seletor de fotos do sistema)
- Ocorrências de grupo: uma ocorrência igual a menos de 10 m adiciona o utilizador como apoiante
- Lista com miniaturas e detalhe com pré-visualização de imagem/vídeo
- Mensagens em tempo real entre munícipe e serviços camarários

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

## Estado atual

O projeto compila e foi testado no emulador com uma conta de operador. Foi percorrido o ciclo completo: criação da ocorrência com fotografia, pedido de informações, início da resolução, registo do início dos trabalhos, resolução com prova, reabertura e histórico.

**Limitação conhecida:** o Firebase Storage não está ativo no projeto Firebase (`cityfix-a454e`), pelo que o envio de multimédia devolve 404. O `MediaUploadWorker` volta a tentar automaticamente, e a ocorrência continua a funcionar sem a multimédia. Para resolver, é preciso ativar o Storage na Consola do Firebase, o que exige o plano Blaze.

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

- Ativar o Firebase Storage e validar o envio de multimédia de ponta a ponta.
- Notificações push remotas (exigem Cloud Functions ou servidor); atualmente são locais.
- Geofence de 20 m: alertar os autores quando um operador se aproxima de uma ocorrência ativa (requer localização em segundo plano).
- Deteção de impactos com o acelerómetro.
- Restrição do munícipe anónimo a 10 km da Praça da República e ocultação de dados no detalhe.
- Mapa integrado (Google Maps Compose) em vez da app de mapas externa.
- Autenticação com duplo fator (MFA) e recuperação de palavra-passe por email.
- Edição e ativação/desativação de categorias existentes.
- Internacionalização: inglês por omissão e português como língua adicional (os textos ainda estão no código).
- Revisão de orientação landscape e acessibilidade.
- Testes unitários e de UI.
- Relatório técnico em PDF.

## Estrutura principal

```text
app/src/main/java/pt/isec/amov/cityfix/
├── data/
│   ├── cloud/          # Firebase (Auth, Firestore, Storage) e serviço FCM
│   ├── local/          # Persistência local
│   ├── notifications/  # TicketNotifier: notificações locais
│   ├── work/           # MediaUploadWorker e TicketLifecycleWorker (WorkManager)
│   └── CityFixApp.kt   # Application: canal de notificações e agendamento de workers
├── model/              # Utilizador, ocorrência, histórico e mensagens
├── ui/
│   ├── screens/        # Ecrãs Compose
│   └── theme/          # Tema visual
├── utils/              # Validações, distâncias e preparação de multimédia
├── viewmodel/          # Estado e regras da aplicação
└── MainActivity.kt     # Navegação e ligação entre ecrãs e ViewModels
```
