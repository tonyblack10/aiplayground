package io.tonyblack10.aiplayground.chat.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tonyblack10.aiplayground.chat.service.ChatService;
import io.tonyblack10.aiplayground.config.security.RagAuthorityHelper;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import org.springframework.security.core.Authentication;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@RequestMapping("/chat")
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);

  record ModelOption(String id, String displayName) {}

  private static final List<ModelOption> AVAILABLE_MODELS = List.of(
      new ModelOption("gpt-4o-mini", "GPT-4o Mini (Rápido)"),
      new ModelOption("gpt-4o", "GPT-4o (Avançado)"),
      new ModelOption("gpt-4-turbo", "GPT-4 Turbo (Poderoso)")
  );

  private final ChatService chatService;
  private final VectorStoreRegistry vectorStoreRegistry;
  private final RagAuthorityHelper ragAuthorityHelper;
  private final ObjectMapper objectMapper;

  public ChatController(ChatService chatService, VectorStoreRegistry vectorStoreRegistry,
      RagAuthorityHelper ragAuthorityHelper, ObjectMapper objectMapper) {
    this.chatService = chatService;
    this.vectorStoreRegistry = vectorStoreRegistry;
    this.ragAuthorityHelper = ragAuthorityHelper;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public Mono<String> index(Model model, Authentication authentication) {
    return Mono.fromCallable(() -> {
      model.addAttribute("models", AVAILABLE_MODELS);
      model.addAttribute("storeInfos",
          ragAuthorityHelper.accessibleStores(authentication, vectorStoreRegistry.getAllStoreInfos()));
      return "chat/index";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @ResponseBody
  public Flux<ServerSentEvent<String>> stream(
      @RequestParam String message,
      @RequestParam(defaultValue = "gpt-4o-mini") String model,
      @RequestParam(defaultValue = "0.7") double temperature,
      @RequestParam(defaultValue = "false") boolean useRag,
      @RequestParam(defaultValue = "") String storeId,
      Authentication authentication,
      WebSession session) {

    String conversationId = session.getId();
    log.debug("Streaming chat for session {} with model {} (useRag={}, store={})",
        conversationId, model, useRag, storeId);

    return chatService.stream(message, conversationId, model, temperature, useRag, storeId, authentication)
        .map(token -> {
          try {
            // JSON-encode the token so the SSE spec does not strip leading spaces
            // (e.g. " porque" → "\" porque\"" → client JSON.parses it back)
            return ServerSentEvent.<String>builder()
                .data(objectMapper.writeValueAsString(token))
                .build();
          } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder().data("\"\"").build();
          }
        })
        .concatWith(Flux.just(
            ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build()))
        .onErrorResume(e -> {
          log.error("Streaming error for session {}", conversationId, e);
          return Flux.just(ServerSentEvent.<String>builder()
              .event("error")
              .data("Erro ao processar a resposta: " + e.getMessage())
              .build());
        });
  }

  @PostMapping("/clear")
  @ResponseBody
  public Mono<Void> clearConversation(WebSession session) {
    return Mono.fromRunnable(() -> chatService.clearMemory(session.getId()))
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }
}
