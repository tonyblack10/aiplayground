# PRD – Chat Page

**Status:** Planning
**Date:** 2026-03-24
**Tech Stack:** Java 21 · Spring Boot 3.5.11 · Spring AI 1.1.3 · WebFlux · Thymeleaf · HTMX · Tailwind CSS

---

## 1. Overview

Add a full-featured chat interface to the AI Playground that rivals the UX of ChatGPT, Gemini and Claude. Users can configure provider, model, temperature, RAG grounding and tools before starting a conversation — with streaming responses displayed in real time.

---

## 2. Goals

- Provide a conversational UI with multi-turn message history
- Allow users to select and configure their AI session (provider, model, temperature)
- Enable optional RAG grounding through any registered vector store
- Expose built-in tools the model can invoke during a conversation
- Deliver streaming token-by-token output (SSE) like modern AI chats
- Reuse all existing design patterns: Thymeleaf fragments, HTMX, Tailwind, sidebar layout

---

## 3. User Stories

| # | As a user I want to… | So that… |
|---|----------------------|----------|
| 1 | Select a chat provider (OpenAI, Ollama, Anthropic) | I can choose which LLM backend to use |
| 2 | Select a model available for the chosen provider | I can balance cost vs capability |
| 3 | Set temperature (0.0 – 2.0) | I can control output creativity/determinism |
| 4 | Attach a RAG store to ground the conversation | Responses are informed by my documents |
| 5 | Select tools the model can invoke | I can extend the model with calculators, search, etc. |
| 6 | Send a message and receive a streaming response | I see tokens appear progressively |
| 7 | See full conversation history | I can follow the multi-turn dialogue |
| 8 | Reset/clear the conversation | I can start fresh without re-configuring |
| 9 | See which context is active (provider, model, RAG) | I know how the session is configured |

---

## 4. UI Layout

The layout mirrors the existing RAG page: dark sidebar on the left + main content area on the right.

```
┌─────────────────────────────────────────────────────────────────┐
│  SIDEBAR (dark, w-72)          │  MAIN CONTENT                  │
│                                │                                 │
│  Spring AI Playground ──────── │  ┌── Session Header ─────────┐ │
│  [RAG] [Chat] ← nav links     │  │  Provider · Model · Temp   │ │
│                                │  └───────────────────────────┘ │
│  ── Configuration ──           │                                 │
│  Provider   [OpenAI  ▾]        │  ┌── Message History ─────── ┐ │
│  Model      [gpt-4o-mini ▾]    │  │                            │ │
│  Temperature  ──●──  0.7       │  │  [User]  Hello!            │ │
│                                │  │  [AI]    Hi there…         │ │
│  ── RAG ──                     │  │  [User]  Tell me about…    │ │
│  ○ None                        │  │  [AI]    ▌ (streaming…)    │ │
│  ● Simple Store  (12)          │  │                            │ │
│  ○ PgVector                    │  └────────────────────────────┘ │
│  ○ Redis                       │                                 │
│                                │  ┌── Input Area ─────────────┐ │
│  ── Tools ──                   │  │  [ Type your message… ]    │ │
│  ☑ RAG Search                  │  │                   [Send →] │ │
│  ☐ Calculator                  │  └────────────────────────────┘ │
│  ☐ Current Date                │                                 │
│                                │                                 │
│  [Clear Chat]                  │                                 │
│  ─────────────────             │                                 │
│  Spring AI v1.1.3              │                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Technical Architecture

### 5.1 Package Structure

```
src/main/java/io/tonyblack10/aiplayground/
└── chat/
    ├── model/
    │   ├── ChatMessage.java          # role (USER/ASSISTANT/SYSTEM), content, timestamp
    │   ├── ChatSession.java          # session state: history, settings
    │   ├── ChatSettings.java         # provider, model, temperature, ragStoreId, tools
    │   └── ProviderInfo.java         # id, displayName, available models
    ├── service/
    │   ├── ChatService.java          # orchestrates chat with Spring AI ChatClient
    │   ├── ProviderRegistry.java     # lists available providers & models
    │   └── tools/
    │       ├── CalculatorTool.java   # @Tool annotated method
    │       └── CurrentDateTool.java  # @Tool annotated method
    └── web/
        └── ChatController.java       # HTTP endpoints
```

### 5.2 Template Structure

```
src/main/resources/templates/
└── chat/
    ├── index.html                    # main layout (sidebar + main)
    └── fragments/
        ├── message-list.html         # th:fragment="messageList"
        ├── message-bubble.html       # th:fragment="messageBubble" (user/assistant)
        └── model-options.html        # th:fragment="modelOptions" (dynamic model dropdown)
```

### 5.3 API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/chat` | Main chat page |
| `GET` | `/chat/providers/{providerId}/models` | Returns `<option>` elements for model dropdown (HTMX) |
| `POST` | `/chat/send` | Send message → returns updated message list fragment |
| `GET` | `/chat/stream` | SSE endpoint for streaming token output |
| `DELETE` | `/chat/session` | Clear conversation history |

### 5.4 Spring AI Integration

**ChatClient** — Spring AI's fluent `ChatClient.Builder` will be used to build a call with:
- `system(prompt)` — optional system prompt
- `user(message)` — user message
- `advisors(new QuestionAnswerAdvisor(vectorStore))` — RAG grounding (when enabled)
- `options(OpenAiChatOptions.builder().model(model).temperature(temp).build())` — per-request options
- `.tools(toolBeans)` — selected tools as Spring AI `@Tool` annotated beans

**Streaming** — `ChatClient.stream().content()` returns `Flux<String>`. This will be exposed as a `text/event-stream` SSE endpoint. HTMX SSE extension will connect to it and append tokens to the assistant message bubble.

**Session State** — Conversation history is stored in-memory in `ChatSession` (per HTTP session using Spring's `@SessionScope` or a simple `Map<sessionId, ChatSession>` in a singleton service). Spring AI `MessageHistory` / `InMemoryChatMemory` will be used to feed prior turns to the model.

**Providers** — Initially OpenAI only (already configured). The registry will be designed to allow adding Ollama/Anthropic easily later via additional Spring AI starters.

### 5.5 Reactive Patterns

Follows project conventions:
- Controller returns `Mono<String>` (template name) or `Flux<ServerSentEvent<String>>`
- Blocking operations scheduled on `Schedulers.boundedElastic()`
- No blocking calls on the main event loop

---

## 6. Sidebar Configuration Controls

### 6.1 Provider Selector
- `<select>` dropdown — triggers HTMX GET to `/chat/providers/{id}/models`
- Swaps `#model-select` with updated `<option>` list
- Initially supports: OpenAI

### 6.2 Model Selector
- `<select>` dropdown — populated dynamically based on provider
- **OpenAI models:** `gpt-4o`, `gpt-4o-mini`, `gpt-3.5-turbo`

### 6.3 Temperature Slider
- `<input type="range" min="0" max="2" step="0.1">`
- Live value display next to the slider via `oninput`
- Default: `0.7`

### 6.4 RAG Store Selector
- Radio buttons (only one store can be active at a time, or "None")
- Lists all stores from `VectorStoreRegistry` with document counts
- Color-coded bullets matching RAG page (green/blue/red)
- Selecting a store enables the "RAG Search" tool automatically

### 6.5 Tools Checklist
- Checkboxes for each available tool
- **RAG Search** — uses selected vector store; disabled when RAG is "None"
- **Calculator** — evaluates basic math expressions
- **Current Date** — returns current date/time

### 6.6 Clear Chat Button
- Sends `DELETE /chat/session` via HTMX
- Clears the `#message-list` area with an empty state

---

## 7. Message Display

### User Message Bubble
```
                              ┌─────────────────────────────────┐
                              │  Tell me about Spring AI RAG.   │
                              └─────────────────────────────────┘
                                                          You  12:34
```
- Right-aligned, blue background (`bg-blue-600 text-white`)
- Timestamp in small text

### Assistant Message Bubble
```
  ╭─ gpt-4o-mini ──────────────────────────────────────────────╮
  │  Spring AI RAG (Retrieval-Augmented Generation) is a       │
  │  framework for building context-aware AI applications…     │
  ╰────────────────────────────────────────────────────────────╯
  12:34
```
- Left-aligned, white background with subtle border
- Provider/model label in the header
- Markdown rendering for code blocks (via `<pre>` tag detection)

### Streaming Indicator
- Blinking cursor `▌` appended at stream end position
- Removed when stream completes

### Tool Call Display
- When a tool is invoked, show a collapsible inline indicator:
  ```
  ⚙ Used: Calculator  [▸ show]
  ```

---

## 8. Settings Persistence

- Settings are stored in the user's browser via a hidden form state (Thymeleaf model) updated on each request
- On page load, defaults are applied: provider=OpenAI, model=gpt-4o-mini, temperature=0.7, RAG=None, no tools
- Settings persist for the duration of the browser session (server-side session scope)

---

## 9. Implementation Plan

### Phase 1 — Backend Foundation
1. Create `ChatMessage`, `ChatSession`, `ChatSettings`, `ProviderInfo` model classes
2. Create `ProviderRegistry` — hardcodes OpenAI with its model list
3. Create `CalculatorTool` and `CurrentDateTool` with Spring AI `@Tool` annotations
4. Create `ChatService` — builds `ChatClient`, applies settings, calls model, returns `Flux<String>`
5. Create `ChatController` — all 5 endpoints

### Phase 2 — Frontend Templates
6. Create `chat/index.html` — full page layout with sidebar (mirrors `rag/index.html`)
7. Create `chat/fragments/message-list.html` — empty state + message list
8. Create `chat/fragments/message-bubble.html` — user and assistant bubbles
9. Create `chat/fragments/model-options.html` — dynamic model `<option>` list
10. Wire HTMX: form POST for send, SSE for streaming, DELETE for clear

### Phase 3 — Navigation & Integration
11. Add nav links to both pages (`/rag`, `/chat`) in both page sidebars
12. Configure RAG advisor integration (wire selected `storeId` to `QuestionAnswerAdvisor`)
13. Wire tools list to `ChatClient.tools(...)` based on checked checkboxes

### Phase 4 — Polish
14. Loading spinner while waiting for first token
15. Auto-scroll to latest message
16. Keyboard shortcut: `Ctrl+Enter` to send
17. Disabled send button while streaming
18. Error handling: display error bubble on API failure

---

## 10. File Checklist

### Java
- [ ] `chat/model/ChatMessage.java`
- [ ] `chat/model/ChatSession.java`
- [ ] `chat/model/ChatSettings.java`
- [ ] `chat/model/ProviderInfo.java`
- [ ] `chat/service/ProviderRegistry.java`
- [ ] `chat/service/ChatService.java`
- [ ] `chat/service/tools/CalculatorTool.java`
- [ ] `chat/service/tools/CurrentDateTool.java`
- [ ] `chat/web/ChatController.java`

### Templates
- [ ] `templates/chat/index.html`
- [ ] `templates/chat/fragments/message-list.html`
- [ ] `templates/chat/fragments/message-bubble.html`
- [ ] `templates/chat/fragments/model-options.html`

### Configuration
- [ ] Update `application.yaml` — no new keys needed (reuses `spring.ai.openai`)
- [ ] Update `rag/index.html` — add nav link to `/chat`

---

## 11. Out of Scope (Future)

- Persistent chat history across sessions (requires DB)
- Multiple simultaneous chat sessions / tabs
- File attachment in chat
- Image generation
- Anthropic / Ollama provider support (architecture supports it, just not wired)
- Export conversation to Markdown/PDF
- System prompt editor
