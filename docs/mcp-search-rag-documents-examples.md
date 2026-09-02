# RAG MCP Tools — Example Prompts

This app's MCP server exposes three RAG tools: `searchRagDocuments` (semantic search, now
agentic — see below), `getRagFilterSchema` (discover valid filter fields), and
`aggregateRagDocuments` (counting/grouping, not a similarity search).

## Selecting the vector store

The target vector store is selected via the **`X-Vector-Store` HTTP header** on the MCP
request, not a tool parameter. The header value is the store name **without** the
`VectorStore` suffix, e.g. header value `simple` targets the `simpleVectorStore` bean
(`pgvector` -> `pgVectorStore`, or a configured Redis store name).

## `searchRagDocuments`

| Parameter             | Required | Description                                                                 |
|------------------------|----------|-------------------------------------------------------------------------------|
| `query`                | yes      | Natural language query to find semantically similar documents               |
| `topK`                 | no       | Maximum number of results to return (1-20, default 5)                       |
| `similarityThreshold`  | no       | Minimum similarity score from 0.0 to 1.0 (default 0.0)                      |
| `filterExpression`     | no       | Metadata filter expression (e.g. `source == 'readme.md'`)                   |

**This tool is agentic.** Queries judged "complex" (more than a handful of words, or
containing `and`/`or`/a comma) are first cleaned up by an LLM-based query rewrite step, then
automatically expanded into a few alternative phrasings of the rewritten query; each phrasing is
searched against the vector store in parallel, and the results are deduplicated and merged
before being truncated back down to the requested `topK`. This broadens recall for multi-part
questions without changing behavior for short, simple queries (those still do a single search,
exactly like before). `topK` still means "at most this many results returned" — it's the
internal search breadth that changes, not the contract.

### Valid `filterExpression` fields

Only fields actually written by this app's importers can be used in a filter. Call
`getRagFilterSchema` to get this list (with descriptions and example values) at any time:

| Field        | Present on                                              | Example value       |
|--------------|----------------------------------------------------------|----------------------|
| `source`     | always (filename for uploads/GitHub/S3/URL imports, or `'confluence'`/`'monday'`) | `readme.md`         |
| `spaceKey`   | Confluence imports only                                   | `ENG`                |
| `pageId`     | Confluence imports only                                   | `123456`             |
| `title`      | Confluence imports only                                   | `Release Checklist`  |
| `boardId`    | Monday.com imports only                                   | `789012`             |
| `itemId`     | Monday.com imports only                                   | `345678`             |
| `itemName`   | Monday.com imports only                                   | `Sprint 42 planning` |
| `groupId`    | Monday.com imports only                                   | `topics`              |
| `groupTitle` | Monday.com imports only                                   | `In Progress`         |

GitHub-imported documents only carry `source` = the file's name — there is **no** `branch` or
`folders` field on the ingested chunks (those exist only in the separate import-history record,
not on the searchable documents). A filter referencing an unknown field is rejected before any
search runs, with an error message listing the valid fields.

`filterExpression` syntax notes (from Spring AI's filter grammar):
- Comparisons: `==`, `!=`, `>`, `>=`, `<`, `<=`
- Boolean combinators: `AND`, `OR`, `NOT`, and parentheses for grouping
- List membership uses **square brackets**, not parentheses: `field IN ['a', 'b']` / `NIN`

---

## Simple examples

### 1. Basic semantic search

> Search the `simple` vector store for documents about "how to configure Redis as a vector store".

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "how to configure Redis as a vector store"
  }
}
```
(with HTTP header `X-Vector-Store: simple`)

### 2. Limit the number of results

> Find the top 3 most relevant chunks in `pgvector` about "chat streaming with SSE".

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "chat streaming with SSE",
    "topK": 3
  }
}
```
(with HTTP header `X-Vector-Store: pgvector`)

### 3. Apply a minimum similarity threshold

> Search `redis` for "GitHub webhook ingestion" but only return strong matches (similarity >= 0.75).

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "GitHub webhook ingestion",
    "similarityThreshold": 0.75
  }
}
```
(with HTTP header `X-Vector-Store: redis`)

---

## Discover valid filter fields first

### 4. Call `getRagFilterSchema` before writing a filter

> Before filtering, find out which metadata fields are actually available.

```json
{ "tool": "getRagFilterSchema", "arguments": {} }
```

Response (abridged):
```json
[
  { "name": "source", "description": "Origin of the chunk...", "exampleValue": "readme.md" },
  { "name": "spaceKey", "description": "Confluence space key...", "exampleValue": "ENG" }
]
```

Then use one of the returned fields in `searchRagDocuments`:

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "tool registration",
    "filterExpression": "source == 'readme.md'"
  }
}
```
(with HTTP header `X-Vector-Store: simple`)

---

## Complex examples (query + metadata filters)

### 5. Filter by Confluence space

> Search `pgvector` for "release checklist" but restrict results to the `ENG` Confluence space.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "release checklist",
    "filterExpression": "spaceKey == 'ENG'"
  }
}
```
(with HTTP header `X-Vector-Store: pgvector`)

### 6. Combine multiple conditions with AND

> Search `simple` for "release checklist" from the Confluence `ENG` space and specifically page `123456`.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "release checklist",
    "filterExpression": "spaceKey == 'ENG' AND pageId == '123456'",
    "topK": 8
  }
}
```
(with HTTP header `X-Vector-Store: simple`)

### 7. Combine OR conditions across sources

> In `simple`, find "sprint planning notes" that came from either Monday.com or Confluence.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "sprint planning notes",
    "filterExpression": "source == 'monday' OR source == 'confluence'"
  }
}
```
(with HTTP header `X-Vector-Store: simple`)

### 8. Filter using IN with a list of values (square brackets)

> Search `pgvector` for "board item status" limited to a specific set of Monday.com board IDs.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "board item status",
    "filterExpression": "boardId IN ['12345', '67890']"
  }
}
```
(with HTTP header `X-Vector-Store: pgvector`)

### 9. Negate a condition with NOT

> Search `redis` for "deployment steps" excluding anything sourced from `confluence`.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "deployment steps",
    "filterExpression": "NOT source == 'confluence'"
  }
}
```
(with HTTP header `X-Vector-Store: redis`)

### 10. Full example: complex query + filter + topK + threshold

> Search `pgvector` for "how does the vector store selection and filtering work end to end",
> restricted to the app's own README, requiring high-confidence matches, at most 5 results.
> Note the query is intentionally long/multi-part — this triggers automatic multi-query
> expansion for broader recall.

```json
{
  "tool": "searchRagDocuments",
  "arguments": {
    "query": "how does the vector store selection and filtering work end to end",
    "filterExpression": "source == 'readme.md'",
    "similarityThreshold": 0.8,
    "topK": 5
  }
}
```
(with HTTP header `X-Vector-Store: pgvector`)

---

## Count/aggregate queries — `aggregateRagDocuments`

This tool answers quantitative questions by grouping/counting already-ingested documents from
the in-memory document registry — it does **not** run a vector similarity search, so it can
answer things vector search structurally can't (like "how many"). Counts reflect only documents
ingested into the running application instance.

| Parameter          | Required | Description                                                        |
|---------------------|----------|------------------------------------------------------------------------|
| `groupByField`      | yes      | Metadata field to group by, e.g. `source`, `spaceKey`, `boardId`       |
| `filterExpression`  | no       | Optional filter restricting which documents are counted                |

### 11. How many documents came from each source?

```json
{
  "tool": "aggregateRagDocuments",
  "arguments": {
    "groupByField": "source"
  }
}
```
(with HTTP header `X-Vector-Store: simple`)

Response:
```json
[
  { "value": "readme.md", "count": 12 },
  { "value": "confluence", "count": 47 },
  { "value": "monday", "count": 9 }
]
```

### 12. How many Confluence documents are in the `ENG` space?

```json
{
  "tool": "aggregateRagDocuments",
  "arguments": {
    "groupByField": "pageId",
    "filterExpression": "source == 'confluence' AND spaceKey == 'ENG'"
  }
}
```
(with HTTP header `X-Vector-Store: simple`)

The number of buckets returned is the number of distinct Confluence pages ingested from the
`ENG` space; summing their counts gives the total chunk count for that space.
