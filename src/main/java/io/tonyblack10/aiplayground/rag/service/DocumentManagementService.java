package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.model.ConfluenceImportResult;
import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import io.tonyblack10.aiplayground.rag.model.DocumentImportRecord;
import io.tonyblack10.aiplayground.rag.model.FileUploadResult;
import io.tonyblack10.aiplayground.rag.model.MondayImportResult;
import io.tonyblack10.aiplayground.rag.model.S3ImportResult;
import io.tonyblack10.aiplayground.rag.registry.DocumentRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class DocumentManagementService {

  private final VectorStoreRegistry vectorStoreRegistry;
  private final DocumentRegistry documentRegistry;
  private final DocumentParserService parserService;
  private final GitHubImporter gitHubImportService;
  private final ConfluenceImportService confluenceImportService;
  private final MondayImportService mondayImportService;
  private final S3ImportService s3ImportService;
  private final ImportRecordS3Repository importRecordRepository;

  public DocumentManagementService(
      VectorStoreRegistry vectorStoreRegistry,
      DocumentRegistry documentRegistry,
      DocumentParserService parserService,
      GitHubImporter gitHubImportService,
      ConfluenceImportService confluenceImportService,
      MondayImportService mondayImportService,
      S3ImportService s3ImportService,
      ImportRecordS3Repository importRecordRepository) {
    this.vectorStoreRegistry = vectorStoreRegistry;
    this.documentRegistry = documentRegistry;
    this.parserService = parserService;
    this.gitHubImportService = gitHubImportService;
    this.confluenceImportService = confluenceImportService;
    this.mondayImportService = mondayImportService;
    this.s3ImportService = s3ImportService;
    this.importRecordRepository = importRecordRepository;
  }

  public Mono<List<DocumentEntry>> listDocuments(String storeId) {
    return Mono.fromCallable(() -> documentRegistry.getDocuments(storeId))
        .subscribeOn(Schedulers.boundedElastic());
  }

  private static final long MAX_UPLOAD_SIZE_BYTES = 12L * 1024 * 1024;

  public Mono<List<FileUploadResult>> uploadFiles(String storeId, List<FilePart> files) {
    return Flux.fromIterable(files)
        .flatMapSequential(filePart -> DataBufferUtils.join(filePart.content())
            .map(buffer -> {
              byte[] bytes = new byte[buffer.readableByteCount()];
              buffer.read(bytes);
              DataBufferUtils.release(buffer);
              return Map.entry(filePart.filename(), bytes);
            })
        )
        .collectList()
        .flatMap(fileBytes -> {
          long totalSize = fileBytes.stream().mapToLong(e -> e.getValue().length).sum();
          if (totalSize > MAX_UPLOAD_SIZE_BYTES) {
            return Mono.error(new IllegalArgumentException(
                "O tamanho total dos arquivos (" + (totalSize / (1024 * 1024)) + "MB) excede o limite de 12MB"));
          }
          return Flux.fromIterable(fileBytes)
              .flatMapSequential(entry ->
                  parserService.parseTika(entry.getValue(), entry.getKey())
                      .flatMap(docs -> Mono.fromCallable(() -> {
                        VectorStore store = vectorStoreRegistry.getStore(storeId);
                        store.add(docs);
                        documentRegistry.register(storeId, docs);
                        return new FileUploadResult(entry.getKey(), true, docs.size(), null);
                      }).subscribeOn(Schedulers.boundedElastic()))
                      .onErrorResume(e -> Mono.just(
                          new FileUploadResult(entry.getKey(), false, 0, e.getMessage())))
              )
              .collectList();
        });
  }

  public Mono<List<DocumentEntry>> uploadFile(String storeId, FilePart filePart) {
    return parserService.parse(filePart)
        .flatMap(docs -> Mono.fromCallable(() -> {
          VectorStore store = vectorStoreRegistry.getStore(storeId);
          store.add(docs);
          documentRegistry.register(storeId, docs);
          return documentRegistry.getDocuments(storeId);
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  public Mono<List<DocumentEntry>> importFromGitHub(
      String storeId, String repoUrl, String branch, List<String> folders, String importedBy) {
    return gitHubImportService.importFromGitHub(repoUrl, branch, folders)
        .flatMap(docs -> Mono.fromCallable(() -> {
          VectorStore store = vectorStoreRegistry.getStore(storeId);
          if (!docs.isEmpty()) {
            store.add(docs);
            documentRegistry.register(storeId, docs);
          }
          importRecordRepository.save(new DocumentImportRecord(
              UUID.randomUUID().toString(),
              extractRepoName(repoUrl),
              Instant.now(),
              "github",
              importedBy,
              storeId,
              Map.of(
                  "repoUrl", repoUrl,
                  "branch", branch,
                  "folders", String.join(",", folders)
              )
          ));
          return documentRegistry.getDocuments(storeId);
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  public Mono<ConfluenceImportResult> importFromConfluence(String storeId, String spaceKey, String importedBy) {
    return confluenceImportService.importFromSpace(spaceKey)
        .flatMap(result -> storeConfluenceResult(storeId, result, new DocumentImportRecord(
            UUID.randomUUID().toString(),
            "Space: " + spaceKey,
            Instant.now(),
            "confluence",
            importedBy,
            storeId,
            Map.of("spaceKey", spaceKey)
        )));
  }

  public Mono<ConfluenceImportResult> importFromConfluencePages(
      String storeId, String spaceKey, List<String> pageIds, String importedBy) {
    return confluenceImportService.importSpecificPages(spaceKey, pageIds)
        .flatMap(result -> storeConfluenceResult(storeId, result, new DocumentImportRecord(
            UUID.randomUUID().toString(),
            "Space: " + spaceKey + " (" + pageIds.size() + " page(s))",
            Instant.now(),
            "confluence",
            importedBy,
            storeId,
            Map.of("spaceKey", spaceKey, "pageIds", String.join(",", pageIds))
        )));
  }

  private Mono<ConfluenceImportResult> storeConfluenceResult(
      String storeId, ConfluenceImportResult result, DocumentImportRecord record) {
    return Mono.fromCallable(() -> {
      if (!result.documents().isEmpty()) {
        VectorStore store = vectorStoreRegistry.getStore(storeId);
        store.add(result.documents());
        documentRegistry.register(storeId, result.documents());
      }
      importRecordRepository.save(record);
      return new ConfluenceImportResult(
          result.pagesProcessed(),
          result.pagesIngested(),
          result.chunksIngested(),
          result.errors(),
          List.of());
    }).subscribeOn(Schedulers.boundedElastic());
  }

  public Mono<MondayImportResult> importFromMonday(String storeId, String boardId, String importedBy) {
    return mondayImportService.importFromBoard(boardId)
        .flatMap(result -> Mono.fromCallable(() -> {
          if (!result.documents().isEmpty()) {
            VectorStore store = vectorStoreRegistry.getStore(storeId);
            store.add(result.documents());
            documentRegistry.register(storeId, result.documents());
          }
          importRecordRepository.save(new DocumentImportRecord(
              UUID.randomUUID().toString(),
              "Monday Board: " + boardId,
              Instant.now(),
              "monday",
              importedBy,
              storeId,
              Map.of("boardId", boardId)
          ));
          return new MondayImportResult(
              result.itemsProcessed(),
              result.itemsIngested(),
              result.chunksIngested(),
              result.errors(),
              List.of());
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  public Mono<S3ImportResult> importFromS3(
      String storeId, String bucketName, String prefix, List<String> extensions, String importedBy) {
    return s3ImportService.importFromBucket(bucketName, prefix, extensions)
        .flatMap(result -> Mono.fromCallable(() -> {
          if (!result.documents().isEmpty()) {
            VectorStore store = vectorStoreRegistry.getStore(storeId);
            store.add(result.documents());
            documentRegistry.register(storeId, result.documents());
          }
          importRecordRepository.save(new DocumentImportRecord(
              UUID.randomUUID().toString(),
              "s3://" + bucketName + (prefix.isBlank() ? "" : "/" + prefix),
              Instant.now(),
              "s3",
              importedBy,
              storeId,
              Map.of(
                  "bucket", bucketName,
                  "prefix", prefix,
                  "extensions", String.join(",", extensions)
              )
          ));
          return new S3ImportResult(
              result.filesProcessed(),
              result.filesIngested(),
              result.chunksIngested(),
              result.errors(),
              List.of());
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  public Mono<List<DocumentEntry>> deleteDocuments(String storeId, List<String> ids) {
    return Mono.fromCallable(() -> {
      VectorStore store = vectorStoreRegistry.getStore(storeId);
      store.delete(ids);
      documentRegistry.remove(storeId, ids);
      return documentRegistry.getDocuments(storeId);
    }).subscribeOn(Schedulers.boundedElastic());
  }

  public Mono<List<Document>> semanticSearch(
      String storeId, String query, int topK, double similarityThreshold, String filterExpression) {
    return Mono.fromCallable(() -> {
      VectorStore store = vectorStoreRegistry.getStore(storeId);
      SearchRequest.Builder builder = SearchRequest.builder()
          .query(query)
          .topK(topK)
          .similarityThreshold(similarityThreshold);

      if (filterExpression != null && !filterExpression.isBlank()) {
        builder.filterExpression(filterExpression);
      }
      return store.similaritySearch(builder.build());
    }).subscribeOn(Schedulers.boundedElastic());
  }

  private String extractRepoName(String repoUrl) {
    String normalized = repoUrl.trim().replaceAll("/$", "").replace(".git", "");
    int lastSlash = normalized.lastIndexOf('/');
    int lastColon = normalized.lastIndexOf(':');
    int start = Math.max(lastSlash, lastColon);
    return start >= 0 ? normalized.substring(start + 1) : normalized;
  }
}
