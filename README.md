# Spring AI Playground

Uma aplicação web interativa para explorar as capacidades do [Spring AI](https://docs.spring.io/spring-ai/reference/), com uma interface de chat com streaming e um sistema completo de gerenciamento de documentos via RAG (Retrieval-Augmented Generation).

---

## Índice

- [Visão Geral](#visão-geral)
- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Como Executar](#como-executar)
- [Configuração](#configuração)
- [Backends de Vector Store](#backends-de-vector-store)
- [Segurança e Usuários](#segurança-e-usuários)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Endpoints da API](#endpoints-da-api)

---

## Visão Geral

O Spring AI Playground é um projeto de laboratório criado para experimentar os recursos centrais do Spring AI em um ambiente realista e próximo de produção. Ele expõe dois módulos principais:

- **Chat** — interface conversacional baseada em modelos OpenAI, com respostas em streaming, temperatura configurável, histórico de conversa persistente e enriquecimento opcional via RAG.
- **Gerenciamento RAG** — painel de gerenciamento de documentos para ingestão, busca e exclusão de documentos em múltiplos backends de vector store, com controle de acesso por papel por store.

---

## Funcionalidades

### Chat
- Respostas em streaming em tempo real via Server-Sent Events (SSE)
- Seleção de modelo (GPT-4o Mini, GPT-4o, GPT-4 Turbo)
- Temperatura configurável (0.0 – 1.0)
- Histórico de conversa persistente no Redis
- Enriquecimento RAG opcional: selecione qualquer vector store acessível para enriquecer as respostas com documentos indexados
- Renderização de Markdown na interface
- Limpeza de conversa

### Gerenciamento RAG
- **Upload de arquivos** — PDF, Markdown, texto simples (até 256 MB)
- **Importação do GitHub** — clone de repositório com indexação de arquivos Markdown e filtro por pasta
- **Importação do Confluence** — importação de espaço completo ou páginas individuais via Confluence REST API v2
- **Importação do Monday.com** — importação de itens de um quadro
- **Importação do AWS S3** — importação em massa de um bucket S3 com filtro por prefixo e formato
- **Busca semântica** — consulta ao vector store com `topK` e limiar de similaridade configuráveis
- **Exclusão de documentos** — remoção de documentos individuais por ID
- Controle de acesso por papel: cada usuário vê e gerencia apenas os stores aos quais tem acesso

---

## Tecnologias

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 |
| IA | Spring AI 1.1.3 |
| Web | Spring WebFlux (reativo) |
| Templates | Thymeleaf |
| Frontend | HTMX, Tailwind CSS, JavaScript puro |
| Segurança | Spring Security (WebFlux) |
| Vector Stores | SimpleVectorStore, pgvector, Redis Search |
| Memória de Chat | Redis |
| Banco de Dados | PostgreSQL 16 (pgvector) |
| Parsing de Documentos | Apache Tika, PDFBox, Spring AI Markdown Reader |
| Integração GitHub | JGit |
| Confluence | Confluence REST API v2 + jsoup |
| AWS | AWS SDK para Java v2 (S3) |
| Build | Maven (wrapper incluído) |
| Contêineres | Docker Compose (gerenciado automaticamente) |

---

## Arquitetura

### Modelo Reativo

Toda a aplicação é reativa. Os controllers retornam `Mono<String>` ou `Flux<T>`, e toda I/O bloqueante (arquivos, clonagem Git, chamadas HTTP externas) é delegada ao `Schedulers.boundedElastic()`. As respostas do chat são transmitidas token a token via SSE.

### Padrão Registry

Três registries centrais coordenam as principais abstrações:

- **`VectorStoreRegistry`** — mapeia IDs de store (`simple`, `pgvector`, `redis`) para beans Spring do tipo `VectorStore`.
- **`DocumentRegistry`** — rastreia documentos ingeridos em memória (fonte, preview, timestamp) por store.
- **`ProviderRegistry`** — mantém a lista estática de provedores e modelos de IA disponíveis.

### Fluxo do Chat

```
Usuário envia mensagem
      │
      ▼
ChatController GET /chat/stream (SSE)
      │
      ▼
ChatService.stream()
      │
      ├── MessageChatMemoryAdvisor  ← janela de 20 mensagens do Redis
      ├── QuestionAnswerAdvisor     ← enriquecimento RAG (opcional, se storeId fornecido)
      │
      ▼
ChatClient (Spring AI) → OpenAI API → Flux<String> → tokens SSE
```

### Fluxo de Ingestão RAG

```
Fonte (arquivo / GitHub / Confluence / Monday / S3)
      │
      ▼
DocumentParserService  (PDF / Markdown / TXT / Tika)
      │
      ▼
TokenTextSplitter  (divisão em chunks)
      │
      ▼
VectorStore.add()  →  Embedding API  →  Vetores armazenados
      │
      ▼
DocumentRegistry.register()
```

---

## Pré-requisitos

| Requisito | Versão |
| --- | --- |
| Java | 21+ |
| Maven | 3.9+ (ou use `./mvnw`) |
| Docker | Última versão (para PostgreSQL + Redis) |
| Chave de API OpenAI | Obrigatória |

> O Docker Compose é gerenciado automaticamente pelo Spring Boot. Os contêineres `pgvector` e `redis-stack` são iniciados junto com a aplicação.

---

## Como Executar

### 1. Clonar o repositório

```bash
git clone https://github.com/tonyblack10/aiplayground.git
cd aiplayground
```

### 2. Definir a variável de ambiente obrigatória

```bash
export OPENAI_API_KEY=sk-...
```

No Windows (PowerShell):

```powershell
$env:OPENAI_API_KEY = "sk-..."
```

### 3. Executar a aplicação

```bash
./mvnw spring-boot:run
```

Isso irá:
1. Iniciar os contêineres PostgreSQL (pgvector) e Redis via Docker Compose
2. Inicializar o schema do vector store automaticamente
3. Pré-carregar os documentos de exemplo de `docs/faq.txt` no SimpleVectorStore
4. Iniciar a aplicação em **http://localhost:8080**

### 4. Fazer login

Acesse [http://localhost:8080](http://localhost:8080) e faça login com uma das contas de teste (veja [Segurança e Usuários](#segurança-e-usuários)).

### Build (sem executar)

```bash
./mvnw clean package
```

### Executar testes

```bash
./mvnw test

# Classe ou método específico
./mvnw test -Dtest=NomeDaClasse#nomeDoMetodo
```

---

## Configuração

Toda a configuração está em `src/main/resources/application.yaml`. A tabela abaixo lista as propriedades relevantes.

### Variáveis de Ambiente

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `OPENAI_API_KEY` | Sim | Chave de API da OpenAI |
| `GITHUB_TOKEN` | Não | Token de acesso pessoal do GitHub para repositórios privados |
| `CONFLUENCE_BASE_URL` | Não | URL base da instância do Confluence |
| `CONFLUENCE_EMAIL` | Não | E-mail da conta do Confluence |
| `CONFLUENCE_API_TOKEN` | Não | Token de API do Confluence |
| `MONDAY_API_TOKEN` | Não | Token de API do Monday.com |
| `AWS_ACCESS_KEY_ID` | Não | Chave de acesso AWS para S3 |
| `AWS_SECRET_ACCESS_KEY` | Não | Chave secreta AWS para S3 |
| `AWS_REGION` | Não | Região AWS (padrão: `us-east-1`) |
| `AWS_S3_ENDPOINT` | Não | Endpoint S3 customizado (ex.: MinIO) |
| `APP_FRONTEND_BASE_URL` | Não | URL base quando atrás de um reverse proxy |

### Propriedades da Aplicação (destaques)

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-4o-mini   # Modelo padrão

  webflux:
    multipart:
      max-file-size: 256MB
      max-request-size: 256MB

app:
  github:
    clone-dir: /tmp/github-clones
```

---

## Backends de Vector Store

Três backends são configurados e ficam disponíveis simultaneamente.

| ID | Tipo | Persistência | Observações |
| --- | --- | --- |---|
| `simple` | Em memória | `vectorstore.json` (arquivo) | Pré-carregado com documentos de FAQ |
| `pgvector` | PostgreSQL | Persistente | Índice HNSW, distância cosseno, 1536 dimensões |
| `redis` | Redis Search | Persistente enquanto o Redis está ativo | Estratégia TokenCountBatching |

Os três stores são iniciados automaticamente via Docker Compose. Os usuários podem alternar entre eles na barra lateral do chat ou no painel de gerenciamento RAG, respeitando suas permissões de acesso.

---

## Segurança e Usuários

A aplicação utiliza Spring Security com login por formulário. Três contas de teste são fornecidas por padrão:

| Usuário | Senha | Stores Acessíveis |
| --- | --- | --- |
| `user01` | `pass01` | Todos os stores (Simple, pgvector, Redis) |
| `user02` | `pass02` | Simple, pgvector |
| `user03` | `pass03` | Apenas Redis |

O controle de acesso é aplicado no nível do controller via anotação `@RequiresRagAccess`, que delega para `RagAuthorityHelper` a verificação das autoridades do usuário em relação ao store solicitado.

> **Atenção:** A proteção CSRF está desativada na configuração atual. Isso é aceitável em ambiente de laboratório local, mas deve ser habilitada antes de qualquer implantação em produção.

Para adicionar ou alterar usuários, edite as seções `app.user.accounts` e `app.user.permissions` no `application.yaml`.

---

## Estrutura do Projeto

```
src/main/java/io/tonyblack10/aiplayground/
├── AiplaygroundApplication.java
├── chat/
│   ├── service/
│   │   ├── ChatService.java            # Orquestração Spring AI, streaming
│   │   └── RedisChatMemory.java        # Memória de conversa no Redis
│   └── web/
│       └── ChatController.java         # Endpoints /chat + stream SSE
├── config/
│   ├── FrontendProperties.java
│   └── security/
│       ├── SecurityConfig.java
│       ├── AppAuthenticationManager.java
│       ├── UserSecurityProperties.java
│       ├── RagAuthorityHelper.java
│       └── RequiresRagAccess.java
├── login/web/LoginController.java
├── rag/
│   ├── model/                          # DocumentEntry, VectorStoreInfo, records de resultado
│   ├── registry/DocumentRegistry.java
│   ├── service/
│   │   ├── DocumentManagementService.java
│   │   ├── DocumentParserService.java
│   │   ├── VectorStoreRegistry.java
│   │   ├── JGitGitHubImportService.java
│   │   ├── ConfluenceImportService.java
│   │   ├── MondayImportService.java
│   │   └── S3ImportService.java
│   └── web/
│       ├── RagManagementController.java
│       └── *Form.java                  # Records de binding de formulário
└── config/rag/vectorstore/
    ├── SimpleVectorStoreConfig.java
    ├── PgVectorStoreConfig.java
    └── RedisVectorStoreConfig.java

src/main/resources/
├── application.yaml
├── data/vectorstore.json               # SimpleVectorStore serializado
├── docs/faq.txt                        # Documentos de exemplo pré-carregados
└── templates/
    ├── login.html
    ├── chat/index.html
    ├── rag/index.html
    ├── rag/fragments/                  # Templates parciais HTMX
    └── fragments/                      # Navbar, configuração HTMX
```

---

## Endpoints da API

### Chat

| Método | Caminho | Descrição |
| --- | --- | --- |
| `GET` | `/chat` | Página da interface de chat |
| `GET` | `/chat/stream` | Stream SSE — query params: `message`, `model`, `temperature`, `useRag`, `storeId` |
| `POST` | `/chat/clear` | Limpa o histórico de conversa da sessão atual |

### Gerenciamento RAG

| Método | Caminho | Descrição |
| --- | --- | --- |
| `GET` | `/rag` | Página do painel de gerenciamento RAG |
| `GET` | `/rag/stores/{storeId}` | Carrega o fragmento de visualização do store |
| `POST` | `/rag/stores/{storeId}/upload` | Upload de arquivos (multipart) |
| `POST` | `/rag/stores/{storeId}/import/github` | Importação de repositório GitHub |
| `POST` | `/rag/stores/{storeId}/import/confluence` | Importação de espaço ou páginas do Confluence |
| `POST` | `/rag/stores/{storeId}/import/monday` | Importação de quadro do Monday.com |
| `POST` | `/rag/stores/{storeId}/import/s3` | Importação de bucket AWS S3 |
| `POST` | `/rag/stores/{storeId}/delete` | Exclusão de documentos por ID |
| `GET` | `/rag/stores/{storeId}/search` | Busca semântica — query params: `query`, `topK`, `similarityThreshold` |

Todos os endpoints `/rag` exigem autenticação e aplicam controle de acesso por store.
