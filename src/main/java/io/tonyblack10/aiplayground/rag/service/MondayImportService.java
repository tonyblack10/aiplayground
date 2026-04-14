package io.tonyblack10.aiplayground.rag.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tonyblack10.aiplayground.rag.model.MondayImportResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
public class MondayImportService {

  private static final Logger log = LoggerFactory.getLogger(MondayImportService.class);
  private static final Duration REQUEST_DELAY = Duration.ofMillis(500);
  private static final int MAX_RETRIES = 3;
  private static final int PAGE_SIZE = 50;

  private final WebClient webClient;
  private final String apiToken;

  public MondayImportService(
      WebClient.Builder webClientBuilder,
      @Value("${app.monday.api-url:https://api.monday.com/v2}") String apiUrl,
      @Value("${app.monday.api-token:}") String apiToken) {

    this.apiToken = apiToken;
    this.webClient = webClientBuilder
        .baseUrl(apiUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, apiToken)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//        .filter(logRequest())
        .build();
  }

  public Mono<MondayImportResult> importFromBoard(String boardId) {
    if (apiToken.isBlank()) {
      return Mono.error(new IllegalStateException(
          "Monday.com não está configurado. Defina MONDAY_API_TOKEN nas propriedades da aplicação."));
    }
    return fetchAllItems(boardId)
        .collectList()
        .flatMap(items -> processItems(items, boardId));
  }

  private Flux<ItemResult> fetchAllItems(String boardId) {
    return fetchItemsPage(boardId, null)
        .expand(page -> {
          if (page.cursor() == null || page.cursor().isBlank()) {
            return Mono.empty();
          }
          return Mono.delay(REQUEST_DELAY).then(fetchItemsPage(boardId, page.cursor()));
        })
        .flatMapIterable(page -> page.items() != null ? page.items() : List.of());
  }

  private Mono<ItemsPage> fetchItemsPage(String boardId, String cursor) {
    String queryString;
    Map<String, Object> variables;

    if (cursor == null) {
      queryString = "query BoardItems($boardId: ID!, $limit: Int!) { boards(ids: [$boardId]) { items_page(limit: $limit) { cursor items { id name group { id title } column_values { id column { title } text } } } } }";
      variables = Map.of("boardId", boardId, "limit", PAGE_SIZE);
    } else {
      queryString = "query BoardItemsCursor($boardId: ID!, $limit: Int!, $cursor: String!) { boards(ids: [$boardId]) { items_page(limit: $limit, cursor: $cursor) { cursor items { id name group { id title } column_values { id column { title } text } } } } }";
      variables = Map.of("boardId", boardId, "limit", PAGE_SIZE, "cursor", cursor);
    }

    return postGraphQL(Map.of("query", queryString, "variables", variables),
        "items_page board " + boardId + (cursor != null ? " (cursor)" : ""))
        .map(r -> {
          if (r.data() == null || r.data().boards() == null || r.data().boards().isEmpty()) {
            throw new IllegalArgumentException("Board não encontrado: " + boardId);
          }
          ItemsPage page = r.data().boards().getFirst().itemsPage();
          return page != null ? page : new ItemsPage(null, List.of());
        });
  }

  private Mono<GraphQLResponse> postGraphQL(Map<String, Object> body, String context) {
    ObjectMapper objectMapper = new ObjectMapper();
    // parse body to json string for logging
    String bodyString;
    try {
      bodyString = objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      bodyString = body.toString();
    }
    log.debug("Sending GraphQL request for {}: {}", context, bodyString);

    return webClient.post()
        .contentType(MediaType.APPLICATION_JSON)
        .header("API-Version", "2026-01")
        .bodyValue(body)
        .retrieve()
        .bodyToMono(GraphQLResponse.class)
        .doOnError(e -> log.error("Error during {} request: {}", context, e.getMessage()))
        .retryWhen(buildRetry(context));
  }

  private Mono<MondayImportResult> processItems(List<ItemResult> items, String boardId) {
    log.info("Processing {} items from board '{}'", items.size(), boardId);
    return Mono.fromCallable(() -> {
      List<ItemOutcome> outcomes = items.stream().map(item -> {
        try {
          return new ItemOutcome(buildDocument(item, boardId), null);
        } catch (Exception e) {
          log.warn("Failed to process item {}: {}", item.id(), e.getMessage());
          return new ItemOutcome(null, "Item " + item.id() + " (" + item.name() + "): " + e.getMessage());
        }
      }).toList();

      List<Document> rawDocs = outcomes.stream()
          .map(ItemOutcome::doc)
          .filter(Objects::nonNull)
          .toList();

      List<Document> allChunks = rawDocs.isEmpty() ? List.of()
          : new TokenTextSplitter().apply(rawDocs);

      List<String> errors = outcomes.stream()
          .map(ItemOutcome::error)
          .filter(Objects::nonNull)
          .toList();

      int itemsIngested = rawDocs.size();
      log.info("Monday import done: {}/{} items ingested, {} chunks, {} errors",
          itemsIngested, items.size(), allChunks.size(), errors.size());

      return new MondayImportResult(items.size(), itemsIngested, allChunks.size(), errors, allChunks);
    }).subscribeOn(Schedulers.boundedElastic());
  }

  private Document buildDocument(ItemResult item, String boardId) {
    StringBuilder sb = new StringBuilder();
    sb.append("Item: ").append(item.name() != null ? item.name() : "").append("\n");

    if (item.group() != null && item.group().title() != null) {
      sb.append("Group: ").append(item.group().title()).append("\n");
    }

    if (item.columnValues() != null) {
      for (ColumnValue cv : item.columnValues()) {
        if (cv.text() == null || cv.text().isBlank()) continue;
        String label = (cv.column() != null && cv.column().title() != null) ? cv.column().title() : cv.id();
        sb.append(label).append(": ").append(cv.text()).append("\n");
      }
    }

    String content = sb.toString().trim();
    if (content.isEmpty()) {
      throw new IllegalStateException("nenhum conteúdo para ingerir");
    }

    return new Document(content, Map.of(
        "source", "monday",
        "boardId", boardId,
        "itemId", item.id() != null ? item.id() : "",
        "itemName", item.name() != null ? item.name() : "",
        "groupId", item.group() != null && item.group().id() != null ? item.group().id() : "",
        "groupTitle", item.group() != null && item.group().title() != null ? item.group().title() : ""
    ));
  }

  private Retry buildRetry(String context) {
    return Retry.backoff(MAX_RETRIES, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(10))
        .filter(e -> {
          if (e instanceof WebClientResponseException wre) {
            int status = wre.getStatusCode().value();
            return status == 429 || (status >= 500 && status < 600);
          }
          return !(e instanceof IllegalArgumentException) && !(e instanceof IllegalStateException);
        })
        .doBeforeRetry(signal -> log.warn("Retrying {} (attempt {}): {}",
            context, signal.totalRetries() + 1, signal.failure().getMessage()));
  }

  // --- Internal DTOs ---

  private record ItemOutcome(Document doc, String error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GraphQLResponse(DataWrapper data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DataWrapper(List<BoardResult> boards) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BoardResult(@JsonProperty("items_page") ItemsPage itemsPage) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ItemsPage(String cursor, List<ItemResult> items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ItemResult(
      String id,
      String name,
      GroupInfo group,
      @JsonProperty("column_values") List<ColumnValue> columnValues) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GroupInfo(String id, String title) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ColumnValue(String id, ColumnInfo column, String text) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ColumnInfo(String title) {}

}
