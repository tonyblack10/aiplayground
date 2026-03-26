# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot AI Playground — an interactive web app for exploring Spring AI capabilities, focused on:
- **Chat**: Conversational AI with streaming, configurable models, RAG grounding, and tools
- **RAG Management**: Upload, manage, and search documents across multiple vector store backends (Simple/in-memory, pgvector, Redis)

**Stack**: Java 21, Spring Boot 3.5, Spring AI 1.1.3, Spring WebFlux (reactive), Thymeleaf + HTMX + Tailwind CSS, Docker Compose (PostgreSQL/pgvector + Redis).

## Commands

```bash
# Build
mvn clean package

# Run (starts Docker Compose services automatically)
mvn spring-boot:run

# Test
mvn test

# Single test
mvn test -Dtest=ClassName#methodName
```

**Required environment variables:**
- `OPENAI_API_KEY` — required for all AI functionality
- `GITHUB_TOKEN` — optional, for private GitHub repo imports

Docker Compose starts PostgreSQL (5432) and Redis (6379) automatically via Spring Boot's Docker Compose integration.

## Architecture

### Reactive Model
The entire stack is reactive (WebFlux). Controllers return `Mono<String>` or `Flux<T>`. All blocking I/O must be scheduled on `Schedulers.boundedElastic()`. Chat responses stream via Server-Sent Events (SSE).

### Package Structure
```
io.tonyblack10.aiplayground/
├── chat/
│   ├── model/          # ChatMessage, ChatSession, ChatSettings, ProviderInfo
│   ├── service/        # ChatService (Spring AI orchestration), ProviderRegistry
│   │   └── tools/      # @Tool beans: CalculatorTool, CurrentDateTool
│   └── web/            # ChatController (5 endpoints + SSE streaming)
├── rag/
│   ├── model/          # DocumentEntry, VectorStoreInfo
│   ├── registry/       # DocumentRegistry (in-memory document tracking)
│   ├── service/        # DocumentManagementService, DocumentParserService,
│   │                   # GitHubImportService, VectorStoreRegistry
│   └── web/            # RagManagementController
└── config/rag/vectorstore/
    ├── SimpleVectorStoreConfig   # In-memory (file-backed via vectorstore.json)
    ├── PgVectorStoreConfig       # PostgreSQL pgvector
    └── RedisVectorStoreConfig    # Redis Search
```

### Key Patterns

**Registry pattern:** `VectorStoreRegistry` maps store IDs (`simple`, `pgvector`, `redis`) to Spring bean instances qualified with `@Qualifier`. `ProviderRegistry` holds the static list of providers/models. `DocumentRegistry` tracks ingested documents in memory.

**Chat session flow:**
1. `POST /chat/send` — settings from HTMX `hx-include` form, adds user message to `WebSession`-backed `ChatSession`, returns message list fragment with streaming bubble
2. `GET /chat/stream` — SSE endpoint; builds `ChatClient` with model/temperature, optional `QuestionAnswerAdvisor` (RAG), and selected `@Tool` beans; returns `Flux<String>` as SSE

**RAG flow:** File upload → `DocumentParserService` (TXT/PDF/Markdown) → `TextSplitter` → vector store. GitHub import → `GitHubImportService` clones repo, filters, parses. All ops tracked in `DocumentRegistry`.

**Frontend:** Thymeleaf full pages + HTML fragments returned by HTMX requests. Vanilla JS handles SSE token streaming, textarea auto-resize, and Ctrl+Enter submission.

### Vector Store Backends
Three stores are always configured; `pgvector` auto-configuration is excluded in `application.yaml` (manually configured instead via `PgVectorStoreConfig` with `JdbcTemplate`).

### Spring AI Integration
- `ChatClient.Builder` used in `ChatService` — advisors and tools attached per-request based on `ChatSettings`
- `QuestionAnswerAdvisor` wraps a `VectorStore` for RAG
- Tool beans annotated with `@Tool` on methods; Spring AI auto-discovers them when passed to `ChatClient`