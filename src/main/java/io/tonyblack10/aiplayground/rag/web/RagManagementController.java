package io.tonyblack10.aiplayground.rag.web;

import io.tonyblack10.aiplayground.rag.service.DocumentManagementService;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@RequestMapping("/rag")
public class RagManagementController {

  private static final Logger log = LoggerFactory.getLogger(RagManagementController.class);
  private final DocumentManagementService managementService;
  private final VectorStoreRegistry storeRegistry;

  public RagManagementController(DocumentManagementService managementService,
      VectorStoreRegistry storeRegistry) {
    this.managementService = managementService;
    this.storeRegistry = storeRegistry;
  }

  @GetMapping
  public Mono<String> index(Model model) {
    return Mono.fromCallable(() -> {
      model.addAttribute("storeInfos", storeRegistry.getAllStoreInfos());
      return "rag/index";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @GetMapping("/{storeId}/view")
  public Mono<String> storeView(@PathVariable String storeId, Model model) {
    return managementService.listDocuments(storeId)
        .doOnNext(docs -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("documents", docs);
        })
        .thenReturn("rag/fragments/store-view :: storeView");
  }

  @GetMapping("/{storeId}/documents")
  public Mono<String> listDocuments(@PathVariable String storeId, Model model) {
    return managementService.listDocuments(storeId)
        .doOnNext(docs -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("documents", docs);
        })
        .thenReturn("rag/fragments/document-list :: documentList");
  }

  @GetMapping("/{storeId}/add")
  public String addForm(@PathVariable String storeId, Model model) {
    model.addAttribute("storeId", storeId);
    return "rag/fragments/add-forms :: addForms";
  }

  @GetMapping("/{storeId}/search")
  public String searchPanel(@PathVariable String storeId, Model model) {
    model.addAttribute("storeId", storeId);
    return "rag/fragments/search-panel :: searchPanel";
  }

  @PostMapping(value = "/{storeId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Mono<String> uploadFiles(
      @PathVariable String storeId,
      @RequestPart("files") Flux<FilePart> files,
      Model model) {
    return files.collectList()
        .flatMap(fileList -> managementService.uploadFiles(storeId, fileList))
        .doOnNext(results -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("uploadResults", results);
        })
        .thenReturn("rag/fragments/upload-results :: uploadResults")
        .onErrorResume(e -> {
          log.error("Upload failed for store {}", storeId, e);
          model.addAttribute("storeId", storeId);
          model.addAttribute("errorMessage", "Upload falhou: " + e.getMessage());
          return Mono.just("rag/fragments/feedback :: feedback");
        });
  }

  @PostMapping("/{storeId}/documents/github")
  public Mono<String> importFromGitHub(
      @PathVariable String storeId,
      @RequestParam(defaultValue = "https://github.com/tonyblack10/prompts-diversos.git") String repoUrl,
      @RequestParam(defaultValue = "main") String branch,
      Model model) {
    return managementService.importFromGitHub(storeId, repoUrl, branch)
        .doOnNext(docs -> model.addAttribute("successMessage",
            "Importação concluída. " + docs.size() + " documento(s) adicionado(s) à store."))
        .thenReturn("rag/fragments/github-import-result :: githubImportFeedback")
        .onErrorResume(e -> {
          log.error("GitHub import failed for repo {} (branch: {})", repoUrl, branch, e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/github-import-result :: githubImportFeedback");
        });
  }

  @PostMapping("/{storeId}/documents/confluence")
  public Mono<String> importFromConfluence(
      @PathVariable String storeId,
      @RequestParam(defaultValue = "~5570589115414629b149a9a6cc9c310018c84e") String spaceKey,
      Model model) {
    return managementService.importFromConfluence(storeId, spaceKey.strip().toUpperCase())
        .doOnNext(result -> model.addAttribute("confluenceResult", result))
        .thenReturn("rag/fragments/confluence-import-result :: confluenceImportFeedback")
        .onErrorResume(e -> {
          log.error("Confluence import failed for space {}", spaceKey, e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/confluence-import-result :: confluenceImportFeedback");
        });
  }

  @PostMapping("/{storeId}/documents/delete")
  public Mono<String> deleteDocuments(
      @PathVariable String storeId,
      @RequestParam List<String> ids,
      Model model) {
    return managementService.deleteDocuments(storeId, ids)
        .doOnNext(docs -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("documents", docs);
          model.addAttribute("successMessage", ids.size() + " document(s) deleted.");
        })
        .thenReturn("rag/fragments/document-list :: documentList")
        .onErrorResume(e -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("errorMessage", "Delete failed: " + e.getMessage());
          return Mono.just("rag/fragments/feedback :: feedback");
        });
  }

  @GetMapping("/{storeId}/search/results")
  public Mono<String> searchResults(
      @PathVariable String storeId,
      @RequestParam String query,
      @RequestParam(defaultValue = "5") int topK,
      @RequestParam(defaultValue = "0.0") double similarityThreshold,
      @RequestParam(defaultValue = "") String filterExpression,
      Model model) {
    return managementService.semanticSearch(storeId, query, topK, similarityThreshold, filterExpression)
        .doOnNext(results -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("searchResults", results);
          model.addAttribute("query", query);
        })
        .thenReturn("rag/fragments/search-results :: searchResults")
        .onErrorResume(e -> {
          model.addAttribute("errorMessage", "Search failed: " + e.getMessage());
          return Mono.just("rag/fragments/feedback :: feedback");
        });
  }
}
