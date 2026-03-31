package io.tonyblack10.aiplayground.rag.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.tonyblack10.aiplayground.rag.model.ConfluenceImportResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
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
public class ConfluenceImportService {

  private static final Logger log = LoggerFactory.getLogger(ConfluenceImportService.class);
  private static final Duration REQUEST_DELAY = Duration.ofMillis(500);
  private static final int MAX_RETRIES = 3;
  private static final int PAGE_BATCH_SIZE = 50;

  private final WebClient webClient;
  private final String baseUrl;

  public ConfluenceImportService(
      WebClient.Builder webClientBuilder,
      @Value("${app.confluence.base-url:}") String baseUrl,
      @Value("${app.confluence.email:}") String email,
      @Value("${app.confluence.api-token:}") String apiToken) {

    this.baseUrl = baseUrl.replaceAll("/+$", "");
    String credentials = Base64.getEncoder()
        .encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));

    this.webClient = webClientBuilder
        .baseUrl(this.baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  public Mono<ConfluenceImportResult> importFromSpace(String spaceKey) {
    if (baseUrl.isBlank()) {
      return Mono.error(new IllegalStateException(
          "Confluence não está configurado. Defina CONFLUENCE_BASE_URL, CONFLUENCE_EMAIL e CONFLUENCE_API_TOKEN."));
    }
    return resolveSpaceId(spaceKey)
        .flatMap(spaceId -> fetchAllPageIds(spaceId).collectList())
        .flatMap(pageIds -> processPages(pageIds, spaceKey));
  }

  private Mono<String> resolveSpaceId(String spaceKey) {
    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/wiki/api/v2/spaces")
            .queryParam("keys", spaceKey)
            .queryParam("limit", 1)
            .build())
        .retrieve()
        .bodyToMono(SpacesResponse.class)
        .retryWhen(buildRetry("resolving space " + spaceKey))
        .flatMap(response -> {
          if (response.results() == null || response.results().isEmpty()) {
            return Mono.error(new IllegalArgumentException(
                "Space não encontrado: " + spaceKey));
          }
          log.info("Resolved space key '{}' to ID '{}'", spaceKey, response.results().get(0).id());
          return Mono.just(response.results().get(0).id());
        });
  }

  private Flux<String> fetchAllPageIds(String spaceId) {
    String initialPath = "/wiki/api/v2/spaces/" + spaceId + "/pages?limit=" + PAGE_BATCH_SIZE + "&depth=all";
    return fetchPageBatch(initialPath)
        .expand(response -> {
          if (response.links() == null || response.links().next() == null) {
            return Mono.empty();
          }
          return fetchPageBatch(response.links().next());
        })
        .flatMapIterable(PagesResponse::results)
        .map(PageResult::id);
  }

  private Mono<PagesResponse> fetchPageBatch(String path) {
    return webClient.get()
        .uri(path)
        .retrieve()
        .bodyToMono(PagesResponse.class)
        .retryWhen(buildRetry("fetching pages batch"));
  }

  private Mono<ConfluenceImportResult> processPages(List<String> pageIds, String spaceKey) {
    log.info("Found {} pages in space '{}'. Starting ingestion with {}ms delay between requests.",
        pageIds.size(), spaceKey, REQUEST_DELAY.toMillis());

    return Flux.fromIterable(pageIds)
        .concatMap(pageId -> Mono.delay(REQUEST_DELAY)
            .then(fetchAndParsePage(pageId, spaceKey)
                .map(docs -> new PageOutcome(docs, null))
                .onErrorResume(e -> {
                  log.warn("Failed to process page {}: {}", pageId, e.getMessage());
                  return Mono.just(new PageOutcome(List.of(), "Página " + pageId + ": " + e.getMessage()));
                })))
        .subscribeOn(Schedulers.boundedElastic())
        .collectList()
        .map(outcomes -> {
          List<Document> allDocs = outcomes.stream()
              .flatMap(o -> o.docs().stream())
              .toList();
          List<String> errors = outcomes.stream()
              .filter(o -> o.error() != null)
              .map(PageOutcome::error)
              .toList();
          int pagesIngested = (int) outcomes.stream().filter(o -> o.error() == null && !o.docs().isEmpty()).count();
          log.info("Confluence import done: {}/{} pages ingested, {} chunks, {} errors",
              pagesIngested, pageIds.size(), allDocs.size(), errors.size());
          return new ConfluenceImportResult(pageIds.size(), pagesIngested, allDocs.size(), errors, allDocs);
        });
  }

  private Mono<List<Document>> fetchAndParsePage(String pageId, String spaceKey) {
    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/wiki/api/v2/pages/{id}")
            .queryParam("body-format", "storage")
            .build(pageId))
        .retrieve()
        .bodyToMono(PageContentResponse.class)
        .retryWhen(buildRetry("fetching page " + pageId))
        .flatMap(page -> Mono.fromCallable(() -> {
          String rawHtml = page.body() != null && page.body().storage() != null
              ? page.body().storage().value() : "";
          String plainText = Jsoup.parse(rawHtml).text();
          if (plainText.isBlank()) {
            return List.<Document>of();
          }
          String title = page.title() != null ? page.title() : pageId;
          Document doc = new Document(plainText, Map.of(
              "source", "confluence",
              "spaceKey", spaceKey,
              "pageId", pageId,
              "title", title
          ));
          return new TokenTextSplitter().apply(List.of(doc));
        }).subscribeOn(Schedulers.boundedElastic()));
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

  // --- Internal response DTOs ---

  private record PageOutcome(List<Document> docs, String error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SpacesResponse(List<SpaceResult> results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SpaceResult(String id, String key, String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PagesResponse(
      List<PageResult> results,
      @JsonProperty("_links") PageLinks links) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PageResult(String id, String title) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PageLinks(String next) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PageContentResponse(String id, String title, PageBody body) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PageBody(PageBodyStorage storage) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PageBodyStorage(String value) {}
}
