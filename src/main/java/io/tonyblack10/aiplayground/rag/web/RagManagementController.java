package io.tonyblack10.aiplayground.rag.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tonyblack10.aiplayground.config.security.RagAuthorityHelper;
import io.tonyblack10.aiplayground.config.security.RequiresRagAccess;
import io.tonyblack10.aiplayground.rag.model.ConfluenceImportResult;
import io.tonyblack10.aiplayground.rag.model.RestoreStreamEvent;
import io.tonyblack10.aiplayground.rag.model.UrlLinksImportResult;
import io.tonyblack10.aiplayground.rag.service.DocumentManagementService;
import io.tonyblack10.aiplayground.rag.service.RagRestoreService;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@RequestMapping("/rag")
public class RagManagementController {

  private static final Logger log = LoggerFactory.getLogger(RagManagementController.class);
  private final DocumentManagementService managementService;
  private final VectorStoreRegistry storeRegistry;
  private final RagAuthorityHelper ragAuthorityHelper;
  private final RagRestoreService ragRestoreService;
  private final ObjectMapper objectMapper;

  public RagManagementController(DocumentManagementService managementService,
      VectorStoreRegistry storeRegistry,
      RagAuthorityHelper ragAuthorityHelper,
      RagRestoreService ragRestoreService,
      ObjectMapper objectMapper) {
    this.managementService = managementService;
    this.storeRegistry = storeRegistry;
    this.ragAuthorityHelper = ragAuthorityHelper;
    this.ragRestoreService = ragRestoreService;
    this.objectMapper = objectMapper;
  }

  @PreAuthorize("@ragAuthorityHelper.hasAnyRagAccess(authentication)")
  @GetMapping
  public Mono<String> index(Model model, Authentication authentication) {
    return Mono.fromCallable(() -> {
      model.addAttribute("storeInfos",
          ragAuthorityHelper.accessibleStores(authentication, storeRegistry.getAllStoreInfos()));
      return "rag/index";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @RequiresRagAccess
  @GetMapping("/{storeId}/view")
  public Mono<String> storeView(@PathVariable String storeId, Model model) {
    return managementService.listDocuments(storeId)
        .doOnNext(docs -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("documents", docs);
        })
        .thenReturn("rag/fragments/store-view :: storeView");
  }

  @RequiresRagAccess
  @GetMapping("/{storeId}/documents")
  public Mono<String> listDocuments(@PathVariable String storeId, Model model) {
    return managementService.listDocuments(storeId)
        .doOnNext(docs -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("documents", docs);
        })
        .thenReturn("rag/fragments/document-list :: documentList");
  }

  @RequiresRagAccess
  @GetMapping("/{storeId}/add")
  public Mono<String> addForm(@PathVariable String storeId, Model model) {
    return Mono.fromCallable(() -> {
      model.addAttribute("storeId", storeId);
      return "rag/fragments/add-forms :: addForms";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @RequiresRagAccess
  @GetMapping("/{storeId}/search")
  public Mono<String> searchPanel(@PathVariable String storeId, Model model) {
    return Mono.fromCallable(() -> {
      model.addAttribute("storeId", storeId);
      return "rag/fragments/search-panel :: searchPanel";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @RequiresRagAccess
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

  @RequiresRagAccess
  @PostMapping("/{storeId}/documents/github")
  public Mono<String> importFromGitHub(
      @PathVariable String storeId,
      @Valid @ModelAttribute GitHubImportForm form,
      BindingResult bindingResult,
      Authentication authentication,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("errorMessage", formatValidationErrors(bindingResult));
      return Mono.just("rag/fragments/github-import-result :: githubImportFeedback");
    }
    List<String> folderList = (form.folders() != null && !form.folders().isBlank())
        ? Arrays.stream(form.folders().split("[\\r\\n,]+"))
              .map(String::strip)
              .filter(s -> !s.isEmpty())
              .toList()
        : List.of();
    return managementService.importFromGitHub(storeId, form.repoUrl(), form.branch(), folderList, authentication.getName())
        .doOnNext(docs -> model.addAttribute("successMessage",
            "Importação concluída. " + docs.size() + " documento(s) adicionado(s) à store."))
        .thenReturn("rag/fragments/github-import-result :: githubImportFeedback")
        .onErrorResume(e -> {
          log.error("GitHub import failed for repo {} (branch: {})", form.repoUrl(), form.branch(), e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/github-import-result :: githubImportFeedback");
        });
  }

  @RequiresRagAccess
  @PostMapping("/{storeId}/documents/confluence")
  public Mono<String> importFromConfluence(
      @PathVariable String storeId,
      @Valid @ModelAttribute ConfluenceImportForm form,
      BindingResult bindingResult,
      Authentication authentication,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("errorMessage", formatValidationErrors(bindingResult));
      return Mono.just("rag/fragments/confluence-import-result :: confluenceImportFeedback");
    }
    String key = form.spaceKey().strip().toUpperCase();
    List<String> parsedPageIds = (form.pageIds() != null && !form.pageIds().isBlank())
        ? Arrays.stream(form.pageIds().split("[\\r\\n,]+"))
              .map(String::strip)
              .filter(s -> !s.isEmpty())
              .toList()
        : List.of();
    Mono<ConfluenceImportResult> importMono = !parsedPageIds.isEmpty()
        ? managementService.importFromConfluencePages(storeId, key, parsedPageIds, authentication.getName())
        : managementService.importFromConfluence(storeId, key, authentication.getName());
    return importMono
        .doOnNext(result -> model.addAttribute("confluenceResult", result))
        .thenReturn("rag/fragments/confluence-import-result :: confluenceImportFeedback")
        .onErrorResume(e -> {
          log.error("Confluence import failed for space {}", key, e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/confluence-import-result :: confluenceImportFeedback");
        });
  }

  @RequiresRagAccess
  @PostMapping("/{storeId}/documents/monday")
  public Mono<String> importFromMonday(
      @PathVariable String storeId,
      @Valid @ModelAttribute MondayImportForm form,
      BindingResult bindingResult,
      Authentication authentication,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("errorMessage", formatValidationErrors(bindingResult));
      return Mono.just("rag/fragments/monday-import-result :: mondayImportFeedback");
    }
    List<String> itemIds = (form.itemIds() != null && !form.itemIds().isBlank())
        ? Arrays.stream(form.itemIds().split("[\\r\\n,]+"))
              .map(String::strip)
              .filter(s -> s.matches("\\d+"))
              .toList()
        : List.of();
    return managementService.importFromMonday(storeId, form.boardId().strip(), itemIds, authentication.getName())
        .doOnNext(result -> model.addAttribute("mondayResult", result))
        .thenReturn("rag/fragments/monday-import-result :: mondayImportFeedback")
        .onErrorResume(e -> {
          log.error("Monday import failed for board {}", form.boardId(), e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/monday-import-result :: mondayImportFeedback");
        });
  }

  @RequiresRagAccess
  @PostMapping("/{storeId}/documents/url-links")
  public Mono<String> importFromUrlLinks(
      @PathVariable String storeId,
      @Valid @ModelAttribute UrlLinksImportForm form,
      BindingResult bindingResult,
      Authentication authentication,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("errorMessage", formatValidationErrors(bindingResult));
      return Mono.just("rag/fragments/url-links-import-result :: urlLinksImportFeedback");
    }
    List<String> urls = Arrays.stream(form.links().split("[\\r\\n]+"))
        .map(String::strip)
        .filter(s -> !s.isEmpty() && s.startsWith("http"))
        .toList();
    if (urls.isEmpty()) {
      model.addAttribute("errorMessage", "Nenhum link válido encontrado. Os links devem começar com http:// ou https://");
      return Mono.just("rag/fragments/url-links-import-result :: urlLinksImportFeedback");
    }
    return managementService.importFromUrls(storeId, urls, authentication.getName())
        .doOnNext(result -> model.addAttribute("urlLinksResult", result))
        .thenReturn("rag/fragments/url-links-import-result :: urlLinksImportFeedback")
        .onErrorResume(e -> {
          log.error("URL links import failed for store {}", storeId, e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/url-links-import-result :: urlLinksImportFeedback");
        });
  }

  @RequiresRagAccess
  @PostMapping("/{storeId}/documents/s3")
  public Mono<String> importFromS3(
      @PathVariable String storeId,
      @Valid @ModelAttribute S3ImportForm form,
      BindingResult bindingResult,
      Authentication authentication,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("errorMessage", formatValidationErrors(bindingResult));
      return Mono.just("rag/fragments/s3-import-result :: s3ImportFeedback");
    }
    String prefix = form.prefix() != null ? form.prefix().strip() : "";
    return managementService.importFromS3(storeId, form.bucketName().strip(), prefix, form.fileFormats(), authentication.getName())
        .doOnNext(result -> model.addAttribute("s3Result", result))
        .thenReturn("rag/fragments/s3-import-result :: s3ImportFeedback")
        .onErrorResume(e -> {
          log.error("S3 import failed for bucket {}", form.bucketName(), e);
          model.addAttribute("errorMessage", "Importação falhou: " + e.getMessage());
          return Mono.just("rag/fragments/s3-import-result :: s3ImportFeedback");
        });
  }

  @RequiresRagAccess
  @PostMapping("/{storeId}/documents/delete")
  public Mono<String> deleteDocuments(
      @PathVariable String storeId,
      @Valid @ModelAttribute DeleteDocumentsForm form,
      BindingResult bindingResult,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("storeId", storeId);
      model.addAttribute("errorMessage", formatValidationErrors(bindingResult));
      return Mono.just("rag/fragments/feedback :: feedback");
    }
    return managementService.deleteDocuments(storeId, form.ids())
        .doOnNext(docs -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("documents", docs);
          model.addAttribute("successMessage", form.ids().size() + " document(s) deleted.");
        })
        .thenReturn("rag/fragments/document-list :: documentList")
        .onErrorResume(e -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("errorMessage", "Delete failed: " + e.getMessage());
          return Mono.just("rag/fragments/feedback :: feedback");
        });
  }

  @RequiresRagAccess
  @GetMapping("/{storeId}/search/results")
  public Mono<String> searchResults(
      @PathVariable String storeId,
      @Valid @ModelAttribute DocumentSearchForm form,
      BindingResult bindingResult,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("errorMessage", formatValidationErrors(bindingResult));
      return Mono.just("rag/fragments/search-results :: searchResults");
    }
    int topK = form.topK() != null ? form.topK() : 5;
    double similarityThreshold = form.similarityThreshold() != null ? form.similarityThreshold() : 0.0;
    String filterExpression = form.filterExpression() != null ? form.filterExpression() : "";
    return managementService.semanticSearch(storeId, form.query(), topK, similarityThreshold, filterExpression)
        .doOnNext(results -> {
          model.addAttribute("storeId", storeId);
          model.addAttribute("searchResults", results);
          model.addAttribute("query", form.query());
        })
        .thenReturn("rag/fragments/search-results :: searchResults")
        .onErrorResume(e -> {
          model.addAttribute("errorMessage", "Search failed: " + e.getMessage());
          return Mono.just("rag/fragments/feedback :: feedback");
        });
  }

  @RequiresRagAccess
  @GetMapping("/{storeId}/restore")
  public Mono<String> restorePanel(@PathVariable String storeId, Model model) {
    return Mono.fromCallable(() -> {
      model.addAttribute("storeId", storeId);
      model.addAttribute("restoreRunning", ragRestoreService.isRunning(storeId));
      return "rag/fragments/restore-panel :: restorePanel";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @RequiresRagAccess
  @GetMapping(value = "/{storeId}/restore/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @ResponseBody
  public Flux<ServerSentEvent<String>> restoreStream(
      @PathVariable String storeId, Authentication authentication) {
    return ragRestoreService.restoreByStore(storeId)
        .map(event -> {
          try {
            return ServerSentEvent.<String>builder()
                .event(event.type())
                .data(objectMapper.writeValueAsString(event))
                .build();
          } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"type\":\"error\",\"error\":\"Serialization failed\"}")
                .build();
          }
        })
        .onErrorResume(e -> {
          log.error("Restore stream error for store {} (user: {}): {}", storeId, authentication.getName(), e.getMessage());
          String errJson = "{\"type\":\"error\",\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
          return Flux.just(ServerSentEvent.<String>builder().event("error").data(errJson).build());
        });
  }

  @RequiresRagAccess
  @DeleteMapping("/{storeId}/restore")
  @ResponseBody
  public Mono<Void> cancelRestore(@PathVariable String storeId) {
    ragRestoreService.cancelRestore(storeId);
    return Mono.empty();
  }

  private String formatValidationErrors(BindingResult bindingResult) {
    return bindingResult.getFieldErrors().stream()
        .map(fe -> fe.getDefaultMessage())
        .collect(Collectors.joining("; "));
  }
}
