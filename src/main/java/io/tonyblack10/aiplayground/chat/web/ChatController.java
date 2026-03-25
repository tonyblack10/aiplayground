package io.tonyblack10.aiplayground.chat.web;

import io.tonyblack10.aiplayground.chat.dto.SendMessageDTO;
import io.tonyblack10.aiplayground.chat.model.ChatSession;
import io.tonyblack10.aiplayground.chat.model.ChatSettings;
import io.tonyblack10.aiplayground.chat.service.ChatService;
import io.tonyblack10.aiplayground.chat.service.ProviderRegistry;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
  private static final String SESSION_KEY = "chatSession";

  private final ChatService chatService;
  private final ProviderRegistry providerRegistry;
  private final VectorStoreRegistry vectorStoreRegistry;

  public ChatController(ChatService chatService, ProviderRegistry providerRegistry,
      VectorStoreRegistry vectorStoreRegistry) {
    this.chatService = chatService;
    this.providerRegistry = providerRegistry;
    this.vectorStoreRegistry = vectorStoreRegistry;
  }

  @GetMapping
  public Mono<String> index(WebSession webSession, Model model) {
    return Mono.fromCallable(() -> {
      var session = getOrCreate(webSession);
      model.addAttribute("messages", session.getMessages());
      model.addAttribute("settings", session.getSettings());
      model.addAttribute("providers", providerRegistry.getAllProviders());
      model.addAttribute("storeInfos", vectorStoreRegistry.getAllStoreInfos());
      model.addAttribute("availableTools", availableTools());
      model.addAttribute("streaming", true);
      return "chat/index";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @GetMapping("/models")
  public String getModels(@RequestParam String provider, Model model) {
    model.addAttribute("models", providerRegistry.getModels(provider));
    model.addAttribute("selectedModel", providerRegistry.getModels(provider).getFirst());
    return "chat/fragments/model-options :: modelOptions";
  }

  @PostMapping("/send")
  public Mono<String> sendMessage(
      WebSession webSession,
      @ModelAttribute SendMessageDTO messageDTO,
      Model uiModel) {

    return Mono.fromCallable(() -> {
      var session = getOrCreate(webSession);
      var settings = new ChatSettings(
          messageDTO.provider(), messageDTO.model(), messageDTO.temperature(),
          messageDTO.ragStoreId() != null && !messageDTO.ragStoreId().isBlank() ? messageDTO.ragStoreId() : null,
          messageDTO.tools() != null ? messageDTO.tools() : List.of()
      );
      session.updateSettings(settings);
      session.addUserMessage(messageDTO.message().trim());

      uiModel.addAttribute("messages", session.getMessages());
      uiModel.addAttribute("settings", settings);
      uiModel.addAttribute("streaming", true);
      return "chat/fragments/message-list :: messageList";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @ResponseBody
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> stream(WebSession webSession) {
    var session = getOrCreate(webSession);

    if (session.isEmpty()) {
      return Flux.just(done());
    }

    var allMessages = session.getMessages();
    List<io.tonyblack10.aiplayground.chat.model.ChatMessage> history = allMessages.size() > 1
        ? allMessages.subList(0, allMessages.size() - 1)
        : List.of();
    var userMessage = allMessages.getLast().content();
    var settings = session.getSettings();

    var accumulated = new StringBuilder();

    return chatService.stream(history, userMessage, settings)
        .doOnNext(accumulated::append)
        .map(token -> ServerSentEvent.<String>builder().data(token).build())
        .concatWith(Flux.defer(() -> {
          session.addAssistantMessage(accumulated.toString());
          return Flux.just(done());
        }))
        .onErrorResume(e -> {
          log.error("Streaming error for session {}", webSession.getId(), e);
          return Flux.just(
              ServerSentEvent.<String>builder().data("[ERROR] " + e.getMessage()).build(),
              done()
          );
        });
  }

  @DeleteMapping("/session")
  public Mono<String> clearSession(WebSession webSession, Model model) {
    return Mono.fromCallable(() -> {
      getOrCreate(webSession).clear();
      model.addAttribute("messages", List.of());
      model.addAttribute("streaming", false);
      return "chat/fragments/message-list :: messageList";
    }).subscribeOn(Schedulers.boundedElastic());
  }

  private ChatSession getOrCreate(WebSession webSession) {
    return (ChatSession) webSession.getAttributes()
        .computeIfAbsent(SESSION_KEY, k -> new ChatSession());
  }

  private ServerSentEvent<String> done() {
    return ServerSentEvent.<String>builder().data("[DONE]").build();
  }

  private List<Map<String, String>> availableTools() {
    return List.of(
        Map.of("id", "calculator", "name", "Calculator",
            "description", "Evaluate math expressions"),
        Map.of("id", "currentDate", "name", "Current Date",
            "description", "Get the current date and time")
    );
  }
}
