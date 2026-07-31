# `searchRagDocuments` MCP Tool — Example Prompts

The `searchRagDocuments` MCP tool performs semantic search against a vector store. These examples
assume the target vector store is selected via a **`vectorStoreName` tool parameter** (e.g.
`simple`, `pgvector`, `redis`) rather than an HTTP header.

## Tool parameters

| Parameter            | Required | Description                                                                 |
|-----------------------|----------|-------------------------------------------------------------------------------|
| `vectorStoreName`      | yes      | Vector store to search: `simple`, `pgvector`, or `redis`                     |
| `query`                | yes      | Natural language query to find semantically similar documents               |
| `topK`                 | no       | Maximum number of results to return (1-20, default 5)                       |
| `similarityThreshold`  | no       | Minimum similarity score from 0.0 to 1.0 (default 0.0)                      |
| `filterExpression`     | no       | Metadata filter expression (e.g. `source == 'readme.md'`)                   |

---

## Simple examples

### 1. Basic semantic search

> Search the `simple` vector store for documents about "how to configure Redis as a vector store".

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "simple",
    "query": "how to configure Redis as a vector store"
  }
}
```

### 2. Limit the number of results

> Find the top 3 most relevant chunks in `pgvector` about "chat streaming with SSE".

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "pgvector",
    "query": "chat streaming with SSE",
    "topK": 3
  }
}
```

### 3. Apply a minimum similarity threshold

> Search `redis` for "GitHub webhook ingestion" but only return strong matches (similarity >= 0.75).

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "redis",
    "query": "GitHub webhook ingestion",
    "similarityThreshold": 0.75
  }
}
```

---

## Complex examples (query + metadata filters)

### 4. Filter by document source

> In the `simple` store, find content about "tool registration" that only comes from `readme.md`.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "simple",
    "query": "tool registration",
    "filterExpression": "source == 'readme.md'"
  }
}
```

### 5. Filter by Confluence space

> Search `pgvector` for "release checklist" but restrict results to the `ENG` Confluence space.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "pgvector",
    "query": "release checklist",
    "filterExpression": "spaceKey == 'ENG'"
  }
}
```

### 6. Combine multiple conditions with AND

> Search `redis` for "authentication flow", limited to GitHub-sourced content on the `main` branch.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "redis",
    "query": "authentication flow",
    "filterExpression": "source == 'github' AND branch == 'main'",
    "topK": 8
  }
}
```

### 7. Combine OR conditions across sources

> In `simple`, find "sprint planning notes" that came from either Monday.com or Confluence.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "simple",
    "query": "sprint planning notes",
    "filterExpression": "source == 'monday' OR source == 'confluence'"
  }
}
```

### 8. Filter using IN with a list of values

> Search `pgvector` for "board item status" limited to a specific set of Monday.com board IDs.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "pgvector",
    "query": "board item status",
    "filterExpression": "boardId IN ('12345', '67890')"
  }
}
```

### 9. Negate a condition with NOT

> Search `redis` for "deployment steps" excluding anything sourced from `confluence`.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "redis",
    "query": "deployment steps",
    "filterExpression": "NOT source == 'confluence'"
  }
}
```

### 10. Full example: query + filter + topK + threshold

> Search `pgvector` for "vector store configuration", restricted to files ingested from the
> `src/main/resources` folder of the GitHub import, requiring high confidence matches, and
> returning at most 5 results.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "vectorStoreName": "pgvector",
    "query": "vector store configuration",
    "filterExpression": "source == 'github' AND folders == 'src/main/resources'",
    "similarityThreshold": 0.8,
    "topK": 5
  }
}
```
