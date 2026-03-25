package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import io.tonyblack10.aiplayground.rag.registry.DocumentRegistry;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class DocumentManagementService {

  private final VectorStoreRegistry vectorStoreRegistry;
  private final DocumentRegistry documentRegistry;
  private final DocumentParserService parserService;
  private final GitHubImportService gitHubImportService;

  public DocumentManagementService(
      VectorStoreRegistry vectorStoreRegistry,
      DocumentRegistry documentRegistry,
      DocumentParserService parserService,
      GitHubImportService gitHubImportService) {
    this.vectorStoreRegistry = vectorStoreRegistry;
    this.documentRegistry = documentRegistry;
    this.parserService = parserService;
    this.gitHubImportService = gitHubImportService;
  }

  public Mono<List<DocumentEntry>> listDocuments(String storeId) {
    return Mono.fromCallable(() -> documentRegistry.getDocuments(storeId))
        .subscribeOn(Schedulers.boundedElastic());
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

  public Mono<List<DocumentEntry>> deleteDocuments(String storeId, List<String> ids) {
    return Mono.fromCallable(() -> {
      VectorStore store = vectorStoreRegistry.getStore(storeId);
      store.delete(ids);
      documentRegistry.remove(storeId, ids);
      return documentRegistry.getDocuments(storeId);
    }).subscribeOn(Schedulers.boundedElastic());
  }

  public Mono<List<Document>> semanticSearch(String storeId, String query, int topK) {
    return Mono.fromCallable(() -> {
      VectorStore store = vectorStoreRegistry.getStore(storeId);
      SearchRequest request = SearchRequest.builder()
          .query(query)
          .topK(topK)
          .build();
      return store.similaritySearch(request);
    }).subscribeOn(Schedulers.boundedElastic());
  }
}
