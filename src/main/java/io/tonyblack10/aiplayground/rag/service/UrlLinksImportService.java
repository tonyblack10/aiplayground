package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.model.UrlLinkImportEntry;
import io.tonyblack10.aiplayground.rag.model.UrlLinksImportResult;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UrlLinksImportService {

  private static final Logger log = LoggerFactory.getLogger(UrlLinksImportService.class);

  private final WebClient webClient;
  private final DocumentParserService parserService;

  public UrlLinksImportService(WebClient.Builder webClientBuilder, DocumentParserService parserService) {
    this.webClient = webClientBuilder
        .defaultHeader("User-Agent", "aiplayground-rag-importer/1.0")
        .build();
    this.parserService = parserService;
  }

  public Mono<UrlLinksImportResult> importFromUrls(List<String> urls) {
    return Flux.fromIterable(urls)
        .flatMapSequential(this::fetchAndParse)
        .collectList()
        .map(pairs -> {
          List<UrlLinkImportEntry> entries = pairs.stream().map(Map.Entry::getKey).toList();
          List<Document> docs = pairs.stream().flatMap(p -> p.getValue().stream()).toList();
          int succeeded = (int) entries.stream().filter(UrlLinkImportEntry::success).count();
          return new UrlLinksImportResult(entries.size(), succeeded, entries.size() - succeeded, entries, docs);
        });
  }

  private Mono<Map.Entry<UrlLinkImportEntry, List<Document>>> fetchAndParse(String originalUrl) {
    String rawUrl = toRawUrl(originalUrl);
    String filename = extractFilename(rawUrl);
    log.debug("Fetching URL: {} (original: {})", rawUrl, originalUrl);
    return webClient.get()
        .uri(rawUrl)
        .retrieve()
        .bodyToMono(byte[].class)
        .flatMap(bytes -> parserService.parseTika(bytes, filename)
            .map(docs -> Map.entry(
                new UrlLinkImportEntry(originalUrl, true, docs.size(), null),
                docs
            )))
        .onErrorResume(e -> {
          log.warn("Failed to import URL {}: {}", originalUrl, e.getMessage());
          return Mono.just(Map.entry(
              new UrlLinkImportEntry(originalUrl, false, 0, e.getMessage()),
              List.of()
          ));
        });
  }

  private String toRawUrl(String url) {
    // Convert GitHub blob URL to raw URL
    // https://github.com/owner/repo/blob/branch/path → https://raw.githubusercontent.com/owner/repo/branch/path
    if (url.contains("github.com") && url.contains("/blob/")) {
      return url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/");
    }
    return url;
  }

  private String extractFilename(String url) {
    String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
    int lastSlash = path.lastIndexOf('/');
    return (lastSlash >= 0 && lastSlash < path.length() - 1) ? path.substring(lastSlash + 1) : "document.txt";
  }
}
