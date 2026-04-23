package io.tonyblack10.aiplayground.rag.mcp;

import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import io.tonyblack10.aiplayground.rag.service.DocumentManagementService;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class RagSearchMcpTools {

  private final DocumentManagementService documentManagementService;

  public RagSearchMcpTools(
      DocumentManagementService documentManagementService,
      VectorStoreRegistry vectorStoreRegistry) {
    this.documentManagementService = documentManagementService;
  }

  @Tool(description = "Search for RAG documents using semantic similarity in a specific vector store. Returns matching document chunks with content and metadata.")
  public List<SearchResult> searchRagDocuments(
      @ToolParam(description = "ID of the vector store to search. Available values: 'simpleVectorStore', 'pgVectorStore', 'redisVectorStore'") String storeId,
      @ToolParam(description = "Natural language query to find semantically similar documents") String query,
      @ToolParam(required = false, description = "Maximum number of results to return (1-20, default 5)") Integer topK,
      @ToolParam(required = false, description = "Minimum similarity score from 0.0 to 1.0 (default 0.0)") Double similarityThreshold,
      @ToolParam(required = false, description = "Optional metadata filter expression (e.g. \"source == 'readme.md'\")") String filterExpression
  ) {
    int k = topK != null ? Math.clamp(topK, 1, 20) : 5;
    double threshold = similarityThreshold != null ? similarityThreshold : 0.0;

    return documentManagementService
        .semanticSearch(storeId, query, k, threshold, filterExpression)
        .map(docs -> docs.stream().map(SearchResult::from).toList())
        .block();
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
