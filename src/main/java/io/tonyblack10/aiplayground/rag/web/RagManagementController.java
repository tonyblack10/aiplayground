package io.tonyblack10.aiplayground.rag.web;

import io.tonyblack10.aiplayground.rag.model.ConfluenceImportResult;
import io.tonyblack10.aiplayground.rag.service.DocumentManagementService;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
      @ModelAttribute GitHubImportForm form,
      Model model) {
    String repoUrl = form.repoUrl() != null ? form.repoUrl() : "https://github.com/tonyblack10/prompts-diversos.git";
    String branch = form.branch() != null && !form.branch().isBlank() ? form.branch() : "main";
    List<String> folderList = (form.folders() != null && !form.folders().isBlank())
        ? Arrays.stream(form.folders().split("[\\r\\n,]+"))
              .map(String::strip)
              .filter(s -> !s.isEmpty())
              .toList()
        : List.of();
    return managementService.importFromGitHub(storeId, repoUrl, branch, folderList)
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
      @ModelAttribute ConfluenceImportForm form,
      Model model) {
    String key = (form.spaceKey() != null ? form.spaceKey() : "").strip().toUpperCase();
    List<String> parsedPageIds = (form.pageIds() != null && !form.pageIds().isBlank())
        ? Arrays.stream(form.pageIds().split("[\\r\\n,]+"))
              .map(String::strip)
              .filter(s -> !s.isEmpty())
              .toList()
        : List.of();
    Mono<ConfluenceImportResult> importMono = !parsedPageIds.isEmpty()
        ? managementService.importFromConfluencePages(storeId, key, parsedPageIds)
        : managementService.importFromConfluence(storeId, key);
    return importMono
        .doOnNext(result -> model.addAttribute("confluenceResult", result))
        .thenReturn("rag/fragments/confluence-import-result :: confluenceImportFeedback")
        .onErrorResume(e -> {
          log.error("Confluence import failed for space {}", key, e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/confluence-import-result :: confluenceImportFeedback");
        });
  }

  @PostMapping("/{storeId}/documents/monday")
  public Mono<String> importFromMonday(
      @PathVariable String storeId,
      @ModelAttribute MondayImportForm form,
      Model model) {
    String boardId = form.boardId() != null ? form.boardId().strip() : "18390996096";
    return managementService.importFromMonday(storeId, boardId)
        .doOnNext(result -> model.addAttribute("mondayResult", result))
        .thenReturn("rag/fragments/monday-import-result :: mondayImportFeedback")
        .onErrorResume(e -> {
          log.error("Monday import failed for board {}", boardId, e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/monday-import-result :: mondayImportFeedback");
        });
  }

  @PostMapping("/{storeId}/documents/s3")
  public Mono<String> importFromS3(
      @PathVariable String storeId,
      @ModelAttribute S3ImportForm form,
      Model model) {
    String bucketName = form.bucketName() != null ? form.bucketName().strip() : "";
    String prefix = form.prefix() != null ? form.prefix().strip() : "";
    List<String> fileFormats = form.fileFormats() != null ? form.fileFormats() : List.of();
    return managementService.importFromS3(storeId, bucketName, prefix, fileFormats)
        .doOnNext(result -> model.addAttribute("s3Result", result))
        .thenReturn("rag/fragments/s3-import-result :: s3ImportFeedback")
        .onErrorResume(e -> {
          log.error("S3 import failed for bucket {}", bucketName, e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/s3-import-result :: s3ImportFeedback");
        });
  }

  @PostMapping("/{storeId}/documents/delete")
  public Mono<String> deleteDocuments(
      @PathVariable String storeId,
      @ModelAttribute DeleteDocumentsForm form,
      Model model) {
    List<String> ids = form.ids() != null ? form.ids() : List.of();
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
      @ModelAttribute DocumentSearchForm form,
      Model model) {
    String query = form.query() != null ? form.query() : "";
    int topK = form.topK() != null ? form.topK() : 5;
    double similarityThreshold = form.similarityThreshold() != null ? form.similarityThreshold() : 0.0;
    String filterExpression = form.filterExpression() != null ? form.filterExpression() : "";
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
