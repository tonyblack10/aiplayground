package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.config.AgenticRagProperties;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Agentic RAG retrieval: for "complex" queries, first cleans up the query with Spring AI's
 * {@link RewriteQueryTransformer} (the "Advanced RAG" pattern from the Spring AI RAG reference),
 * then decomposes it into several phrasings ({@link MultiQueryExpander}), retrieves the full
 * requested {@code topK} for each phrasing in parallel, fuses/deduplicates the results
 * ({@link ConcatenationDocumentJoiner}), and truncates back down to the caller's requested
 * {@code topK} by score. Simple queries skip rewriting/expansion entirely and behave exactly
 * like a single {@code VectorStore.similaritySearch} call, so latency/cost for trivial lookups
 * is unchanged.
 */
@Service
@EnableConfigurationProperties(AgenticRagProperties.class)
public class AgenticRagSearchService {

  private final VectorStoreRegistry vectorStoreRegistry;
  private final ChatClient.Builder chatClientBuilder;
  private final AgenticRagProperties properties;

  public AgenticRagSearchService(
      VectorStoreRegistry vectorStoreRegistry,
      ChatClient.Builder chatClientBuilder,
      AgenticRagProperties properties) {
    this.vectorStoreRegistry = vectorStoreRegistry;
    this.chatClientBuilder = chatClientBuilder;
    this.properties = properties;
  }

  public Mono<List<Document>> agenticSearch(
      String storeId, String query, int topK, double similarityThreshold, Filter.Expression filterExpression) {

    VectorStore store = vectorStoreRegistry.getStore(storeId);
    VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
        .vectorStore(store)
        .topK(topK)
        .similarityThreshold(similarityThreshold);
    if (filterExpression != null) {
      retrieverBuilder.filterExpression(filterExpression);
    }
    DocumentRetriever retriever = retrieverBuilder.build();

    if (!properties.isEnabled() || !isComplex(query)) {
      return Mono.fromCallable(() -> retriever.retrieve(new Query(query)))
          .subscribeOn(Schedulers.boundedElastic());
    }

    return Mono.fromCallable(() -> rewrite(query))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(rewrittenQuery -> Mono.fromCallable(() -> expand(rewrittenQuery))
            .subscribeOn(Schedulers.boundedElastic()))
        .flatMap(subQueries -> Flux.fromIterable(subQueries)
            .flatMap(subQuery -> Mono.fromCallable(() -> Map.entry(subQuery, retriever.retrieve(subQuery)))
                .subscribeOn(Schedulers.boundedElastic()))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue))
        .map(perQueryResults -> fuse(perQueryResults, topK));
  }

  private boolean isComplex(String query) {
    String trimmed = query.trim();
    int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    return wordCount > properties.getComplexityWordThreshold()
        || trimmed.matches("(?i).*\\b(and|or)\\b.*")
        || trimmed.contains(",");
  }

  private Query rewrite(String query) {
    QueryTransformer transformer = RewriteQueryTransformer.builder()
        .chatClientBuilder(chatClientBuilder)
        .targetSearchSystem("a vector store of ingested technical documentation and imported records")
        .build();
    return transformer.transform(new Query(query));
  }

  private List<Query> expand(Query query) {
    QueryExpander expander = MultiQueryExpander.builder()
        .chatClientBuilder(chatClientBuilder)
        .numberOfQueries(properties.getNumberOfQueries())
        .includeOriginal(true)
        .build();
    return expander.expand(query);
  }

  private List<Document> fuse(Map<Query, List<Document>> perQueryResults, int topK) {
    Map<Query, List<List<Document>>> forJoiner = perQueryResults.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));
    DocumentJoiner joiner = new ConcatenationDocumentJoiner();
    return joiner.join(forJoiner).stream()
        .sorted(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(topK)
        .toList();
  }
}
