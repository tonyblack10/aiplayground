package io.tonyblack10.aiplayground.rag.mcp;

import io.tonyblack10.aiplayground.chat.service.tools.UserToolContext;
import io.tonyblack10.aiplayground.config.mcp.McpTransportHeaders;
import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import io.tonyblack10.aiplayground.rag.service.AgenticRagSearchService;
import io.tonyblack10.aiplayground.rag.service.FilterExpressionValidator;
import io.tonyblack10.aiplayground.rag.service.RagAggregationService;
import io.tonyblack10.aiplayground.rag.service.RagFilterSchema;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

@Service
public class RagSearchMcpTools {

  private static final Logger log = LoggerFactory.getLogger(RagSearchMcpTools.class);

  /** HTTP header carrying the target vector store name, without the {@link #VECTOR_STORE_SUFFIX}. */
  private static final String VECTOR_STORE_HEADER = "X-Vector-Store";

  /** Suffix appended to the header value to form the full vector store id (e.g. 'simple' -> 'simpleVectorStore'). */
  private static final String VECTOR_STORE_SUFFIX = "VectorStore";

  private final AgenticRagSearchService agenticRagSearchService;
  private final RagAggregationService ragAggregationService;

  public RagSearchMcpTools(
      AgenticRagSearchService agenticRagSearchService,
      RagAggregationService ragAggregationService) {
    this.agenticRagSearchService = agenticRagSearchService;
    this.ragAggregationService = ragAggregationService;
  }

  @Tool(description = "Search for RAG documents using semantic similarity in the vector store selected via the '"
      + VECTOR_STORE_HEADER + "' HTTP header. For complex/multi-part queries, automatically rewrites the query "
      + "for clarity, expands it into several phrasings, and fuses the results for broader recall. Optional "
      + "filterExpression fields: " + RagFilterSchema.CSV_SUMMARY + " -- call getRagFilterSchema for descriptions "
      + "and examples. Returns matching document chunks with content and metadata.")
  public List<SearchResult> searchRagDocuments(
      @ToolParam(description = "Natural language query to find semantically similar documents") String query,
      @ToolParam(required = false, description = "Maximum number of results to return (1-20, default 5)") Integer topK,
      @ToolParam(required = false, description = "Minimum similarity score from 0.0 to 1.0 (default 0.0)") Double similarityThreshold,
      @ToolParam(required = false, description = "Optional metadata filter expression, e.g. \"source == 'readme.md'\". "
          + "Call getRagFilterSchema first if unsure which fields are valid.") String filterExpression,
      ToolContext toolContext
  ) {
    int k = topK != null ? Math.clamp(topK, 1, 20) : 5;
    double threshold = similarityThreshold != null ? similarityThreshold : 0.0;
    String storeId = resolveStoreId(toolContext);
    Filter.Expression parsedFilter = parseFilterOrNull(filterExpression);

    logInvocation("searchRagDocuments", toolContext, storeId, query);

    return agenticRagSearchService.agenticSearch(storeId, query, k, threshold, parsedFilter)
        .map(docs -> docs.stream().map(SearchResult::from).toList())
        .block();
  }

  @Tool(description = "Returns the metadata fields that can be used in searchRagDocuments' and "
      + "aggregateRagDocuments' filterExpression/groupByField parameters, with descriptions and example values. "
      + "Call this before constructing a non-trivial filter to discover valid field names.")
  public List<RagFilterSchema.FieldDef> getRagFilterSchema(ToolContext toolContext) {
    return RagFilterSchema.fields();
  }

  @Tool(description = "Counts/groups already-ingested RAG documents by a metadata field, using the document "
      + "registry -- NOT a vector similarity search. Use this for questions like 'how many documents about X' "
      + "or 'how many docs came from Confluence'. Counts reflect only documents ingested into this running "
      + "application instance. Valid groupByField/filterExpression fields: " + RagFilterSchema.CSV_SUMMARY
      + " -- call getRagFilterSchema for descriptions and examples.")
  public List<RagAggregationService.AggregateBucket> aggregateRagDocuments(
      @ToolParam(description = "Metadata field to group by, e.g. 'source', 'spaceKey', 'boardId'") String groupByField,
      @ToolParam(required = false, description = "Optional metadata filter expression restricting counted documents")
          String filterExpression,
      ToolContext toolContext
  ) {
    String storeId = resolveStoreId(toolContext);
    Filter.Expression parsedFilter = parseFilterOrNull(filterExpression);

    logInvocation("aggregateRagDocuments", toolContext, storeId, groupByField);

    return ragAggregationService.aggregate(storeId, groupByField, parsedFilter).block();
  }

  private Filter.Expression parseFilterOrNull(String filterExpression) {
    return filterExpression != null && !filterExpression.isBlank()
        ? FilterExpressionValidator.parseAndValidate(filterExpression)
        : null;
  }

  private void logInvocation(String toolName, ToolContext toolContext, String storeId, String detail) {
    var userCtx = (UserToolContext) toolContext.getContext().get(UserToolContext.TOOL_CONTEXT_KEY);
    if (userCtx != null) {
      log.info("MCP tool invoked: {} | user='{}' authorities={} | store='{}' detail='{}'",
          toolName, userCtx.username(), userCtx.authorities(), storeId, detail);
    } else {
      log.info("MCP tool invoked: {} | store='{}' detail='{}'", toolName, storeId, detail);
    }
  }

  /** Combines the '{@value #VECTOR_STORE_HEADER}' header value with the '{@value #VECTOR_STORE_SUFFIX}' suffix. */
  private String resolveStoreId(ToolContext toolContext) {
    String storeName = McpTransportHeaders.from(toolContext).getFirst(VECTOR_STORE_HEADER);
    if (storeName == null || storeName.isBlank()) {
      throw new IllegalArgumentException(
          "Missing required '" + VECTOR_STORE_HEADER + "' header identifying the vector store (its name without the '"
              + VECTOR_STORE_SUFFIX + "' suffix)");
    }
    return storeName.strip() + VECTOR_STORE_SUFFIX;
  }

  public record SearchResult(String id, String content, Map<String, Object> metadata,
                             Double score) {

    static SearchResult from(Document doc) {
      return new SearchResult(doc.getId(), doc.getText(), doc.getMetadata(), doc.getScore());
    }
  }

  public record DocumentSummary(String id, String source, String contentPreview,
                                String ingestedAt) {

    static DocumentSummary from(DocumentEntry entry) {
      return new DocumentSummary(
          entry.id(),
          entry.source(),
          entry.contentPreview(),
          entry.ingestedAt() != null ? entry.ingestedAt().toString() : null
      );
    }
  }

  public record StoreInfo(String id, String displayName, String type, boolean primary,
                          int documentCount) {

  }
}
