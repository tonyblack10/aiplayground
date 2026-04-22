package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.model.DocumentImportRecord;
import io.tonyblack10.aiplayground.rag.registry.DocumentRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Restores the RAG from import records persisted in S3.
 *
 * <p>Only integration-sourced documents (github, confluence, s3, monday) are restored.
 * File uploads are excluded because the original bytes are not retained.</p>
 */
@Service
public class RagRestoreService {

  private static final Logger log = LoggerFactory.getLogger(RagRestoreService.class);

  private final ImportRecordS3Repository recordRepository;
  private final GitHubImporter gitHubImporter;
  private final ConfluenceImportService confluenceImportService;
  private final S3ImportService s3ImportService;
  private final MondayImportService mondayImportService;
  private final VectorStoreRegistry vectorStoreRegistry;
  private final DocumentRegistry documentRegistry;

  public RagRestoreService(
      ImportRecordS3Repository recordRepository,
      GitHubImporter gitHubImporter,
      ConfluenceImportService confluenceImportService,
      S3ImportService s3ImportService,
      MondayImportService mondayImportService,
      VectorStoreRegistry vectorStoreRegistry,
      DocumentRegistry documentRegistry) {
    this.recordRepository = recordRepository;
    this.gitHubImporter = gitHubImporter;
    this.confluenceImportService = confluenceImportService;
    this.s3ImportService = s3ImportService;
    this.mondayImportService = mondayImportService;
    this.vectorStoreRegistry = vectorStoreRegistry;
    this.documentRegistry = documentRegistry;
  }

  /**
   * Reads all import records from S3 and re-ingests each integration document into the
   * appropriate vector store. Upload-sourced records are skipped.
   *
   * @return a report summarising how many records were restored and any failures
   */
  public Mono<RagRestoreReport> restoreAll() {
    return Mono.fromCallable(recordRepository::findAll)
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(records -> {
          List<DocumentImportRecord> integrationRecords = records.stream()
              .filter(r -> !"upload".equals(r.source()))
              .toList();
          log.info("Starting RAG restore: {} integration record(s) found", integrationRecords.size());

          return Flux.fromIterable(integrationRecords)
              .concatMap(record -> restoreRecord(record)
                  .map(chunks -> new RestoreOutcome(record.id(), record.source(), record.documentName(), chunks, null))
                  .onErrorResume(e -> {
                    log.warn("Failed to restore record {} ({}): {}", record.id(), record.source(), e.getMessage());
                    return Mono.just(new RestoreOutcome(record.id(), record.source(), record.documentName(), 0, e.getMessage()));
                  }))
              .collectList()
              .map(outcomes -> {
                int restored = (int) outcomes.stream().filter(o -> o.error() == null).count();
                int failed = outcomes.size() - restored;
                log.info("RAG restore complete: {}/{} record(s) restored, {} failed", restored, outcomes.size(), failed);
                return new RagRestoreReport(outcomes.size(), restored, failed, outcomes);
              });
        });
  }

  private Mono<Integer> restoreRecord(DocumentImportRecord record) {
    Map<String, String> data = record.additionalData() != null ? record.additionalData() : Map.of();
    return switch (record.source()) {
      case "github" -> restoreGitHub(record.storeId(), data);
      case "confluence" -> restoreConfluence(record.storeId(), data);
      case "s3" -> restoreS3(record.storeId(), data);
      case "monday" -> restoreMonday(record.storeId(), data);
      default -> Mono.error(new IllegalArgumentException("Unsupported source for restore: " + record.source()));
    };
  }

  private Mono<Integer> restoreGitHub(String storeId, Map<String, String> data) {
    String repoUrl = data.getOrDefault("repoUrl", "");
    String branch = data.getOrDefault("branch", "main");
    List<String> folders = splitCsv(data.getOrDefault("folders", ""));
    return gitHubImporter.importFromGitHub(repoUrl, branch, folders)
        .flatMap(docs -> Mono.fromCallable(() -> {
          if (!docs.isEmpty()) {
            vectorStoreRegistry.getStore(storeId).add(docs);
            documentRegistry.register(storeId, docs);
          }
          log.info("GitHub restore: {} chunk(s) re-ingested from {}", docs.size(), repoUrl);
          return docs.size();
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  private Mono<Integer> restoreConfluence(String storeId, Map<String, String> data) {
    String spaceKey = data.getOrDefault("spaceKey", "");
    List<String> pageIds = splitCsv(data.getOrDefault("pageIds", ""));
    Mono<io.tonyblack10.aiplayground.rag.model.ConfluenceImportResult> importMono = pageIds.isEmpty()
        ? confluenceImportService.importFromSpace(spaceKey)
        : confluenceImportService.importSpecificPages(spaceKey, pageIds);
    return importMono.flatMap(result -> Mono.fromCallable(() -> {
      if (!result.documents().isEmpty()) {
        vectorStoreRegistry.getStore(storeId).add(result.documents());
        documentRegistry.register(storeId, result.documents());
      }
      log.info("Confluence restore: {} chunk(s) re-ingested from space {}", result.chunksIngested(), spaceKey);
      return result.chunksIngested();
    }).subscribeOn(Schedulers.boundedElastic()));
  }

  private Mono<Integer> restoreS3(String storeId, Map<String, String> data) {
    String bucket = data.getOrDefault("bucket", "");
    String prefix = data.getOrDefault("prefix", "");
    List<String> extensions = splitCsv(data.getOrDefault("extensions", ""));
    return s3ImportService.importFromBucket(bucket, prefix, extensions)
        .flatMap(result -> Mono.fromCallable(() -> {
          if (!result.documents().isEmpty()) {
            vectorStoreRegistry.getStore(storeId).add(result.documents());
            documentRegistry.register(storeId, result.documents());
          }
          log.info("S3 restore: {} chunk(s) re-ingested from s3://{}/{}", result.chunksIngested(), bucket, prefix);
          return result.chunksIngested();
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  private Mono<Integer> restoreMonday(String storeId, Map<String, String> data) {
    String boardId = data.getOrDefault("boardId", "");
    return mondayImportService.importFromBoard(boardId)
        .flatMap(result -> Mono.fromCallable(() -> {
          if (!result.documents().isEmpty()) {
            vectorStoreRegistry.getStore(storeId).add(result.documents());
            documentRegistry.register(storeId, result.documents());
          }
          log.info("Monday restore: {} chunk(s) re-ingested from board {}", result.chunksIngested(), boardId);
          return result.chunksIngested();
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  private List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public record RagRestoreReport(
      int totalRecords,
      int restored,
      int failed,
      List<RestoreOutcome> outcomes
  ) {}

  public record RestoreOutcome(
      String recordId,
      String source,
      String documentName,
      int chunksRestored,
      String error
  ) {}
}
