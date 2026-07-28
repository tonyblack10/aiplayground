package io.tonyblack10.aiplayground.chat.service;

import io.tonyblack10.aiplayground.chat.service.tools.UserToolContext;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

  private static final String SYSTEM_PROMPT = """
      Você é um assistente especializado em consultas de documentação técnica.
      Seja sempre didático, cordial e claro nas suas respostas.
      Quando disponível, utilize o contexto de documentação fornecido para enriquecer suas respostas.
      Responda SEMPRE em Português do Brasil, independentemente do idioma da pergunta do usuário.
      Ao explicar conceitos técnicos, use exemplos práticos sempre que possível.
      """;

  private static final int CHAT_HISTORY_WINDOW = 20;

  private final ChatClient.Builder chatClientBuilder;
  private final RedisChatMemory chatMemoryRepository;
  private final VectorStoreRegistry vectorStoreRegistry;

  public ChatService(ChatClient.Builder chatClientBuilder,
      RedisChatMemory chatMemoryRepository,
      VectorStoreRegistry vectorStoreRegistry) {
    this.chatClientBuilder = chatClientBuilder;
    this.chatMemoryRepository = chatMemoryRepository;
    this.vectorStoreRegistry = vectorStoreRegistry;
  }

  public Flux<String> stream(String message, String conversationId, String model,
      double temperature, boolean useRag, String storeId, Authentication authentication) {

    var chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(chatMemoryRepository)
        .maxMessages(CHAT_HISTORY_WINDOW)
        .build();

    List<Advisor> advisors = new ArrayList<>();
    advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());

    if (useRag && storeId != null && !storeId.isBlank()) {
      advisors.add(QuestionAnswerAdvisor.builder(vectorStoreRegistry.getStore(storeId)).build());
    }

    var userCtx = UserToolContext.from(authentication);
    Map<String, Object> toolContextMap = userCtx != null
        ? Map.of(UserToolContext.TOOL_CONTEXT_KEY, userCtx)
        : Map.of();

    return chatClientBuilder.build()
        .prompt()
        .system(SYSTEM_PROMPT)
        .user(message)
        .advisors(advisors)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .options(OpenAiChatOptions.builder()
            .model(model)
            .temperature(temperature)
            .build())
        .toolContext(toolContextMap)
        .stream()
        .content();
  }

  public void clearMemory(String conversationId) {
    chatMemoryRepository.deleteByConversationId(conversationId);
  }
}
