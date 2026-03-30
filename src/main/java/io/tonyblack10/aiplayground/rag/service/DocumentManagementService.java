package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.model.ConfluenceImportResult;
import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import io.tonyblack10.aiplayground.rag.model.FileUploadResult;
import io.tonyblack10.aiplayground.rag.registry.DocumentRegistry;
import java.util.List;
import java.util.Map;
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

  public DocumentManagementService(
      VectorStoreRegistry vectorStoreRegistry,
      DocumentRegistry documentRegistry,
      DocumentParserService parserService,
      GitHubImporter gitHubImportService,
      ConfluenceImportService confluenceImportService) {
    this.vectorStoreRegistry = vectorStoreRegistry;
    this.documentRegistry = documentRegistry;
    this.parserService = parserService;
    this.gitHubImportService = gitHubImportService;
    this.confluenceImportService = confluenceImportService;
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

  public Mono<List<DocumentEntry>> importFromGitHub(String storeId, String repoUrl, String branch) {
    return gitHubImportService.importFromGitHub(repoUrl, branch)
        .flatMap(docs -> Mono.fromCallable(() -> {
          VectorStore store = vectorStoreRegistry.getStore(storeId);
          if (!docs.isEmpty()) {
            store.add(docs);
            documentRegistry.register(storeId, docs);
          }
          return documentRegistry.getDocuments(storeId);
        }).subscribeOn(Schedulers.boundedElastic()));
  }

  public Mono<ConfluenceImportResult> importFromConfluence(String storeId, String spaceKey) {
    return confluenceImportService.importFromSpace(spaceKey)
        .flatMap(result -> Mono.fromCallable(() -> {
          if (!result.documents().isEmpty()) {
            VectorStore store = vectorStoreRegistry.getStore(storeId);
            store.add(result.documents());
            documentRegistry.register(storeId, result.documents());
          }
          return new ConfluenceImportResult(
              result.pagesProcessed(),
              result.pagesIngested(),
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
}
